package com.bitifyware.control;

import com.bitifyware.control.CodeArea.CodeAreaContent;
import com.bitifyware.control.CodeArea.ParagraphList;
import com.bitifyware.control.CodeArea.ParagraphListChange;
import com.sun.javafx.collections.ListListenerHelper;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.io.Reader;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Editable disk-mapped paragraph content (Java 21+ FFM). Only paragraph metadata
 * lives on the heap; edits append affected paragraphs, never rewrite the file.
 * Positions use UTF-16 and int, like CodeArea. Explicit get()/getValue() still
 * materialize the entire document. Edits remain single-owner (FX thread when
 * attached); independent cursors synchronize short reads with edits and close.
 * Close the content when its owning editor is no longer used. A Cleaner is a
 * fallback, not a substitute for close. Obsolete edit records remain until close.
 */
public final class InCacheContent extends CodeAreaContent implements AutoCloseable {
    private record Entry(long offset, int length) { }
    private final MappedCache cache = new MappedCache();
    private final List<Entry> entries = new ArrayList<>();
    private final ParagraphList paragraphList = new ParagraphList();
    private int[] starts = {0};
    private int contentLength;

    public InCacheContent() {
        entries.add(new Entry(0, 0));
        paragraphs = new AbstractList<>() {
            @Override public StringBuilder get(int index) {
                return new StringBuilder(read(entries.get(index)));
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
            entries.clear();
            char[] buffer = new char[8192];
            StringBuilder line = new StringBuilder();
            int count;
            while ((count = reader.read(buffer)) != -1) {
                String filtered = CodeInputControl.filterInput(new String(buffer, 0, count), false, false);
                for (int i = 0; i < filtered.length(); i++) {
                    char c = filtered.charAt(i);
                    if (c == '\n') {
                        entries.add(store(line));
                        line.setLength(0);
                    } else {
                        line.append(c);
                    }
                }
            }
            entries.add(store(line));
            reindex();
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

    private void reindex() {
        int[] updated = new int[entries.size()];
        long length = 0;
        for (int i = 0; i < entries.size(); i++) {
            updated[i] = Math.toIntExact(length);
            length += entries.get(i).length;
            if (i + 1 < entries.size()) length++;
        }
        contentLength = Math.toIntExact(length);
        starts = updated;
    }

    @Override public synchronized int getParagraphStart(int index) {
        cache.checkOpen();
        return starts[Objects.checkIndex(index, entries.size())];
    }

    @Override public synchronized int getParagraphLength(int index) {
        cache.checkOpen();
        return entries.get(index).length;
    }

    @Override public synchronized int getParagraphIndex(int position) {
        cache.checkOpen();
        if (position < 0 || position > contentLength) throw new IndexOutOfBoundsException();
        int low = 0, high = starts.length - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (starts[middle] <= position) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    @Override public synchronized String get(int start, int end) {
        cache.checkOpen();
        Objects.checkFromToIndex(start, end, contentLength);
        StringBuilder result = new StringBuilder(end - start);
        int line = getParagraphIndex(start);
        int position = start;
        while (position < end) {
            Entry entry = entries.get(line);
            int offset = position - starts[line];
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
        cache.checkOpen();
        if (index < 0 || index > contentLength) throw new IndexOutOfBoundsException();
        if (text == null) throw new IllegalArgumentException("text cannot be null");
        text = CodeInputControl.filterInput(text, false, false);
        if (text.isEmpty()) return;
        Math.addExact(contentLength, text.length());
        int line = getParagraphIndex(index);
        Entry old = entries.get(line);
        String original = read(old);
        int offset = index - starts[line];
        String replacement = original.substring(0, offset) + text + original.substring(offset);
        List<Entry> added = new ArrayList<>();
        int from = 0;
        for (int i = 0; i < replacement.length(); i++) {
            if (replacement.charAt(i) == '\n') {
                added.add(store(replacement.subSequence(from, i)));
                from = i + 1;
            }
        }
        added.add(store(replacement.subSequence(from, replacement.length())));
        markContentModified();
        entries.set(line, added.getFirst());
        entries.addAll(line + 1, added.subList(1, added.size()));
        reindex();
        publishContentChange(index, 0, text.length());
        fireChange(line, line + added.size(), List.of(old));
        if (notifyListeners) fireValueChangedEvent();
    }

    @Override public synchronized void delete(int start, int end, boolean notifyListeners) {
        cache.checkOpen();
        if (start > end) throw new IllegalArgumentException();
        Objects.checkFromToIndex(start, end, contentLength);
        if (start == end) return;
        int first = getParagraphIndex(start), last = getParagraphIndex(end);
        List<Entry> removed = new ArrayList<>(entries.subList(first, last + 1));
        String leading = read(removed.getFirst()), trailing = read(removed.getLast());
        Entry merged = store(leading.substring(0, start - starts[first])
                + trailing.substring(end - starts[last]));
        markContentModified();
        entries.subList(first, last + 1).clear();
        entries.add(first, merged);
        reindex();
        publishContentChange(start, end - start, 0);
        fireChange(first, first + 1, removed);
        if (notifyListeners) fireValueChangedEvent();
    }

    private void fireChange(int from, int to, List<Entry> removed) {
        // Lazy immutable snapshots: deleting many lines doesn't decode them all.
        List<CharSequence> old = new AbstractList<>() {
            @Override public CharSequence get(int index) { return read(removed.get(index)); }
            @Override public int size() { return removed.size(); }
        };
        ListListenerHelper.fireValueChangedEvent(paragraphList.getListenerHelper(),
                new ParagraphListChange(paragraphList, from, to, old));
    }

    @Override public synchronized int length() { cache.checkOpen(); return contentLength; }
    @Override public synchronized String get() { return get(0, length()); }
    @Override public String getValue() { return get(); }
    @Override public synchronized ObservableList<CharSequence> getParagraphList() {
        cache.checkOpen();
        return paragraphList;
    }
    @Override protected void checkContentOpen() { cache.checkOpen(); }
    @Override public synchronized void close() {
        if (!closed) {
            closed = true;
            markContentModified();
            cache.close();
        }
    }
    private boolean closed;
}
