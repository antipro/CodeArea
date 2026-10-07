package com.bitifyware.control;

import javafx.scene.control.IndexRange;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Incremental, thread-confined search. Holds one text block and one match, not
 * all results. Regex uses a lazy CharSequence with full-document context, not
 * independently matched chunks. Backward regex scanning may scan the whole range.
 */
public final class SearchCursor implements AutoCloseable {
    private final TextCursor text;
    private final SearchQuery query;
    private final int from, to;
    private final Pattern pattern;
    private final char[] needle, reversed;
    private final int[] prefix, reversePrefix;
    private int position;
    private IndexRange current;
    private boolean forwardExhausted, backwardExhausted;

    SearchCursor(TextCursor text, SearchQuery query, int from, int to, int position) {
        this.text = text;
        this.query = Objects.requireNonNull(query);
        Objects.checkFromToIndex(from, to, text.length());
        this.from = from;
        this.to = to;
        if (query.regex()) {
            pattern = Pattern.compile(query.text(), query.caseSensitive() ? 0
                    : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            needle = reversed = null;
            prefix = reversePrefix = null;
        } else {
            pattern = null;
            needle = query.text().toCharArray();
            reversed = new char[needle.length];
            for (int i = 0; i < needle.length; i++) needle[i] = fold(needle[i]);
            for (int i = 0; i < needle.length; i++) reversed[i] = needle[needle.length - i - 1];
            prefix = prefix(needle);
            reversePrefix = prefix(reversed);
        }
        seek(position);
    }

    public long version() { return text.version(); }
    public void seek(int position) {
        text.checkValid();
        if (position < from || position > to) throw new IndexOutOfBoundsException();
        this.position = position;
        current = null;
        forwardExhausted = backwardExhausted = false;
    }

    /** Changes of direction skip the current match; wrapping happens at most once per call. */
    public Optional<IndexRange> next() {
        text.checkValid();
        long offset = current == null ? position : current.getEnd();
        if (current != null && current.getLength() == 0) offset++;
        IndexRange result = forwardExhausted || offset > to ? null : forward((int) offset);
        if (result == null && query.wrap()) result = forward(from);
        text.checkValid();
        backwardExhausted = false;
        if (result != null) { current = result; forwardExhausted = false; }
        else { current = null; position = to; forwardExhausted = true; }
        return Optional.ofNullable(result);
    }

    public Optional<IndexRange> previous() {
        text.checkValid();
        int limit = current == null ? position : current.getStart();
        boolean excludeZero = current != null && current.getLength() == 0;
        IndexRange result = backwardExhausted ? null : backward(limit, excludeZero);
        if (result == null && query.wrap()) result = backward(to, false);
        text.checkValid();
        forwardExhausted = false;
        if (result != null) { current = result; backwardExhausted = false; }
        else { current = null; position = from; backwardExhausted = true; }
        return Optional.ofNullable(result);
    }

    public SearchCount countAsync() {
        text.checkValid();
        return new SearchCount(new SearchCursor(text.fork(), query.withWrap(false), from, to, from));
    }

    /** Allows an application to supply its own worker executor, without touching JavaFX properties. */
    public SearchCount countAsync(Executor executor) {
        Objects.requireNonNull(executor);
        text.checkValid();
        return new SearchCount(new SearchCursor(text.fork(), query.withWrap(false), from, to, from),
                executor);
    }

    private IndexRange forward(int offset) {
        if (pattern != null) {
            Matcher matcher = matcher(offset);
            while (matcher.find()) {
                if (accept(matcher.start(), matcher.end())) return new IndexRange(matcher.start(), matcher.end());
            }
            return null;
        }
        int matched = 0;
        for (int i = offset; i < to; i++) {
            char c = fold(text.scanCharAt(i));
            while (matched > 0 && needle[matched] != c) matched = prefix[matched - 1];
            if (needle[matched] == c) matched++;
            if (matched == needle.length) {
                int start = i - needle.length + 1;
                if (accept(start, i + 1)) return new IndexRange(start, i + 1);
                matched = prefix[matched - 1];
            }
        }
        return null;
    }

    private IndexRange backward(int limit, boolean excludeZero) {
        if (pattern != null) {
            Matcher matcher = matcher(from);
            IndexRange result = null;
            while (matcher.find() && matcher.start() <= limit) {
                if (matcher.end() <= limit && (!excludeZero || matcher.start() != limit)
                        && accept(matcher.start(), matcher.end())) {
                    result = new IndexRange(matcher.start(), matcher.end());
                }
            }
            return result;
        }
        int matched = 0;
        for (int i = limit - 1; i >= from; i--) {
            char c = fold(text.scanCharAt(i));
            while (matched > 0 && reversed[matched] != c) matched = reversePrefix[matched - 1];
            if (reversed[matched] == c) matched++;
            if (matched == reversed.length) {
                int end = i + reversed.length;
                if (accept(i, end)) return new IndexRange(i, end);
                matched = reversePrefix[matched - 1];
            }
        }
        return null;
    }

    private Matcher matcher(int offset) {
        return pattern.matcher(text.asCharSequence()).region(offset, to)
                .useTransparentBounds(true).useAnchoringBounds(false);
    }

    private boolean accept(int start, int end) {
        return !query.wholeWord() || (!wordBefore(start) && !wordAfter(end));
    }

    private boolean wordBefore(int position) {
        if (position == 0) return false;
        char c = text.charAt(position - 1);
        int point = c;
        if (Character.isLowSurrogate(c) && position > 1) {
            char high = text.charAt(position - 2);
            if (Character.isHighSurrogate(high)) point = Character.toCodePoint(high, c);
        }
        return word(point);
    }

    private boolean wordAfter(int position) {
        if (position == text.length()) return false;
        char c = text.charAt(position);
        int point = c;
        if (Character.isHighSurrogate(c) && position + 1 < text.length()) {
            char low = text.charAt(position + 1);
            if (Character.isLowSurrogate(low)) point = Character.toCodePoint(c, low);
        }
        return word(point);
    }

    private static boolean word(int point) {
        int type = Character.getType(point);
        return Character.isLetterOrDigit(point) || point == '_'
                || type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || type == Character.CONNECTOR_PUNCTUATION;
    }

    private char fold(char c) {
        return query.caseSensitive() ? c : Character.toLowerCase(Character.toUpperCase(c));
    }

    private static int[] prefix(char[] needle) {
        int[] result = new int[needle.length];
        for (int i = 1, n = 0; i < needle.length; i++) {
            while (n > 0 && needle[i] != needle[n]) n = result[n - 1];
            if (needle[i] == needle[n]) n++;
            result[i] = n;
        }
        return result;
    }

    TextSource source() { return text.source; }
    @Override public void close() { text.close(); }
}
