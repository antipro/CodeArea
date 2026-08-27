package com.bitifyware.control;

import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class InDiskContentTest {

    @Test
    public void preservesParagraphsAndCharacterRanges() {
        String text = "first\n\nthird\n";
        try (InDiskContent content = new InDiskContent(text)) {
            assertEquals(text, content.get());
            assertEquals(text.length(), content.length());
            assertEquals(List.of("first", "", "third", ""),
                    List.copyOf(content.getParagraphList()));
            assertEquals("st\n\nth", content.get(3, 9));
        }
    }

    @Test
    public void handlesEditsAtParagraphBoundariesAndUnicode() {
        try (InDiskContent content = new InDiskContent("alpha\nbeta")) {
            content.insert(6, "\uD83D\uDE03\n", true);
            assertEquals("alpha\n\uD83D\uDE03\nbeta", content.get());
            assertEquals(List.of("alpha", "\uD83D\uDE03", "beta"),
                    List.copyOf(content.getParagraphList()));

            content.delete(5, 9, true);
            assertEquals("alphabeta", content.get());
            assertEquals(List.of("alphabeta"), List.copyOf(content.getParagraphList()));
        }
    }

    @Test
    public void matchesStringBuilderAcrossRandomEdits() {
        Random random = new Random(0xD15C);
        StringBuilder expected = new StringBuilder();

        try (InDiskContent content = new InDiskContent()) {
            for (int operation = 0; operation < 500; operation++) {
                if (expected.isEmpty() || random.nextBoolean()) {
                    int index = random.nextInt(expected.length() + 1);
                    String inserted = randomText(random);
                    expected.insert(index, inserted);
                    content.insert(index, inserted, operation % 2 == 0);
                } else {
                    int start = random.nextInt(expected.length());
                    int end = start + random.nextInt(expected.length() - start + 1);
                    expected.delete(start, end);
                    content.delete(start, end, operation % 2 == 0);
                }

                assertEquals("operation " + operation, expected.toString(), content.get());
                assertEquals("operation " + operation, expected.length(), content.length());
                assertEquals("operation " + operation,
                        List.of(expected.toString().split("\\n", -1)),
                        List.copyOf(content.getParagraphList()));
            }
        }
    }

    @Test
    public void movesContentLargerThanTheIoBuffer() {
        String original = "0123456789abcdef\n".repeat(10_000);
        String inserted = "large insertion\n".repeat(5_000);
        StringBuilder expected = new StringBuilder(original);
        int insertionPoint = original.length() / 3;
        expected.insert(insertionPoint, inserted);

        try (InDiskContent content = new InDiskContent(original)) {
            content.insert(insertionPoint, inserted, false);
            assertEquals(expected.toString(), content.get());

            int deleteStart = 32_001;
            int deleteEnd = expected.length() - 27_003;
            expected.delete(deleteStart, deleteEnd);
            content.delete(deleteStart, deleteEnd, false);
            assertEquals(expected.toString(), content.get());
        }
    }

    @Test
    public void rejectsUseAfterClose() {
        InDiskContent content = new InDiskContent("text");
        content.close();
        content.close();

        assertThrows(IllegalStateException.class, content::get);
        assertThrows(IllegalStateException.class, () -> content.insert(0, "x", false));
    }

    private static String randomText(Random random) {
        int length = 1 + random.nextInt(12);
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int value = random.nextInt(8);
            text.append(value == 0 ? '\n' : (char) ('a' + value));
        }
        return text.toString();
    }
}
