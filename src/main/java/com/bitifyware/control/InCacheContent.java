package com.bitifyware.control;

import com.bitifyware.control.CodeArea.CodeAreaContent;
import com.bitifyware.control.CodeArea.ParagraphList;
import com.bitifyware.control.CodeArea.ParagraphListChange;
import com.sun.javafx.collections.ListListenerHelper;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.io.Reader;
import java.util.AbstractList;
import java.util.List;
import java.util.Objects;

/**
 * Editable disk-mapped paragraph content (Java 21+ FFM). Only paragraph metadata
 * lives on the heap; edits append affected paragraphs, never rewrite the file.
 * Positions use UTF-16 and int, like CodeArea. Explicit get()/getValue() still
 * materialize the entire document. Edits remain single-owner (FX thread when
 * attached); independent cursors synchronize short reads with edits and close.
 * Close the content when its owning editor is no longer used. A Cleaner is a
 * fallback, not a substitute for close. Retained lazy change snapshots hold a
 * mapping lease until reclaimed, including across a cache installation.
 */
public final class InCacheContent extends CodeAreaContent implements AutoCloseable {

    /** Configure the cache root before creating any cached content. Repeated use of the same root is safe. */
    public static void initializeCacheDirectory(java.nio.file.Path root) {
        try {
            CacheDirectory.current(Objects.requireNonNull(root));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Available disk space on the mapped cache's actual filesystem. */
    public static long getCacheUsableSpace() throws IOException {
        return java.nio.file.Files.getFileStore(CacheDirectory.initialized().directory()).getUsableSpace();
    }

    private record Entry(long offset, int length) { }
    private MappedCache cache = new MappedCache();
    private ParagraphIndex entries = new ParagraphIndex(true, false);
    private final ParagraphList paragraphList = new ParagraphList();
    private int contentLength;

    public InCacheContent() {
        entries.append(0, 1, 0);
        paragraphs = new AbstractList<>() {
            @Override public StringBuilder get(int index) {
                checkContentOpen();
                return new StringBuilder(read(entry(index)));
            }
            @Override public int size() { return entries.size(); }
        };
        paragraphList.setContent(this);
    }

    public InCacheContent(String text) {
        this();
        try {
            insert(0, text, false);
        } catch (RuntimeException | Error e) {
            close();
            throw e;
        }
    }

    /** Streams a reader without a full-document String. Does not close the reader. */
    public InCacheContent(Reader reader) throws IOException {
        this();
        try {
            Objects.requireNonNull(reader);
            entries = new ParagraphIndex(true, false);
            char[] buffer = new char[8192];
            StringBuilder line = new StringBuilder();
            int count;
            while ((count = reader.read(buffer)) != -1) {
                String filtered = CodeInputControl.filterInput(new String(buffer, 0, count), false, false);
                for (int i = 0; i < filtered.length(); i++) {
                    char c = filtered.charAt(i);
                    if (c == '\n') {
                        append(entries, store(line));
                        line.setLength(0);
                    } else {
                        line.append(c);
                    }
                }
            }
            append(entries, store(line));
            contentLength = Math.toIntExact(entries.characters() - 1);
        } catch (IOException | RuntimeException | Error e) {
            close();
            throw e;
        }
    }

    private Entry store(CharSequence line) {
        return new Entry(cache.append(line), line.length());
    }

    private String read(Entry entry) {
        return cache.read(entry.offset, entry.length);
    }

    private Entry entry(int index) { return new Entry(entries.offset(index), entries.span(index) - 1); }

    private static void append(ParagraphIndex index, Entry entry) {
        index.append(entry.offset, Math.addExact(entry.length, 1), 0);
    }

    @Override public synchronized int getParagraphStart(int index) {
        checkContentOpen();
        Objects.checkIndex(index, entries.size());
        return Math.toIntExact(entries.start(index));
    }

    @Override public synchronized int getParagraphLength(int index) {
        checkContentOpen();
        return entries.span(index) - 1;
    }

    @Override public synchronized int getParagraphIndex(int position) {
        checkContentOpen();
        if (position < 0 || position > contentLength) throw new IndexOutOfBoundsException();
        return entries.atPosition(position);
    }

    @Override public synchronized String get(int start, int end) {
        checkContentOpen();
        Objects.checkFromToIndex(start, end, contentLength);
        StringBuilder result = new StringBuilder(end - start);
        int line = getParagraphIndex(start);
        int position = start;
        while (position < end) {
            Entry entry = entry(line);
            int offset = position - Math.toIntExact(entries.start(line));
            int count = Math.min(entry.length - offset, end - position);
            result.append(cache.read(entry.offset + offset, count));
            position += count;
            if (position < end) {
                result.append('\n');
                position++;
                line++;
            }
        }
        return result.toString();
    }

    @Override public synchronized void insert(int index, String text, boolean notifyListeners) {
        checkContentOpen();
        if (index < 0 || index > contentLength) throw new IndexOutOfBoundsException();
        if (text == null) throw new IllegalArgumentException("text cannot be null");
        text = CodeInputControl.filterInput(text, false, false);
        if (text.isEmpty()) return;
        Math.addExact(contentLength, text.length());
        int line = getParagraphIndex(index);
        Entry old = entry(line);
        String original = read(old);
        int offset = index - getParagraphStart(line);
        String replacement = original.substring(0, offset) + text + original.substring(offset);
        ParagraphIndex added = new ParagraphIndex(true, false);
        int from = 0;
        for (int i = 0; i < replacement.length(); i++) {
            if (replacement.charAt(i) == '\n') {
                append(added, store(replacement.subSequence(from, i)));
                from = i + 1;
            }
        }
        append(added, store(replacement.subSequence(from, replacement.length())));
        int addedCount = added.size();
        markContentModified();
        ParagraphIndex removed = entries.splice(line, 1, added);
        contentLength += text.length();
        publishContentChange(index, 0, text.length());
        fireChange(line, line + addedCount, removed, cache);
        if (notifyListeners) fireValueChangedEvent();
    }

    @Override public synchronized void delete(int start, int end, boolean notifyListeners) {
        checkContentOpen();
        if (start > end) throw new IllegalArgumentException();
        Objects.checkFromToIndex(start, end, contentLength);
        if (start == end) return;
        int first = getParagraphIndex(start), last = getParagraphIndex(end);
        String leading = read(entry(first)), trailing = read(entry(last));
        Entry merged = store(leading.substring(0, start - getParagraphStart(first))
                + trailing.substring(end - getParagraphStart(last)));
        ParagraphIndex added = new ParagraphIndex(true, false);
        append(added, merged);
        markContentModified();
        ParagraphIndex removed = entries.splice(first, last - first + 1, added);
        contentLength -= end - start;
        publishContentChange(start, end - start, 0);
        fireChange(first, first + 1, removed, cache);
        if (notifyListeners) fireValueChangedEvent();
    }

    /**
     * Transfers a prepared cache without copying text or replacing subscriptions.
     * The source becomes closed; closing it again does not close the transferred
     * storage. Existing cursors on both models become stale.
     */
    public synchronized void install(InCacheContent prepared) {
        Objects.requireNonNull(prepared);
        if (prepared == this) throw new IllegalArgumentException("Cannot install content into itself");
        checkContentOpen();
        synchronized (prepared) {
            prepared.checkContentOpen();
            MappedCache previous = cache;
            ParagraphIndex removed = entries;
            int oldLength = contentLength;
            markContentModified();
            cache = prepared.cache;
            entries = prepared.entries;
            contentLength = prepared.contentLength;
            prepared.closed = true;
            prepared.markContentModified();
            prepared.cache = null;
            prepared.entries = new ParagraphIndex(true, false);
            try {
                publishContentChange(0, oldLength, contentLength);
                fireChange(0, entries.size(), removed, previous);
                fireValueChangedEvent();
            } finally {
                previous.close();
            }
        }
    }

    private void fireChange(int from, int to, ParagraphIndex removed, MappedCache source) {
        var helper = paragraphList.getListenerHelper();
        if (helper == null) return;
        // Lazy immutable snapshots: deleting many lines doesn't decode them all.
        List<CharSequence> old = new AbstractList<>() {
            @Override public CharSequence get(int index) {
                return source.read(removed.offset(index), removed.span(index) - 1);
            }
            @Override public int size() { return removed.size(); }
        };
        // A listener may retain a lazy removed snapshot beyond a reload. Its
        // lease keeps only the old mapping alive, until that snapshot is GC'd.
        source.retainFor(old);
        ListListenerHelper.fireValueChangedEvent(helper,
                new ParagraphListChange(paragraphList, from, to, old));
    }

    @Override public synchronized int length() { checkContentOpen(); return contentLength; }
    @Override public synchronized String get() { return get(0, length()); }
    @Override public String getValue() { return get(); }
    @Override public synchronized ObservableList<CharSequence> getParagraphList() {
        checkContentOpen();
        return paragraphList;
    }
    @Override protected void checkContentOpen() {
        if (closed) throw new IllegalStateException("Content cache is closed");
        cache.checkOpen();
    }
    @Override public synchronized void close() {
        if (!closed) {
            closed = true;
            markContentModified();
            cache.close();
        }
    }
    private boolean closed;
}
