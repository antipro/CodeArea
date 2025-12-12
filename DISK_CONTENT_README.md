# DiskContent - Disk-Backed Storage for Large Files

## Quick Start

```java
import com.bitifyware.control.*;

// Create disk-backed content
DiskContent content = new DiskContent("Your large text content here...");

// Create CodeArea
CodeArea codeArea = new CodeArea();

// Swap to disk-backed content
try {
    ContentSwapper.swapContent(codeArea, content);
    codeArea.setText(content.get());
} catch (ReflectiveOperationException e) {
    e.printStackTrace();
}

// Clean up when done
content.close();
```

## What's New

This feature adds disk-backed content storage to CodeArea, enabling handling of very large files that don't fit in memory.

### New Classes

1. **DiskContent** - Disk-backed ContentBase implementation
   - Stores text in temporary file on disk
   - Maintains line-based structure for efficient paragraph access
   - LRU cache for recently accessed lines
   - AutoCloseable for resource management

2. **ContentSwapper** - Reflection utility for runtime content swapping
   - Swaps the private `content` field in CodeInputControl
   - Enables switching between InMemoryContent and DiskContent

3. **DiskContentExample** - JavaFX demo application
   - Shows how to use DiskContent with CodeArea
   - Interactive buttons to load large/small content

4. **DiskContentTest** - Comprehensive test suite
   - Tests basic operations, multi-line handling, edge cases

## Features

- ✅ **Zero modifications** to CodeArea/CodeInputControl
- ✅ **Drop-in replacement** for InMemoryContent
- ✅ **Observable paragraph list** for CodeArea rendering
- ✅ **LRU caching** for performance
- ✅ **Atomic file operations** for crash safety
- ✅ **UTF-8 encoding** with newline handling

## Performance

- **Line access**: O(n) with LRU cache (TODO: sparse index for O(1))
- **Cache size**: 50 lines (configurable in source)
- **File writes**: Atomic (temp file + atomic move)

## Threading

Heavy I/O operations should be performed off the JavaFX Application Thread:

```java
Task<DiskContent> loadTask = new Task<>() {
    @Override
    protected DiskContent call() throws Exception {
        return new DiskContent(veryLargeText);
    }
};

loadTask.setOnSucceeded(e -> {
    try {
        DiskContent content = loadTask.getValue();
        ContentSwapper.swapContent(codeArea, content);
        codeArea.setText(content.get());
    } catch (Exception ex) {
        ex.printStackTrace();
    }
});

new Thread(loadTask).start();
```

## Limitations

1. **Line position scanning**: Current implementation scans from file start (cached). Future: sparse index.
2. **CRLF handling**: Strips `\r` on read. Future: full line ending preservation.
3. **File rewrites**: Size-changing edits rewrite entire file. Future: incremental updates.
4. **Reflection fragility**: ContentSwapper uses reflection; may break with internal changes.

## Example Application

Run the demo:

```bash
java com.bitifyware.example.DiskContentExample
```

Buttons:
- **Load Disk Content (Large)**: Loads 10,000 lines from disk
- **Load Disk Content (Small)**: Loads 5 lines for testing
- **Show Stats**: Displays line count and character count

## API Reference

### DiskContent

```java
// Constructors
DiskContent()                      // Empty content
DiskContent(String initialText)    // With initial text

// ContentBase methods
String get(int start, int end)
void insert(int index, String text, boolean notifyListeners)
void delete(int start, int end, boolean notifyListeners)
int length()
String get()
String getValue()

// Paragraph list
ObservableList<CharSequence> getParagraphList()

// Cleanup
void close()
```

### ContentSwapper

```java
// Swap content in CodeArea
static void swapContent(CodeArea codeArea, ContentBase newContent)
        throws ReflectiveOperationException

// Swap content in any CodeInputControl
static void swapContent(CodeInputControl control, ContentBase newContent)
        throws ReflectiveOperationException
```

## Testing

Run tests:

```bash
java com.bitifyware.control.DiskContentTest
```

## Security

- ✅ CodeQL scan: 0 vulnerabilities
- ✅ Secure temp file creation
- ✅ No hardcoded credentials
- ✅ Documented reflection risks

## Contributing

Future enhancements welcome:

1. Sparse index for O(1) line access
2. Full CRLF preservation
3. In-place writes for same-byte-length edits
4. Async loading with progress
5. Configurable cache size

## License

Same as CodeArea project.
