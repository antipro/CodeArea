package com.bitifyware.control;

import com.bitifyware.control.CodeArea.CodeAreaContent;
import com.bitifyware.control.CodeArea.ParagraphListChange;
import com.sun.javafx.collections.ListListenerHelper;
import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Disk-backed {@link CodeArea} content intended for files that should not be
 * retained as one large in-memory string.
 *
 * <p>The backing file stores Java UTF-16 code units in big-endian order. The
 * fixed-width representation makes a character position a direct file offset,
 * including positions around supplementary characters. Inserts and deletes
 * move file data in bounded chunks rather than loading every paragraph into
 * memory. Paragraph start positions and a small LRU line cache are retained in
 * memory to support rendering.</p>
 *
 * <p>This class has the same single-threaded usage expectation as JavaFX
 * controls. Call {@link #close()} when the owning control is no longer used.</p>
 */
public final class InDiskContent extends CodeAreaContent implements AutoCloseable {

    private static final int CACHE_SIZE = 50;
    private static final int COPY_BUFFER_BYTES = 64 * 1024;

    private final Path tempFile;
    private final FileChannel channel;
    private final DiskParagraphList paragraphList = new DiskParagraphList();
    private final LinkedHashMap<Integer, String> lineCache =
            new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
                    return size() > CACHE_SIZE;
                }
            };

    private int[] lineStarts = new int[16];
    private int lineCount = 1;
    private int contentLength;
    private boolean closed;

    public InDiskContent() {
        this(null);
    }

    public InDiskContent(String initialText) {
        try {
            tempFile = Files.createTempFile("codearea-", ".content");
            tempFile.toFile().deleteOnExit();
            channel = FileChannel.open(tempFile, StandardOpenOption.READ, StandardOpenOption.WRITE);
            paragraphs = new DiskBackedParagraphList();
            lineStarts[0] = 0;

            if (initialText != null && !initialText.isEmpty()) {
                insert(0, initialText, false);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create disk-backed content", e);
        }
    }

    @Override
    public String get(int start, int end) {
        checkRange(start, end);
        ensureOpen();
        try {
            return readText(start, end);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read disk-backed content", e);
        }
    }

    @Override
    public void insert(int index, String text, boolean notifyListeners) {
        ensureOpen();
        if (index < 0 || index > contentLength) {
            throw new IndexOutOfBoundsException("index=" + index + ", length=" + contentLength);
        }
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }

        text = filterInput(text);
        if (text.isEmpty()) {
            return;
        }

        int paragraphIndex = getParagraphIndex(index);
        try {
            shiftRight(index, text.length());
            writeText(index, text);
            updateLineStartsAfterInsert(index, text);
            contentLength += text.length();
            lineCache.clear();

            int addedParagraphs = countNewlines(text);
            fireParagraphListChangeEvent(paragraphIndex, paragraphIndex + 1,
                    Collections.singletonList(""));
            if (addedParagraphs > 0) {
                fireParagraphListChangeEvent(paragraphIndex + 1,
                        paragraphIndex + addedParagraphs + 1, Collections.emptyList());
            }
            if (notifyListeners) {
                fireValueChangedEvent();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to insert disk-backed content", e);
        }
    }

    @Override
    public void delete(int start, int end, boolean notifyListeners) {
        ensureOpen();
        if (start > end) {
            throw new IllegalArgumentException("start > end");
        }
        checkRange(start, end);
        if (start == end) {
            return;
        }

        int firstParagraph = getParagraphIndex(start);
        int lastParagraph = getParagraphIndex(end);

        try {
            shiftLeft(start, end);
            updateLineStartsAfterDelete(start, end);
            contentLength -= end - start;
            lineCache.clear();

            if (lastParagraph > firstParagraph) {
                fireParagraphListChangeEvent(firstParagraph + 1, firstParagraph + 1,
                        Collections.nCopies(lastParagraph - firstParagraph, ""));
            }
            fireParagraphListChangeEvent(firstParagraph, firstParagraph + 1,
                    Collections.singletonList(""));
            if (notifyListeners) {
                fireValueChangedEvent();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete disk-backed content", e);
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

    @Override
    public ObservableList<CharSequence> getParagraphList() {
        return paragraphList;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        lineCache.clear();
        try {
            channel.close();
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to close disk-backed content", e);
        }
    }

    private String readLineUnchecked(int index) {
        try {
            return readLine(index);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read paragraph " + index, e);
        }
    }

    private String readLine(int index) throws IOException {
        ensureOpen();
        if (index < 0 || index >= lineCount) {
            throw new IndexOutOfBoundsException("paragraph=" + index + ", count=" + lineCount);
        }
        String cached = lineCache.get(index);
        if (cached != null) {
            return cached;
        }
        int start = lineStarts[index];
        int end = index + 1 < lineCount ? lineStarts[index + 1] - 1 : contentLength;
        String line = readText(start, end);
        lineCache.put(index, line);
        return line;
    }

    private String readText(int start, int end) throws IOException {
        char[] result = new char[end - start];
        ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(COPY_BUFFER_BYTES,
                Math.max(2L, (long) result.length * 2)));
        long filePosition = (long) start * 2;
        int resultPosition = 0;
        while (resultPosition < result.length) {
            int chars = Math.min(buffer.capacity() / 2, result.length - resultPosition);
            buffer.clear().limit(chars * 2);
            readFully(buffer, filePosition);
            buffer.flip();
            for (int i = 0; i < chars; i++) {
                result[resultPosition++] = buffer.getChar();
            }
            filePosition += (long) chars * 2;
        }
        return new String(result);
    }

    private void writeText(int index, String text) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(COPY_BUFFER_BYTES,
                Math.max(2L, (long) text.length() * 2)));
        long filePosition = (long) index * 2;
        int textPosition = 0;
        while (textPosition < text.length()) {
            buffer.clear();
            while (textPosition < text.length() && buffer.remaining() >= 2) {
                buffer.putChar(text.charAt(textPosition++));
            }
            buffer.flip();
            writeFully(buffer, filePosition);
            filePosition += buffer.limit();
        }
    }

    private void shiftRight(int index, int characterCount) throws IOException {
        long sourceEnd = (long) contentLength * 2;
        long sourceStart = (long) index * 2;
        long displacement = (long) characterCount * 2;
        channel.truncate(sourceEnd + displacement);
        ByteBuffer buffer = ByteBuffer.allocate(COPY_BUFFER_BYTES);
        while (sourceEnd > sourceStart) {
            int bytes = (int) Math.min(buffer.capacity(), sourceEnd - sourceStart);
            long chunkStart = sourceEnd - bytes;
            buffer.clear().limit(bytes);
            readFully(buffer, chunkStart);
            buffer.flip();
            writeFully(buffer, chunkStart + displacement);
            sourceEnd = chunkStart;
        }
    }

    private void shiftLeft(int start, int end) throws IOException {
        long source = (long) end * 2;
        long target = (long) start * 2;
        long fileEnd = (long) contentLength * 2;
        ByteBuffer buffer = ByteBuffer.allocate(COPY_BUFFER_BYTES);
        while (source < fileEnd) {
            int bytes = (int) Math.min(buffer.capacity(), fileEnd - source);
            buffer.clear().limit(bytes);
            readFully(buffer, source);
            buffer.flip();
            writeFully(buffer, target);
            source += bytes;
            target += bytes;
        }
        channel.truncate(fileEnd - (long) (end - start) * 2);
    }

    private void readFully(ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position);
            if (read < 0) {
                throw new IOException("Unexpected end of backing file");
            }
            if (read == 0) {
                continue;
            }
            position += read;
        }
    }

    private void writeFully(ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            int written = channel.write(buffer, position);
            if (written == 0) {
                continue;
            }
            position += written;
        }
    }

    private void updateLineStartsAfterInsert(int index, String text) {
        int additions = countNewlines(text);
        ensureLineCapacity(lineCount + additions);
        int split = upperBound(lineStarts, lineCount, index);
        System.arraycopy(lineStarts, split, lineStarts, split + additions, lineCount - split);
        for (int i = split + additions; i < lineCount + additions; i++) {
            lineStarts[i] += text.length();
        }
        int destination = split;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lineStarts[destination++] = index + i + 1;
            }
        }
        lineCount += additions;
    }

    private void updateLineStartsAfterDelete(int start, int end) {
        int keptBefore = upperBound(lineStarts, lineCount, start);
        int firstAfter = upperBound(lineStarts, lineCount, end);
        int shift = end - start;
        int remaining = lineCount - firstAfter;
        for (int i = 0; i < remaining; i++) {
            lineStarts[keptBefore + i] = lineStarts[firstAfter + i] - shift;
        }
        lineCount = keptBefore + remaining;
        if (lineCount == 0) {
            lineStarts[0] = 0;
            lineCount = 1;
        }
    }

    @Override
    protected int getParagraphIndex(int characterPosition) {
        int low = 0;
        int high = lineCount;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (lineStarts[middle] <= characterPosition) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return Math.max(0, low - 1);
    }

    @Override
    protected int getParagraphStart(int paragraphIndex) {
        if (paragraphIndex < 0 || paragraphIndex >= lineCount) {
            throw new IndexOutOfBoundsException("paragraph=" + paragraphIndex + ", count=" + lineCount);
        }
        return lineStarts[paragraphIndex];
    }

    private static int upperBound(int[] values, int size, int value) {
        int low = 0;
        int high = size;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (values[middle] <= value) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private void ensureLineCapacity(int required) {
        if (required > lineStarts.length) {
            lineStarts = Arrays.copyOf(lineStarts, Math.max(required, lineStarts.length * 2));
        }
    }

    private static int countNewlines(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private static String filterInput(String text) {
        StringBuilder filtered = null;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            boolean invalid = character == 0x7f
                    || (character < 0x20 && character != '\n' && character != '\t');
            if (invalid) {
                if (filtered == null) {
                    filtered = new StringBuilder(text.length()).append(text, 0, i);
                }
            } else if (filtered != null) {
                filtered.append(character);
            }
        }
        return filtered == null ? text : filtered.toString();
    }

    private void checkRange(int start, int end) {
        if (start < 0 || end > contentLength || start > end) {
            throw new IndexOutOfBoundsException(
                    "start=" + start + ", end=" + end + ", length=" + contentLength);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Disk-backed content is closed");
        }
    }

    private void fireParagraphListChangeEvent(int from, int to, List<CharSequence> removed) {
        ParagraphListChange change = new ParagraphListChange(paragraphList, from, to, removed);
        ListListenerHelper.fireValueChangedEvent(paragraphList.getListenerHelper(), change);
    }

    private final class DiskBackedParagraphList extends AbstractList<StringBuilder> {
        @Override
        public StringBuilder get(int index) {
            return new StringBuilder(readLineUnchecked(index));
        }

        @Override
        public int size() {
            return lineCount;
        }
    }

    private final class DiskParagraphList extends AbstractList<CharSequence>
            implements ObservableList<CharSequence> {
        private ListListenerHelper<CharSequence> listenerHelper;

        @Override
        public CharSequence get(int index) {
            return readLineUnchecked(index);
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
        public void addListener(InvalidationListener listener) {
            listenerHelper = ListListenerHelper.addListener(listenerHelper, listener);
        }

        @Override
        public void removeListener(InvalidationListener listener) {
            listenerHelper = ListListenerHelper.removeListener(listenerHelper, listener);
        }

        @Override
        public boolean addAll(Collection<? extends CharSequence> values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(CharSequence... values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean setAll(Collection<? extends CharSequence> values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean setAll(CharSequence... values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(CharSequence... values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(CharSequence... values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(int from, int to) {
            throw new UnsupportedOperationException();
        }

        private ListListenerHelper<CharSequence> getListenerHelper() {
            return listenerHelper;
        }
    }
}
