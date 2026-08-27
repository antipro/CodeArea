# InDiskContent

`InDiskContent` is the disk-backed content model used by `CodeArea` when the
large-content constructor is selected. It reduces retained heap usage by storing
text in a temporary file instead of keeping every paragraph in a
`StringBuilder`.

## Usage

```java
import com.bitifyware.control.CodeArea;

String text = loadText();
CodeArea codeArea = new CodeArea(text, true);
```

The second constructor argument selects the content implementation:

```java
new CodeArea(text, false); // InMemoryContent
new CodeArea(text, true);  // InDiskContent
```

All normal editing APIs continue to operate through the selected content:

```java
codeArea.insertText(10, "inserted text");
codeArea.deleteText(20, 30);
codeArea.replaceText(0, 5, "start");
```

`InDiskContent` can also be used directly for content-model work:

```java
try (InDiskContent content = new InDiskContent("first\nsecond")) {
    content.insert(5, "\nnew", false);
    System.out.println(content.getParagraphList());
}
```

Do not use reflection to replace a `CodeArea` content instance. A control
registers listeners against its content during construction, so replacing that
instance after construction can leave the control and skin inconsistent.

## Storage Model

The temporary backing file stores Java UTF-16 code units in big-endian order.
This is an internal binary format, not a user-facing text-file format. Fixed
width storage provides these properties:

- A Java character index maps directly to `index * 2` in the backing file.
- Java `String` indexing semantics are preserved, including surrogate pairs.
- Reads can seek directly to a requested character range.
- Inserts and deletes can move the affected suffix in bounded chunks.

The implementation keeps the following data in memory:

- One integer paragraph-start offset per paragraph.
- Up to 50 recently read paragraphs in an LRU cache.
- Bounded 64 KB buffers while moving file data.
- Any `String` explicitly returned by `get()` or `get(start, end)`.

Paragraph offsets are searched with binary search. Paragraph change events are
split into update, add, and remove notifications expected by `CodeAreaSkin`.
Large removals use a count-backed notification list rather than materializing
all deleted paragraphs.

## Viewport Rendering

For a disk-backed `CodeArea` with wrapping disabled, `CodeAreaSkin` renders only
the paragraphs intersecting the viewport plus eight overscan lines on each
side. Scrolling replaces that small paragraph window instead of retaining one
JavaFX `TextFlow` per document paragraph.

Selection remains a logical pair of absolute character offsets. `Ctrl+A` sets
the range to `[0, content.length()]` without reading the selected text. During
layout, the skin intersects that range with each visible paragraph, so scrolling
repaints the selection for the new viewport. Calling `getSelectedText()`, Copy,
or Cut still materializes the selected range because those APIs require its
characters.

Wrapped disk-backed content currently uses the non-virtual rendering path. An
unseen wrapped paragraph has no exact height until JavaFX measures it, so safe
wrapped virtualization requires a lazy variable-height index rather than the
fixed-height mapping used for normal code-editor lines.

## Operation Costs

| Operation | Time | Additional heap |
| --- | --- | --- |
| Find paragraph | `O(log p)` | `O(1)` |
| Read paragraph | `O(line length)` | Returned line plus cache entry |
| Read range | `O(range length)` | Returned string |
| Insert | `O(file suffix + inserted text)` | Bounded I/O buffer plus offset index |
| Delete | `O(file suffix)` | Bounded I/O buffer plus offset index |

Here, `p` is the paragraph count. Disk backing lowers retained text memory; it
does not make edits near the start of a large document constant time.

## Input Handling

The content model follows `CodeArea` input filtering:

- Newline (`\n`) and tab (`\t`) are retained.
- Other control characters below `U+0020` are removed.
- `U+007F` is removed.
- Carriage return (`\r`) is removed rather than preserved as CRLF.

Paragraphs are therefore separated by `\n`. Empty and trailing paragraphs are
preserved.

## Lifecycle

`InDiskContent` implements `AutoCloseable`. Calling `close()` closes its file
channel and deletes the temporary file. Closing is idempotent, and later reads
or edits throw `IllegalStateException`.

Temporary files are also registered with `deleteOnExit()` as a fallback. A
`CodeArea` created with `large=true` owns its content internally and currently
does not expose a public disposal method, so its temporary file normally remains
until JVM exit.

`InDiskContent`, like JavaFX controls generally, is not thread-safe. Once it is
owned by a `CodeArea`, read and edit it on the JavaFX Application Thread.

## Limitations

- `CodeArea(String, true)` still receives the initial text as a `String`, so the
  complete input is temporarily in memory while loading.
- `getText()` and `InDiskContent.get()` necessarily create a complete in-memory
  `String`; prefer range and paragraph access when possible.
- Character positions and paragraph offsets are Java `int` values, so this is
  not an arbitrary-size or multi-gigabyte text abstraction.
- Each size-changing edit moves the remaining file suffix. Repeated edits near
  the beginning of a large document can be expensive.
- Viewport-only rendering currently requires `wrapText=false` and no visual-only
  empty-line decorations.
- Paragraph offsets require approximately four bytes per paragraph before array
  overhead.
- The internal temporary file is not suitable for interchange or direct editing.

## Example

Run `com.bitifyware.example.DiskContentExample` from the test sources. The
example creates its `CodeArea` with `large=true` and provides buttons that load
small and 10,000-line samples through the normal `CodeArea` API. ScenicView is
disabled by default because inspecting thousands of paragraph nodes is
expensive. Enable it explicitly with `-Dcodearea.scenicView=true`.

## Tests

Run all tests:

```bash
mvn test
```

Run the content-model tests only:

```bash
mvn "-Dtest=InDiskContentTest" test
```

Run the TestFX integration test on a machine with a display:

```bash
mvn "-Dtest=InDiskCodeAreaTest" "-Djava.awt.headless=false" test
```

The tests cover randomized edits against `StringBuilder`, paragraph boundaries,
Unicode surrogate pairs, operations larger than the I/O buffer, cleanup,
editing through a live disk-backed `CodeArea`, and the example's complete
10,000-line load path.
