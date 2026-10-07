package com.bitifyware.control;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A block-based weighted sequence. Edits touch O(log(blocks)) metadata plus at
 * most two small blocks, not a document-sized array. Paragraph spans include
 * their trailing newline (including a virtual newline on the last paragraph).
 * This is internal editor metadata; callers provide their own synchronization.
 */
public final class ParagraphIndex {
    private static final int BLOCK_SIZE = 256;
    private final boolean offsets;
    private final boolean geometry;
    private static final AtomicInteger SEED = new AtomicInteger(0x6d2b79f5);
    private Node root;

    public ParagraphIndex(boolean offsets, boolean geometry) {
        this.offsets = offsets;
        this.geometry = geometry;
    }

    private static final class Node {
        Node left, right;
        int priority, count, size;
        long characters, blockCharacters;
        double height, blockHeight;
        final int[] spans = new int[BLOCK_SIZE];
        final long[] offsets;
        final double[] heights;

        Node(int priority, boolean offsets, boolean geometry) {
            this.priority = priority;
            this.offsets = offsets ? new long[BLOCK_SIZE] : null;
            heights = geometry ? new double[BLOCK_SIZE] : null;
        }

        void update() {
            size = size(left) + count + size(right);
            characters = characters(left) + blockCharacters + characters(right);
            height = height(left) + blockHeight + height(right);
        }
    }

    private record Split(Node left, Node right) { }

    private int priority() {
        int value = SEED.getAndAdd(0x9e3779b9);
        value = (value ^ (value >>> 16)) * 0x21f0aaad;
        value = (value ^ (value >>> 15)) * 0x735a2d97;
        return value ^ (value >>> 15);
    }

    private Node node() { return new Node(priority(), offsets, geometry); }
    private static int size(Node node) { return node == null ? 0 : node.size; }
    private static long characters(Node node) { return node == null ? 0 : node.characters; }
    private static double height(Node node) { return node == null ? 0 : node.height; }
    public int size() { return size(root); }
    public long characters() { return characters(root); }
    public double height() { return height(root); }

    public void append(long offset, int span, double height) {
        if (span <= 0 || !Double.isFinite(height) || height < 0) throw new IllegalArgumentException();
        Node last = root;
        if (last != null) while (last.right != null) last = last.right;
        if (last != null && last.count < BLOCK_SIZE) {
            appendLast(root, offset, span, height);
        } else {
            Node added = node();
            add(added, offset, span, height);
            root = merge(root, added);
        }
    }

    private void appendLast(Node node, long offset, int span, double height) {
        if (node.right != null) appendLast(node.right, offset, span, height);
        else add(node, offset, span, height);
        node.update();
    }

    private static void add(Node node, long offset, int span, double height) {
        int index = node.count++;
        node.spans[index] = span;
        if (node.offsets != null) node.offsets[index] = offset;
        if (node.heights != null) node.heights[index] = height;
        node.blockCharacters += span;
        node.blockHeight += node.heights == null ? 0 : height;
        node.update();
    }

    public int span(int paragraph) {
        Objects.checkIndex(paragraph, size());
        Node node = root;
        while (true) {
            int left = size(node.left);
            if (paragraph < left) node = node.left;
            else if (paragraph >= left + node.count) {
                paragraph -= left + node.count;
                node = node.right;
            } else return node.spans[paragraph - left];
        }
    }

    public long offset(int paragraph) {
        Objects.checkIndex(paragraph, size());
        Node node = root;
        while (true) {
            int left = size(node.left);
            if (paragraph < left) node = node.left;
            else if (paragraph >= left + node.count) {
                paragraph -= left + node.count;
                node = node.right;
            } else return node.offsets[paragraph - left];
        }
    }

    public double paragraphHeight(int paragraph) {
        Objects.checkIndex(paragraph, size());
        Node node = root;
        while (true) {
            int left = size(node.left);
            if (paragraph < left) node = node.left;
            else if (paragraph >= left + node.count) {
                paragraph -= left + node.count;
                node = node.right;
            } else return node.heights[paragraph - left];
        }
    }

    public long start(int paragraph) { return (long) prefix(paragraph, false); }
    public double y(int paragraph) { return prefix(paragraph, true); }

    private double prefix(int paragraph, boolean geometry) {
        if (paragraph < 0 || paragraph > size()) throw new IndexOutOfBoundsException();
        double result = 0;
        Node node = root;
        while (node != null) {
            int left = size(node.left);
            if (paragraph < left) node = node.left;
            else {
                result += geometry ? height(node.left) : characters(node.left);
                int count = Math.min(paragraph - left, node.count);
                for (int i = 0; i < count; i++) result += geometry ? node.heights[i] : node.spans[i];
                if (paragraph <= left + node.count) break;
                paragraph -= left + node.count;
                node = node.right;
            }
        }
        return result;
    }

    public int atPosition(int position) { return locate(position, false); }
    public int atY(double y) { return locate(y, true); }

    private int locate(double position, boolean geometry) {
        if (position <= 0) return 0;
        int index = 0;
        Node node = root;
        while (node != null) {
            double left = geometry ? height(node.left) : characters(node.left);
            if (position < left) node = node.left;
            else {
                position -= left;
                index += size(node.left);
                for (int i = 0; i < node.count; i++) {
                    double weight = geometry ? node.heights[i] : node.spans[i];
                    if (position < weight) return index;
                    position -= weight;
                    index++;
                }
                node = node.right;
            }
        }
        return Math.max(0, size() - 1);
    }

    public void setHeight(int paragraph, double height) {
        Objects.checkIndex(paragraph, size());
        if (!Double.isFinite(height) || height < 0) throw new IllegalArgumentException();
        setHeight(root, paragraph, height);
    }

    private static void setHeight(Node node, int paragraph, double height) {
        int left = size(node.left);
        if (paragraph < left) setHeight(node.left, paragraph, height);
        else if (paragraph >= left + node.count) setHeight(node.right, paragraph - left - node.count, height);
        else {
            int local = paragraph - left;
            node.blockHeight += height - node.heights[local];
            node.heights[local] = height;
        }
        node.update();
    }

    /** Consumes replacement; returns detached metadata for lazy change snapshots. */
    public ParagraphIndex splice(int from, int removed, ParagraphIndex replacement) {
        Objects.checkFromIndexSize(from, removed, size());
        if (replacement == this || offsets != replacement.offsets || geometry != replacement.geometry) {
            throw new IllegalArgumentException("Incompatible paragraph index");
        }
        Split leading = split(root, from);
        Split trailing = split(leading.right, removed);
        ParagraphIndex old = new ParagraphIndex(offsets, geometry);
        old.root = trailing.left;
        root = concat(concat(leading.left, replacement.root), trailing.right);
        replacement.root = null;
        return old;
    }

    private static Node merge(Node left, Node right) {
        if (left == null) return right;
        if (right == null) return left;
        if (left.priority < right.priority) {
            left.right = merge(left.right, right);
            left.update();
            return left;
        }
        right.left = merge(left, right.left);
        right.update();
        return right;
    }

    private Split split(Node node, int count) {
        if (node == null) return new Split(null, null);
        int left = size(node.left);
        if (count < left) {
            Split split = split(node.left, count);
            node.left = split.right;
            node.update();
            return new Split(split.left, node);
        }
        if (count > left + node.count) {
            Split split = split(node.right, count - left - node.count);
            node.right = split.left;
            node.update();
            return new Split(node, split.right);
        }
        if (count == left) {
            Node leading = node.left;
            node.left = null;
            node.update();
            return new Split(leading, node);
        }
        if (count == left + node.count) {
            Node trailing = node.right;
            node.right = null;
            node.update();
            return new Split(node, trailing);
        }
        int local = count - left;
        return new Split(merge(node.left, copy(node, 0, local)),
                merge(copy(node, local, node.count), node.right));
    }

    private Node copy(Node source, int from, int to) {
        Node result = node();
        for (int i = from; i < to; i++) {
            add(result, source.offsets == null ? 0 : source.offsets[i], source.spans[i],
                    source.heights == null ? 0 : source.heights[i]);
        }
        return result;
    }

    private Node concat(Node left, Node right) {
        if (left == null || right == null) return merge(left, right);
        Node last = left, first = right;
        while (last.right != null) last = last.right;
        while (first.left != null) first = first.left;
        if (last.count + first.count > BLOCK_SIZE) return merge(left, right);
        Split leading = split(left, size(left) - last.count);
        Split trailing = split(right, first.count);
        Node combined = copy(last, 0, last.count);
        for (int i = 0; i < first.count; i++) {
            add(combined, first.offsets == null ? 0 : first.offsets[i], first.spans[i],
                    first.heights == null ? 0 : first.heights[i]);
        }
        return merge(merge(leading.left, combined), trailing.right);
    }
}
