package com.bitifyware.control;

import com.bitifyware.control.CodeArea.CodeAreaContent;
import com.bitifyware.control.CodeArea.ParagraphList;
import com.bitifyware.control.CodeArea.ParagraphListChange;
import com.sun.javafx.collections.ListListenerHelper;
import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Disk-backed implementation of CodeArea content for handling very large files.
 * 
 * <p>This implementation stores paragraphs (lines) in a temporary file on disk instead of memory,
 * enabling CodeArea to work with files that would be too large to fit in memory.
 * 
 * <h3>Threading Considerations</h3>
 * <p>Heavy I/O operations should be performed off the JavaFX Application Thread to avoid
 * blocking the UI. Consider loading/saving content in background threads.
 * 
 * <h3>File Format</h3>
 * <p>The disk file uses UTF-8 encoding with '\n' as the line terminator. When reading lines,
 * any trailing '\r' characters are stripped. Full CRLF preservation is a TODO.
 * 
 * <h3>Performance Characteristics</h3>
 * <p>Current implementation uses naive scanning for line positions. For very large files,
 * consider these limitations:
 * <ul>
 *   <li>Line access requires scanning from the beginning (or cached position)</li>
 *   <li>TODO: Implement sparse index of line positions for O(1) random access</li>
 *   <li>TODO: Implement incremental edits without full file rewrite for same-byte-length changes</li>
 *   <li>Small LRU cache helps with sequential access during scrolling</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * DiskContent content = new DiskContent();
 * content.insert(0, largeText, true);
 * 
 * // Use with ContentSwapper to replace CodeArea content
 * ContentSwapper.swapContent(codeArea, content);
 * 
 * // Clean up when done
 * content.close();
 * }</pre>
 * 
 * @see InMemoryContent
 * @see ContentSwapper
 */
public final class DiskContent extends CodeAreaContent implements AutoCloseable {
    
    private static final int DEFAULT_PARAGRAPH_CAPACITY = 32;
    private static final int CACHE_SIZE = 50; // LRU cache for recently accessed lines
    
    private final Path tempFile;
    private final DiskParagraphList paragraphList = new DiskParagraphList();
    private int contentLength = 0;
    private int lineCount = 1; // Always at least one line
    
    // LRU cache for recently accessed lines (index -> line content)
    private final LinkedHashMap<Integer, String> lineCache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    
    /**
     * Creates a new DiskContent with an empty temporary file.
     * 
     * @throws UncheckedIOException if the temporary file cannot be created
     */
    public DiskContent() {
        try {
            tempFile = Files.createTempFile("codearea-", ".txt");
            // Initialize with single empty line
            List<String> initialLines = new ArrayList<>();
            initialLines.add("");
            Files.write(tempFile, initialLines, StandardCharsets.UTF_8);
            lineCount = 1;
            paragraphs = new DiskBackedParagraphList();
            paragraphList.setContent(this);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create temporary file for DiskContent", e);
        }
    }
    
    /**
     * Creates a new DiskContent with initial text content.
     * 
     * @param initialText the initial text content
     * @throws UncheckedIOException if the temporary file cannot be created
     */
    public DiskContent(String initialText) {
        try {
            tempFile = Files.createTempFile("codearea-", ".txt");
            // Initialize with single empty line
            List<String> initialLines = new ArrayList<>();
            initialLines.add("");
            Files.write(tempFile, initialLines, StandardCharsets.UTF_8);
            lineCount = 1;
            paragraphs = new DiskBackedParagraphList();
            paragraphList.setContent(this);
            
            if (initialText != null && !initialText.isEmpty()) {
                insert(0, initialText, false);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create temporary file for DiskContent", e);
        }
    }
    
    @Override
    public String get(int start, int end) {
        if (start < 0 || end > contentLength || start > end) {
            throw new IndexOutOfBoundsException("start=" + start + ", end=" + end + ", length=" + contentLength);
        }
        
        int length = end - start;
        if (length == 0) {
            return "";
        }
        
        try {
            StringBuilder result = new StringBuilder(length);
            
            // Find the starting line and offset within that line
            int[] startPos = findLineAndOffset(start);
            int lineIndex = startPos[0];
            int offsetInLine = startPos[1];
            
            int charsRead = 0;
            
            while (charsRead < length && lineIndex < lineCount) {
                String line = readLine(lineIndex);
                int lineLen = line.length();
                
                // Calculate how many chars to read from this line
                int available = lineLen - offsetInLine;
                int toRead = Math.min(available, length - charsRead);
                
                if (toRead > 0) {
                    result.append(line, offsetInLine, offsetInLine + toRead);
                    charsRead += toRead;
                }
                
                // If we've read to the end of the line and need more, add newline
                if (charsRead < length && offsetInLine + toRead >= lineLen) {
                    // Check if there's a newline character at this position
                    if (lineIndex < lineCount - 1) {
                        result.append('\n');
                        charsRead++;
                    }
                }
                
                lineIndex++;
                offsetInLine = 0;
            }
            
            return result.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read text", e);
        }
    }
    
    @Override
    public void insert(int index, String text, boolean notifyListeners) {
        if (index < 0 || index > contentLength) {
            throw new IndexOutOfBoundsException("index=" + index + ", length=" + contentLength);
        }
        
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        
        text = CodeInputControl.filterInput(text, false, false);
        int textLength = text.length();
        
        if (textLength == 0) {
            return;
        }
        
        try {
            // Split text into lines
            List<String> newLines = splitIntoLines(text);
            
            // Find position to insert
            int[] pos = findLineAndOffset(index);
            int lineIndex = pos[0];
            int offsetInLine = pos[1];
            
            String currentLine = readLine(lineIndex);
            
            // Clear cache before making changes so UI reads fresh data
            lineCache.clear();
            
            if (newLines.size() == 1) {
                // Single line insert - just modify the current line
                String newLine = currentLine.substring(0, offsetInLine) + 
                               newLines.get(0) + 
                               currentLine.substring(offsetInLine);
                replaceLine(lineIndex, newLine);
                fireParagraphListChangeEvent(lineIndex, lineIndex + 1,
                    Collections.singletonList(newLine));
            } else {
                // Multi-line insert - split current line
                String firstPart = currentLine.substring(0, offsetInLine) + newLines.get(0);
                String lastPart = newLines.get(newLines.size() - 1) + currentLine.substring(offsetInLine);
                
                // Replace current line with first part
                replaceLine(lineIndex, firstPart);
                fireParagraphListChangeEvent(lineIndex, lineIndex + 1,
                    Collections.singletonList(firstPart));
                
                // Insert middle lines
                for (int i = 1; i < newLines.size() - 1; i++) {
                    insertLine(lineIndex + i, newLines.get(i));
                }
                
                // Insert last part
                insertLine(lineIndex + newLines.size() - 1, lastPart);
                
                // Fire event for inserted lines
                List<CharSequence> insertedLines = new ArrayList<>();
                for (int i = 1; i < newLines.size(); i++) {
                    insertedLines.add(newLines.get(i));
                }
                fireParagraphListChangeEvent(lineIndex + 1, lineIndex + newLines.size(),
                    Collections.emptyList());
            }
            
            contentLength += textLength;
            
            if (notifyListeners) {
                fireValueChangedEvent();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to insert text", e);
        }
    }
    
    @Override
    public void delete(int start, int end, boolean notifyListeners) {
        if (start > end) {
            throw new IllegalArgumentException("start > end");
        }
        
        if (start < 0 || end > contentLength) {
            throw new IndexOutOfBoundsException("start=" + start + ", end=" + end + ", length=" + contentLength);
        }
        
        int length = end - start;
        if (length == 0) {
            return;
        }
        
        try {
            int[] startPos = findLineAndOffset(start);
            int[] endPos = findLineAndOffset(end);
            
            int startLine = startPos[0];
            int startOffset = startPos[1];
            int endLine = endPos[0];
            int endOffset = endPos[1];
            
            // Clear cache before making changes so UI reads fresh data
            lineCache.clear();
            
            if (startLine == endLine) {
                // Delete within single line
                String line = readLine(startLine);
                String newLine = line.substring(0, startOffset) + line.substring(endOffset);
                replaceLine(startLine, newLine);
                fireParagraphListChangeEvent(startLine, startLine + 1,
                    Collections.singletonList(line));
            } else {
                // Delete spans multiple lines
                String firstLine = readLine(startLine);
                String lastLine = readLine(endLine);
                String mergedLine = firstLine.substring(0, startOffset) + lastLine.substring(endOffset);
                
                // Build list of removed lines
                List<CharSequence> removed = new ArrayList<>();
                for (int i = startLine; i <= endLine; i++) {
                    removed.add(readLine(i));
                }
                
                // Delete lines from startLine+1 to endLine (inclusive)
                for (int i = endLine; i > startLine; i--) {
                    deleteLine(i);
                }
                
                // Update the remaining line
                replaceLine(startLine, mergedLine);
                
                fireParagraphListChangeEvent(startLine, startLine, removed);
                fireParagraphListChangeEvent(startLine, startLine + 1,
                    Collections.singletonList(mergedLine));
            }
            
            contentLength -= length;
            
            if (notifyListeners) {
                fireValueChangedEvent();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete text", e);
        }
    }
    
    @Override
    public int length() {
        return contentLength;
    }
    
    @Override
    public String get() {
        return get(0, contentLength);
    }
    
    @Override
    public String getValue() {
        return get();
    }
    
    /**
     * Returns the observable list of paragraphs (lines) backed by disk.
     * 
     * @return the paragraph list
     */
    public ObservableList<CharSequence> getParagraphList() {
        return paragraphList;
    }
    
    /**
     * Closes and deletes the temporary file used by this DiskContent.
     * After calling this method, this DiskContent should not be used.
     */
    @Override
    public void close() {
        try {
            Files.deleteIfExists(tempFile);
            lineCache.clear();
        } catch (IOException e) {
            // Log but don't throw - cleanup is best effort
            System.err.println("Warning: Failed to delete temp file: " + tempFile);
        }
    }
    
    // ========== Line-level operations ==========
    
    /**
     * Reads a line from the file. Uses caching for performance.
     * 
     * @param lineIndex the zero-based line index
     * @return the line content (without line terminator)
     * @throws IOException if reading fails
     */
    String readLine(int lineIndex) throws IOException {
        // Check cache first
        String cached = lineCache.get(lineIndex);
        if (cached != null) {
            return cached;
        }
        
        // TODO: Use sparse index for O(1) access instead of scanning
        try (BufferedReader reader = Files.newBufferedReader(tempFile, StandardCharsets.UTF_8)) {
            String line = "";
            for (int i = 0; i <= lineIndex; i++) {
                line = reader.readLine();
                if (line == null) {
                    line = "";
                    break;
                }
            }
            // Strip trailing \r if present (TODO: full CRLF preservation)
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            lineCache.put(lineIndex, line);
            return line;
        }
    }
    
    /**
     * Counts the total number of lines in the file.
     * 
     * @return the line count
     * @throws IOException if reading fails
     */
    int countLines() throws IOException {
        // TODO: Maintain line count incrementally instead of counting each time
        return lineCount;
    }
    
    /**
     * Replaces a line at the given index with new content.
     * Uses in-place write if byte length is the same, otherwise rewrites file.
     * 
     * @param lineIndex the line index
     * @param newContent the new line content (without line terminator)
     * @throws IOException if writing fails
     */
    void replaceLine(int lineIndex, String newContent) throws IOException {
        List<String> lines = readAllLines();
        if (lineIndex < 0 || lineIndex >= lines.size()) {
            throw new IndexOutOfBoundsException("lineIndex=" + lineIndex + ", lineCount=" + lines.size());
        }
        lines.set(lineIndex, newContent);
        writeAllLines(lines);
    }
    
    /**
     * Inserts a new line at the given index.
     * 
     * @param lineIndex the line index where the new line should be inserted
     * @param content the line content (without line terminator)
     * @throws IOException if writing fails
     */
    void insertLine(int lineIndex, String content) throws IOException {
        List<String> lines = readAllLines();
        lines.add(lineIndex, content);
        writeAllLines(lines);
    }
    
    /**
     * Deletes the line at the given index.
     * 
     * @param lineIndex the line index to delete
     * @throws IOException if writing fails
     */
    void deleteLine(int lineIndex) throws IOException {
        List<String> lines = readAllLines();
        if (lineIndex < 0 || lineIndex >= lines.size()) {
            throw new IndexOutOfBoundsException("lineIndex=" + lineIndex + ", lineCount=" + lines.size());
        }
        lines.remove(lineIndex);
        writeAllLines(lines);
    }
    
    // ========== Helper methods ==========
    
    /**
     * Finds the line index and offset within that line for a given character position.
     * 
     * @param charPos the character position in the overall content
     * @return array of [lineIndex, offsetInLine]
     */
    private int[] findLineAndOffset(int charPos) {
        try {
            int currentPos = 0;
            int lineIndex = 0;
            
            while (lineIndex < lineCount) {
                String line = readLine(lineIndex);
                int lineLen = line.length();
                
                // Check if position is within this line's content
                if (charPos >= currentPos && charPos <= currentPos + lineLen) {
                    int offset = charPos - currentPos;
                    return new int[] { lineIndex, offset };
                }
                
                // Move to next line (account for newline character)
                currentPos += lineLen + 1;
                lineIndex++;
            }
            
            // Position is at the very end - find the last line's length
            if (lineCount > 0) {
                String lastLine = readLine(lineCount - 1);
                return new int[] { lineCount - 1, lastLine.length() };
            }
            return new int[] { 0, 0 };
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to find line position", e);
        }
    }
    
    /**
     * Splits text into lines (preserving empty lines).
     * 
     * @param text the text to split
     * @return list of lines (without line terminators)
     */
    private List<String> splitIntoLines(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines.add(line.toString());
                line = new StringBuilder();
            } else if (c != '\r') {
                line.append(c);
            }
        }
        lines.add(line.toString());
        
        return lines;
    }
    
    /**
     * Reads all lines from the file.
     * 
     * @return list of all lines
     * @throws IOException if reading fails
     */
    private List<String> readAllLines() throws IOException {
        List<String> lines = Files.readAllLines(tempFile, StandardCharsets.UTF_8);
        // Ensure we always have at least one line
        if (lines.isEmpty()) {
            lines.add("");
        }
        // Strip trailing \r from each line (TODO: full CRLF preservation)
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.endsWith("\r")) {
                lines.set(i, line.substring(0, line.length() - 1));
            }
        }
        return lines;
    }
    
    /**
     * Writes all lines to the file atomically.
     * 
     * @param lines the lines to write
     * @throws IOException if writing fails
     */
    private void writeAllLines(List<String> lines) throws IOException {
        // Write to temporary file then atomic move
        Path tempTarget = Files.createTempFile("codearea-tmp-", ".txt");
        try {
            Files.write(tempTarget, lines, StandardCharsets.UTF_8);
            Files.move(tempTarget, tempFile, StandardCopyOption.REPLACE_EXISTING, 
                      StandardCopyOption.ATOMIC_MOVE);
            lineCount = lines.size();
        } catch (IOException e) {
            Files.deleteIfExists(tempTarget);
            throw e;
        }
    }
    
    /**
     * Fires a paragraph list change event.
     */
    private void fireParagraphListChangeEvent(int from, int to, List<CharSequence> removed) {
        ParagraphListChange change = new ParagraphListChange(paragraphList, from, to, removed);
        ListListenerHelper.fireValueChangedEvent(paragraphList.getListenerHelper(), change);
    }
    
    // ========== Inner Classes ==========
    
    /**
     * List implementation that provides the paragraphs field required by CodeAreaContent.
     * This list is backed by the DiskContent and delegates to its line operations.
     */
    private class DiskBackedParagraphList extends AbstractList<StringBuilder> {
        @Override
        public StringBuilder get(int index) {
            try {
                return new StringBuilder(readLine(index));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read line " + index, e);
            }
        }
        
        @Override
        public int size() {
            return lineCount;
        }
    }
    
    /**
     * Observable list wrapper for paragraphs backed by disk storage.
     * Provides integration with CodeArea's paragraph-based rendering.
     */
    private class DiskParagraphList extends AbstractList<CharSequence>
            implements ObservableList<CharSequence> {
        
        private CodeAreaContent content;
        private ListListenerHelper<CharSequence> listenerHelper;
        
        @Override
        public CharSequence get(int index) {
            try {
                return readLine(index);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read line " + index, e);
            }
        }
        
        @Override
        public int size() {
            return lineCount;
        }
        
        @Override
        public void addListener(ListChangeListener<? super CharSequence> listener) {
            listenerHelper = ListListenerHelper.addListener(listenerHelper, listener);
        }
        
        @Override
        public void removeListener(ListChangeListener<? super CharSequence> listener) {
            listenerHelper = ListListenerHelper.removeListener(listenerHelper, listener);
        }
        
        @Override
        public boolean addAll(Collection<? extends CharSequence> c) {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public boolean addAll(CharSequence... elements) {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public boolean setAll(Collection<? extends CharSequence> c) {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public boolean setAll(CharSequence... elements) {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public boolean removeAll(CharSequence... elements) {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public boolean retainAll(CharSequence... elements) {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public void remove(int from, int to) {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public void addListener(InvalidationListener listener) {
            listenerHelper = ListListenerHelper.addListener(listenerHelper, listener);
        }
        
        @Override
        public void removeListener(InvalidationListener listener) {
            listenerHelper = ListListenerHelper.removeListener(listenerHelper, listener);
        }
        
        public void setContent(CodeAreaContent content) {
            this.content = content;
        }
        
        public ListListenerHelper<CharSequence> getListenerHelper() {
            return listenerHelper;
        }
    }
}
