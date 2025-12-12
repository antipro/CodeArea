package com.bitifyware.control;

import com.bitifyware.control.CodeArea.CodeAreaContent;
import com.bitifyware.control.CodeArea.ParagraphList;
import com.bitifyware.control.CodeArea.ParagraphListChange;
import com.sun.javafx.collections.ListListenerHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javafx.collections.ObservableList;

// Text area content model
final class InMemoryContent extends CodeAreaContent {

    private static final int DEFAULT_PARAGRAPH_CAPACITY = 32;

    private final ParagraphList paragraphList = new ParagraphList();

    private int contentLength = 0;

    InMemoryContent() {
        paragraphs = new ArrayList<>();
        paragraphs.add(new StringBuilder(DEFAULT_PARAGRAPH_CAPACITY));
        paragraphList.setContent(this);
    }

    @Override
    public String get(int start, int end) {
        int length = end - start;
        StringBuilder textBuilder = new StringBuilder(length);

        int paragraphCount = paragraphs.size();

        int paragraphIndex = 0;
        int offset = start;

        while (paragraphIndex < paragraphCount) {
            StringBuilder paragraph = paragraphs.get(paragraphIndex);
            int count = paragraph.length() + 1;

            if (offset < count) {
                break;
            }

            offset -= count;
            paragraphIndex++;
        }

        // Read characters until end is reached, appending to text builder
        // and moving to next paragraph as needed
        StringBuilder paragraph = paragraphs.get(paragraphIndex);

        int i = 0;
        while (i < length) {
            if (offset == paragraph.length()
                && i < contentLength) {
                textBuilder.append('\n');
                paragraph = paragraphs.get(++paragraphIndex);
                offset = 0;
            } else {
                textBuilder.append(paragraph.charAt(offset++));
            }

            i++;
        }

        return textBuilder.toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void insert(int index, String text, boolean notifyListeners) {
        if (index < 0
            || index > contentLength) {
            throw new IndexOutOfBoundsException();
        }

        if (text == null) {
            throw new IllegalArgumentException();
        }
        text = CodeInputControl.filterInput(text, false, false);
        int length = text.length();
        if (length > 0) {
            // Split the text into lines
            ArrayList<StringBuilder> lines = new ArrayList<>();

            StringBuilder line = new StringBuilder(DEFAULT_PARAGRAPH_CAPACITY);
            for (int i = 0; i < length; i++) {
                char c = text.charAt(i);

                if (c == '\n') {
                    //                        line.append(c);
                    lines.add(line);
                    line = new StringBuilder(DEFAULT_PARAGRAPH_CAPACITY);
                } else {
                    line.append(c);
                }
            }

            lines.add(line);

            // Merge the text into the existing content
            // Merge the text into the existing content
            int paragraphIndex = paragraphs.size();
            int offset = contentLength + 1;

            StringBuilder paragraph = null;

            do {
                paragraph = paragraphs.get(--paragraphIndex);
                offset -= paragraph.length() + 1;
            } while (index < offset);

            int start = index - offset;

            int n = lines.size();
            if (n == 1) {
                // The text contains only a single line; insert it into the
                // intersecting paragraph
                paragraph.insert(start, line);
                fireParagraphListChangeEvent(paragraphIndex, paragraphIndex + 1,
                    Collections.singletonList((CharSequence) paragraph));
            } else {
                // The text contains multiple line; split the intersecting
                // paragraph
                int end = paragraph.length();
                CharSequence trailingText = paragraph.subSequence(start, end);
                paragraph.delete(start, end);

                // Append the first line to the intersecting paragraph and
                // append the trailing text to the last line
                StringBuilder first = lines.getFirst();
                paragraph.insert(start, first);
                line.append(trailingText);
                fireParagraphListChangeEvent(paragraphIndex, paragraphIndex + 1,
                    Collections.singletonList((CharSequence) paragraph));

                // Insert the remaining lines into the paragraph list
                paragraphs.addAll(paragraphIndex + 1, lines.subList(1, n));
                fireParagraphListChangeEvent(paragraphIndex + 1, paragraphIndex + n,
                    Collections.EMPTY_LIST);
            }

            // Update content length
            contentLength += length;
            if (notifyListeners) {
                fireValueChangedEvent();
            }
        }
    }

    @Override
    public void delete(int start, int end, boolean notifyListeners) {
        if (start > end) {
            throw new IllegalArgumentException();
        }

        if (start < 0
            || end > contentLength) {
            throw new IndexOutOfBoundsException();
        }

        int length = end - start;

        if (length > 0) {
            // Identify the trailing paragraph index
            int paragraphIndex = paragraphs.size();
            int offset = contentLength + 1;

            StringBuilder paragraph = null;

            do {
                paragraph = paragraphs.get(--paragraphIndex);
                offset -= paragraph.length() + 1;
            } while (end < offset);

            int trailingParagraphIndex = paragraphIndex;
            int trailingOffset = offset;
            StringBuilder trailingParagraph = paragraph;

            // Identify the leading paragraph index
            paragraphIndex++;
            offset += paragraph.length() + 1;

            do {
                paragraph = paragraphs.get(--paragraphIndex);
                offset -= paragraph.length() + 1;
            } while (start < offset);

            int leadingParagraphIndex = paragraphIndex;
            int leadingOffset = offset;
            StringBuilder leadingParagraph = paragraph;

            // Remove the text
            if (leadingParagraphIndex == trailingParagraphIndex) {
                // The removal affects only a single paragraph
                leadingParagraph.delete(start - leadingOffset,
                    end - leadingOffset);

                fireParagraphListChangeEvent(leadingParagraphIndex, leadingParagraphIndex + 1,
                    Collections.singletonList((CharSequence) leadingParagraph));
            } else {
                // The removal spans paragraphs; remove any intervening paragraphs and
                // merge the leading and trailing segments
                CharSequence leadingSegment = leadingParagraph.subSequence(0,
                    start - leadingOffset);
                int trailingSegmentLength = (start + length) - trailingOffset;

                trailingParagraph.delete(0, trailingSegmentLength);
                fireParagraphListChangeEvent(trailingParagraphIndex, trailingParagraphIndex + 1,
                    Collections.singletonList((CharSequence) trailingParagraph));

                if (trailingParagraphIndex - leadingParagraphIndex > 0) {
                    List<CharSequence> removed = new ArrayList<>(paragraphs.subList(leadingParagraphIndex,
                        trailingParagraphIndex));
                    paragraphs.subList(leadingParagraphIndex,
                        trailingParagraphIndex).clear();
                    fireParagraphListChangeEvent(leadingParagraphIndex, leadingParagraphIndex,
                        removed);
                }

                // Trailing paragraph is now at the former leading paragraph's index
                trailingParagraph.insert(0, leadingSegment);
                fireParagraphListChangeEvent(leadingParagraphIndex, leadingParagraphIndex + 1,
                    Collections.singletonList((CharSequence) leadingParagraph));
            }

            // Update content length
            contentLength -= length;
            if (notifyListeners) {
                fireValueChangedEvent();
            }
        }
    }

    @Override
    public int length() {
        return contentLength;
    }

    @Override
    public String get() {
        return get(0, length());
    }

    @Override
    public String getValue() {
        return get();
    }

    private void fireParagraphListChangeEvent(int from, int to, List<CharSequence> removed) {
        ParagraphListChange change = new ParagraphListChange(paragraphList, from, to, removed);
        ListListenerHelper.fireValueChangedEvent(paragraphList.getListenerHelper(), change);
    }

    public ObservableList<CharSequence> getParagraphList() {
        return paragraphList;
    }
}
