package com.bitifyware.control;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class ParagraphIndexTest {
    private record Row(long offset, int span, double height) { }

    @Test public void blockSplitsSplicesAndWeightsMatchFlatSequence() {
        Random random = new Random(71831);
        List<Row> expected = new ArrayList<>();
        ParagraphIndex index = new ParagraphIndex(true, true);
        for (int i = 0; i < 3000; i++) {
            Row row = new Row((1L << 54) + i, 1 + random.nextInt(120), 1 + random.nextInt(80));
            expected.add(row);
            index.append(row.offset, row.span, row.height);
        }
        for (int step = 0; step < 600; step++) {
            int from = random.nextInt(expected.size() + 1);
            int removed = random.nextInt(Math.min(700, expected.size() - from) + 1);
            int added = random.nextInt(500);
            List<Row> rows = new ArrayList<>();
            ParagraphIndex replacement = new ParagraphIndex(true, true);
            for (int i = 0; i < added; i++) {
                Row row = new Row((1L << 54) + step * 1000L + i,
                        1 + random.nextInt(120), 1 + random.nextInt(80));
                rows.add(row);
                replacement.append(row.offset, row.span, row.height);
            }
            List<Row> oldRows = new ArrayList<>(expected.subList(from, from + removed));
            ParagraphIndex old = index.splice(from, removed, replacement);
            assertEquals(0, replacement.size());
            verify(old, oldRows);
            expected.subList(from, from + removed).clear();
            expected.addAll(from, rows);
            if (!expected.isEmpty()) {
                int changed = random.nextInt(expected.size());
                Row row = expected.get(changed);
                expected.set(changed, new Row(row.offset, row.span, 13.5));
                index.setHeight(changed, 13.5);
            }
            verify(index, expected);
        }
    }

    private void verify(ParagraphIndex index, List<Row> rows) {
        assertEquals(rows.size(), index.size());
        assertEquals(0, index.atY(-1));
        assertEquals(0, index.atPosition(-1));
        long offset = 0;
        double y = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            assertEquals(row.offset, index.offset(i));
            assertEquals(row.span, index.span(i));
            assertEquals(row.height, index.paragraphHeight(i), 0);
            assertEquals(offset, index.start(i));
            assertEquals(y, index.y(i), 0);
            assertEquals(i, index.atPosition(Math.toIntExact(offset)));
            assertEquals(i, index.atPosition(Math.toIntExact(offset + row.span - 1)));
            assertEquals(i, index.atY(y));
            assertEquals(i, index.atY(y + row.height - 0.25));
            offset += row.span;
            y += row.height;
        }
        assertEquals(offset, index.characters());
        assertEquals(offset, index.start(rows.size()));
        assertEquals(y, index.height(), 0);
        assertEquals(y, index.y(rows.size()), 0);
    }
}
