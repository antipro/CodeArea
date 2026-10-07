package com.bitifyware.control;

import java.util.Objects;

/** Immutable search options; offsets/results use UTF-16, as CodeArea does. */
public record SearchQuery(String text, boolean regex, boolean caseSensitive,
                          boolean wholeWord, boolean wrap) {
    public SearchQuery {
        Objects.requireNonNull(text);
        if (!regex && text.isEmpty()) throw new IllegalArgumentException("Literal query cannot be empty");
    }
    public static SearchQuery literal(String text) { return new SearchQuery(text, false, true, false, false); }
    public static SearchQuery regex(String text) { return new SearchQuery(text, true, true, false, false); }
    public SearchQuery withWrap(boolean wrap) {
        return new SearchQuery(text, regex, caseSensitive, wholeWord, wrap);
    }
}
