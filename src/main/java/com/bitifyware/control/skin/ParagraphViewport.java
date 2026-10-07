package com.bitifyware.control.skin;

import com.bitifyware.control.CodeArea;

/** Geometry metadata only; no text or JavaFX nodes for offscreen paragraphs. */
final class ParagraphViewport {
    private int[] starts = new int[0];
    private double[] heights = new double[0];
    private double[] tree = new double[1];
    private double[] before = new double[0];
    private double tail;

    void reset(CodeArea area, double lineHeight) {
        int count = area.getParagraphs().size();
        starts = new int[count];
        heights = new double[count];
        before = new double[count];
        tree = new double[count + 1];
        tail = 0;
        int offset = 0;
        for (int i = 0; i < count; i++) {
            starts[i] = offset;
            offset += area.getParagraphLength(i);
            if (i + 1 < count) offset++;
            heights[i] = lineHeight;
        }
        for (CodeArea.EmptyLine empty : area.getEmptyLines()) {
            int index = empty.getParagraphIndex();
            if (index < count) before[index] += lineHeight;
            else if (index == count) tail += lineHeight;
        }
        // Linear Fenwick construction.
        for (int i = 1; i <= count; i++) {
            tree[i] += heights[i - 1] + before[i - 1];
            int parent = i + (i & -i);
            if (parent <= count) tree[parent] += tree[i];
        }
    }

    int size() { return starts.length; }
    int start(int paragraph) { return starts[paragraph]; }
    double before(int paragraph) { return before[paragraph]; }
    double y(int paragraph) {
        double sum = 0;
        for (int i = paragraph; i > 0; i -= i & -i) sum += tree[i];
        return sum;
    }
    double height() { return y(size()) + tail; }

    void measure(int paragraph, double height) {
        double delta = height - heights[paragraph];
        heights[paragraph] = height;
        for (int i = paragraph + 1; i < tree.length; i += i & -i) tree[i] += delta;
    }

    int atY(double y) {
        int index = 0;
        for (int bit = Integer.highestOneBit(size()); bit != 0; bit >>= 1) {
            int next = index + bit;
            if (next < tree.length && tree[next] <= y) {
                y -= tree[next];
                index = next;
            }
        }
        return Math.min(index, size() - 1);
    }

    int atPosition(int position) {
        int low = 0, high = size() - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (starts[mid] <= position) low = mid;
            else high = mid - 1;
        }
        return low;
    }
}
