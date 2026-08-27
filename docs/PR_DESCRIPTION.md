# Improve Disk-Backed Content for Large Documents

## Summary

This change replaces the original `InDiskContent` prototype with a bounded-I/O
disk-backed implementation and adds unit and TestFX integration coverage.

The original prototype called `Files.readAllLines()` for line edits and rewrote
the complete file once for each inserted or deleted line. As a result, a large
edit could allocate the entire document on heap, perform many complete file
rewrites, and expose stale cached paragraphs between rewrite steps. Its
documentation also referred to nonexistent `DiskContent` and `ContentSwapper`
classes.

## Implementation

### Fixed-width backing file

`InDiskContent` stores UTF-16 code units in a temporary binary file. A character
index maps directly to a byte position, avoiding UTF-8 byte-offset scans and
preserving Java `String` indexing behavior.

### Bounded file edits

Insertions shift the file suffix backward and deletions shift it forward using a
64 KB buffer. No operation calls `Files.readAllLines()` or constructs a list of
all paragraphs.

This keeps additional I/O memory bounded, although the amount of disk data moved
is still linear in the suffix after the edit.

### Paragraph index and cache

The implementation maintains an `int[]` of paragraph start positions. Paragraph
lookup uses binary search rather than scanning from the beginning of the file.
A 50-entry LRU cache retains recently rendered paragraphs.

Insertions and deletions update the paragraph index incrementally. Large delete
notifications use `Collections.nCopies()` so clearing or replacing a document
does not recreate all removed paragraph strings in memory.

### JavaFX notifications

Paragraph notifications follow the sequence expected by `CodeAreaSkin`:

1. Update the paragraph intersecting the edit.
2. Add newly created paragraph nodes after a multiline insert.
3. Remove obsolete paragraph nodes after a multiline delete.

This avoids indexing paragraph nodes before they have been added to the skin.

### Resource management

`InDiskContent` remains `AutoCloseable`. `close()` is idempotent, closes the file
channel, clears the cache, and deletes the temporary file. Operations after
close fail with `IllegalStateException`. `deleteOnExit()` is registered as a
fallback.

## Integration

`CodeArea` already selects the implementation through its existing constructor:

```java
CodeArea normal = new CodeArea(text, false);
CodeArea diskBacked = new CodeArea(text, true);
```

No reflection-based content swap is used or supported. The
`DiskContentExample` test application now describes its actual disk-backed
constructor usage.

## Tests

### `InDiskContentTest`

- Verifies ranges, empty paragraphs, and trailing newlines.
- Verifies edits at paragraph boundaries and supplementary Unicode characters.
- Compares 500 deterministic random edits with `StringBuilder`.
- Exercises insert and delete operations larger than the 64 KB I/O buffer.
- Verifies idempotent close and use-after-close errors.

### `InDiskCodeAreaTest`

- Creates a live `CodeArea` with `large=true`.
- Loads and renders 20,000 lines.
- Performs insert and delete operations through the public control API.
- Verifies resulting text and paragraphs.

### `DiskContentExampleTest`

- Calls the example's `loadLargeDiskContent()` method on the JavaFX thread.
- Verifies that all 10,001 paragraphs load without stalling the test.
- Verifies the beginning and end of the generated content.

For non-wrapped disk-backed content, `CodeAreaSkin` now retains only the visible
paragraphs plus an eight-line overscan window. Paragraph starts from the content
model provide global caret, hit-test, and selection offsets without rendering
preceding lines. The example no longer starts ScenicView automatically. It
remains available with `-Dcodearea.scenicView=true`.

`Ctrl+A` now changes only the logical anchor and caret offsets. The
`selectedText` property is lazy, and visible selection shapes are recomputed by
intersecting the logical range with the current paragraph window. Copy, Cut, or
an explicit `getSelectedText()` call still reads the requested selection.

Verification commands:

```bash
mvn test
mvn "-Dtest=InDiskCodeAreaTest" "-Djava.awt.headless=false" test
mvn "-DskipTests" package
```

## Performance Characteristics

| Operation | Complexity |
| --- | --- |
| Paragraph lookup | `O(log p)` |
| Range read | `O(r)` |
| Insert | `O(s + i)` |
| Delete | `O(s)` |

`p` is the paragraph count, `r` is the requested range length, `s` is the file
suffix after the edit position, and `i` is inserted text length.

The retained text is disk-backed, but paragraph offsets, cached lines, and
explicitly returned strings remain in memory.

## Known Limitations

- Initial content is accepted as a `String`; loading does not yet stream from a
  `Path`, `Reader`, or channel.
- `CodeArea.getText()` materializes the complete document.
- File edits are synchronous and can block the JavaFX Application Thread.
- Wrapped content and content with visual-only empty-line decorations currently
  use the non-virtual rendering path.
- Repeated edits near the beginning of a large document move substantial disk
  data.
- Positions use Java `int` indices.
- CRLF is normalized because carriage-return control characters are filtered.
- `CodeArea` does not currently expose a public method to close its internally
  owned `InDiskContent` before JVM exit.

Future improvements could add streaming initialization, an explicit control
disposal API, background-safe loading, and a piece-table or gap-buffer strategy
to reduce disk movement during repeated edits.

## Files Changed

- `src/main/java/com/bitifyware/control/InDiskContent.java`
- `src/test/java/com/bitifyware/control/InDiskContentTest.java`
- `src/test/java/com/bitifyware/control/InDiskCodeAreaTest.java`
- `src/test/java/com/bitifyware/example/DiskContentExample.java`
- `docs/DISK_CONTENT_README.md`
- `docs/PR_DESCRIPTION.md`
