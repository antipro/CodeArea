package com.bitifyware.control;

import java.text.BreakIterator;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Set;
import java.util.Objects;

public class CodeBreakIterator extends BreakIterator {

    public static final Set<Character> DELIMITERS = Set.of(' ', '.', ',', ';', ':', '!', '?',
            '(', ')', '[', ']', '{', '}', '<', '>',
            '/', '\\', '|', '\'', '"', '`', '~', '\n', '\t',
            '@', '#', '%', '^', '&', '*', '-', '+', '=', '—');
    private CharSequence text;
    private CharacterIterator source;
    private int current;

    @Override
    public int first() {
        current = 0;
        return current;
    }

    @Override
    public int last() {
        current = text.length();
        return current;
    }

    @Override
    public int next(int n) {
        int newPos = current;
        for (int i = 0; i < n; i++) {
            newPos = next();
            if (newPos == DONE) {
                return DONE;
            }
        }
        return newPos;
    }

    @Override
    public int next() {
        if (current == text.length()) {
            return DONE;
        }

        if (isDelimiter(text.charAt(current))) {
//            current++;
            return current;
        }

        current++;
        while (current < text.length() && !isBoundary(current)) {
            current++;
        }

        return current;
    }

    public static boolean isDelimiter(char c) {
        return DELIMITERS.contains(c);
    }

    @Override
    public int previous() {
        if (current == 0) {
            return DONE;
        }

        current--;
        if (isDelimiter(text.charAt(current))) {
            return current;
        }

        while (current > 0 && !isBoundary(current)) {
            current--;
        }

        return current;
    }

    @Override
    public int following(int offset) {
        if (offset < 0 || offset >= text.length()) {
            return DONE;
        }

        current = offset;
        return next();
    }

    @Override
    public int preceding(int offset) {
        if (offset <= 0 || offset > text.length()) {
            return DONE;
        }

        current = offset;
        return previous();
    }

    @Override
    public boolean isBoundary(int offset) {
        if (offset < 0 || offset > text.length()) {
            return false;
        }

        if (offset == 0 || offset == text.length()) {
            return true;
        }

        return isDelimiter(text.charAt(offset - 1)) ||
                isDelimiter(text.charAt(offset));
    }

    @Override
    public int current() {
        return current;
    }

    @Override
    public CharacterIterator getText() {
        return (CharacterIterator) source.clone();
    }

    @Override
    public void setText(CharacterIterator newText) {
        source = (CharacterIterator) Objects.requireNonNull(newText).clone();
        text = new IteratorSequence((CharacterIterator) source.clone(),
                source.getBeginIndex(), source.getEndIndex());
        current = 0;
    }

    @Override
    public void setText(String newText) {
        text = Objects.requireNonNull(newText);
        source = new StringCharacterIterator(newText);
        current = 0;
    }

    private record IteratorSequence(CharacterIterator iterator, int start, int end) implements CharSequence {
        @Override public int length() { return end - start; }
        @Override public char charAt(int index) {
            return iterator.setIndex(start + Objects.checkIndex(index, length()));
        }
        @Override public CharSequence subSequence(int from, int to) {
            Objects.checkFromToIndex(from, to, length());
            return new IteratorSequence((CharacterIterator) iterator.clone(), start + from, start + to);
        }
        @Override public String toString() {
            StringBuilder result = new StringBuilder(length());
            for (int i = 0; i < length(); i++) result.append(charAt(i));
            return result.toString();
        }
    }

    public static void main(String[] args) {
        String text = "--hello.world this.is.a.test.";

        // Create a custom BreakIterator
        BreakIterator customIterator = new CodeBreakIterator();

        // Set the text to be analyzed
        customIterator.setText(text);

        // Iterate over the words
        int start = customIterator.first();
        for (int end = customIterator.next(); end != BreakIterator.DONE; start = end, end = customIterator.next()) {
            String word = text.substring(start, end);
            System.out.println(word);
        }
    }
}
