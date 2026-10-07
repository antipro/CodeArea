package com.bitifyware.control.skin;

import com.bitifyware.control.CodeArea;
import com.bitifyware.control.ParagraphIndex;

import java.util.HashMap;
import java.util.Map;

/** Geometry metadata only; no text or JavaFX nodes for offscreen paragraphs. */
final class ParagraphViewport {
    private ParagraphIndex rows = new ParagraphIndex(false, true);
    private final Map<Integer, Double> before = new HashMap<>();
    private double tail;

    /**
     * @param keepMeasured when the paragraphs themselves are unchanged, retain
     *     the measured wrapped heights as the estimate for the new metrics.
     *     Discarding them collapses the document to one line per paragraph,
     *     which then re-grows while scrolling and repeatedly shifts the anchor.
     */
    void reset(CodeArea area, double lineHeight, boolean keepMeasured) {
        int count = area.getParagraphs().size();
        if (keepMeasured && rows.size() == count) {
            // Width/font invalidation keeps previous estimates until the visible
            // paragraphs are measured. Their text indices are already current.
            updateEmptyLines(area, lineHeight);
            return;
        }
        rows = new ParagraphIndex(false, true);
        before.clear();
        for (int i = 0; i < count; i++) {
            rows.append(0, Math.addExact(area.getParagraphLength(i), 1), lineHeight);
        }
        updateEmptyLines(area, lineHeight);
    }

    /** Updates only the changed rows; all unaffected measured heights survive. */
    boolean change(CodeArea area, int from, int added, int removed, double lineHeight) {
        if (size() != area.getParagraphs().size() - added + removed) return false;
        boolean replacingDocument = from == 0 && removed == size();
        ParagraphIndex replacement = new ParagraphIndex(false, true);
        for (int i = 0; i < added; i++) {
            double height = !replacingDocument && i < removed
                    ? rows.paragraphHeight(from + i) - before(from + i) : lineHeight;
            replacement.append(0, Math.addExact(area.getParagraphLength(from + i), 1), height);
        }
        // Phantom rows refer to absolute paragraph indices, not shifted text.
        // Strip their old weights, then reapply the sparse list at its indices.
        stripEmptyLines();
        rows.splice(from, removed, replacement);
        updateEmptyLines(area, lineHeight);
        return true;
    }

    private void stripEmptyLines() {
        for (Map.Entry<Integer, Double> empty : before.entrySet()) {
            int index = empty.getKey();
            rows.setHeight(index, rows.paragraphHeight(index) - empty.getValue());
        }
        before.clear();
    }

    private void updateEmptyLines(CodeArea area, double lineHeight) {
        stripEmptyLines();
        tail = 0;
        for (CodeArea.EmptyLine empty : area.getEmptyLines()) {
            int index = empty.getParagraphIndex();
            if (index >= 0 && index < size()) before.merge(index, lineHeight, Double::sum);
            else if (index == size()) tail += lineHeight;
        }
        for (Map.Entry<Integer, Double> empty : before.entrySet()) {
            int index = empty.getKey();
            rows.setHeight(index, rows.paragraphHeight(index) + empty.getValue());
        }
    }

    int size() { return rows.size(); }
    int start(int paragraph) { return Math.toIntExact(rows.start(paragraph)); }
    double before(int paragraph) { return before.getOrDefault(paragraph, 0.0); }
    double y(int paragraph) { return rows.y(paragraph); }
    double height() { return y(size()) + tail; }

    void measure(int paragraph, double height) {
        rows.setHeight(paragraph, height + before(paragraph));
    }

    int atY(double y) {
        return rows.atY(y);
    }

    int atPosition(int position) {
        return rows.atPosition(position);
    }
}
