package com.bitifyware.example;

import java.text.BreakIterator;
import java.util.Locale;

/**
 * @author antipro
 */
public class BreakIteratorExample {

    public static void main(String[] args) {
        String text = "hello.world this.is.a.test";

        // Create a BreakIterator for words
        BreakIterator wordIterator = BreakIterator.getWordInstance(Locale.US);

        // Set the text to be analyzed
        wordIterator.setText(text);

        // Iterate over the words
        int start = wordIterator.first();
        for (int end = wordIterator.next(); end != BreakIterator.DONE; start = end, end = wordIterator.next()) {
            String word = text.substring(start, end);
            if (Character.isLetterOrDigit(word.charAt(0))) {
                System.out.println(word);
            }
        }
    }
}
