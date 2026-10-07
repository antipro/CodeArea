package com.bitifyware.control;

import java.text.CharacterIterator;
import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Thread-confined, bounded-buffer reader over one content version. Separate
 * cursors may read concurrently with FX-thread edits; stale readers fail fast.
 * No document-sized String is created unless explicitly requested via toString().
 */
public final class TextCursor implements AutoCloseable {
    private static final int BUFFER_SIZE = 4096;
    final TextSource source;
    private final long version;
    private final int length;
    private int position;
    private int bufferStart = -1;
    private String buffer = "";
    private int accesses;
    private boolean closed;

    TextCursor(TextSource source, int position) {
        this.source = source;
        synchronized (source) {
            source.checkOpen();
            version = source.version();
            length = source.length();
            seek(position);
        }
    }

    private TextCursor(TextCursor original) {
        source = original.source;
        version = original.version;
        length = original.length;
        position = original.position;
        checkValid();
    }

    public long version() { return version; }
    public int length() { checkValid(); return length; }
    public int position() { checkValid(); return position; }

    public void seek(int position) {
        checkValid();
        if (position < 0 || position > length) throw new IndexOutOfBoundsException();
        this.position = position;
    }

    /** Returns a UTF-16 unit, or -1 at the end. */
    public int next() {
        checkValid();
        return position == length ? -1 : charAt(position++);
    }

    /** Moves backwards and returns a UTF-16 unit, or -1 at the beginning. */
    public int previous() {
        checkValid();
        return position == 0 ? -1 : charAt(--position);
    }

    public int read(char[] target, int offset, int count) {
        Objects.checkFromIndexSize(offset, count, target.length);
        checkValid();
        if (count == 0) return 0;
        if (position == length) return -1;
        int remaining = Math.min(count, length - position);
        int read = 0;
        while (read < remaining) {
            ensureBuffer(position);
            int n = Math.min(remaining - read, bufferStart + buffer.length() - position);
            buffer.getChars(position - bufferStart, position - bufferStart + n, target, offset + read);
            position += n;
            read += n;
        }
        checkValid();
        return read;
    }

    public char charAt(int index) {
        checkValid();
        return scanCharAt(index);
    }

    char scanCharAt(int index) {
        Objects.checkIndex(index, length);
        if (closed) throw new IllegalStateException("Cursor is closed");
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Read interrupted");
        // Also check during regex backtracking inside an already cached block.
        if ((accesses++ & 255) == 0) checkValid();
        ensureBuffer(index);
        return buffer.charAt(index - bufferStart);
    }

    private void ensureBuffer(int index) {
        if (index >= bufferStart && index - bufferStart < buffer.length()) return;
        synchronized (source) {
            checkValid();
            bufferStart = index / BUFFER_SIZE * BUFFER_SIZE;
            buffer = source.read(bufferStart, bufferStart + Math.min(BUFFER_SIZE, length - bufferStart));
        }
    }

    public void checkValid() {
        if (closed) throw new IllegalStateException("Cursor is closed");
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Read interrupted");
        synchronized (source) {
            source.checkOpen();
            if (source.version() != version) {
                throw new ConcurrentModificationException("Content changed since cursor creation");
            }
        }
    }

    public TextCursor fork() { return new TextCursor(this); }

    /** Lazy random-access view for Matcher; does not concatenate the document. */
    public CharSequence asCharSequence() {
        checkValid();
        return new Sequence(0, length);
    }

    /** Adapter for BreakIterator, with independent iterator positioning. */
    public CharacterIterator asCharacterIterator() {
        checkValid();
        return new CursorCharacterIterator(this, 0);
    }

    @Override public void close() {
        closed = true;
        buffer = "";
    }

    private final class Sequence implements CharSequence {
        private final int start, end;
        Sequence(int start, int end) { this.start = start; this.end = end; }
        @Override public int length() { checkValid(); return end - start; }
        @Override public char charAt(int index) {
            return TextCursor.this.scanCharAt(start + Objects.checkIndex(index, end - start));
        }
        @Override public CharSequence subSequence(int from, int to) {
            Objects.checkFromToIndex(from, to, end - start);
            checkValid();
            return new Sequence(start + from, start + to);
        }
        @Override public String toString() {
            StringBuilder result = new StringBuilder(end - start);
            for (int i = start; i < end; i++) result.append(TextCursor.this.charAt(i));
            checkValid();
            return result.toString();
        }
    }

    private static final class CursorCharacterIterator implements CharacterIterator {
        private final TextCursor text;
        private int index;
        CursorCharacterIterator(TextCursor text, int index) { this.text = text; this.index = index; }
        @Override public char first() { index = 0; return current(); }
        @Override public char last() { index = text.length == 0 ? 0 : text.length - 1; return current(); }
        @Override public char current() { text.checkValid(); return index == text.length ? DONE : text.charAt(index); }
        @Override public char next() { if (index < text.length) index++; return current(); }
        @Override public char previous() { if (index == 0) return DONE; index--; return current(); }
        @Override public char setIndex(int index) {
            if (index < 0 || index > text.length) throw new IllegalArgumentException();
            this.index = index;
            return current();
        }
        @Override public int getBeginIndex() { return 0; }
        @Override public int getEndIndex() { return text.length(); }
        @Override public int getIndex() { return index; }
        @Override public Object clone() { return new CursorCharacterIterator(text.fork(), index); }
    }
}
