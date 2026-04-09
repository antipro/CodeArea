package com.bitifyware.control;

import javafx.scene.paint.Color;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for CodeArea.LineBackground inner class.
 * These tests do not require a GUI environment.
 */
public class LineBackgroundTest {

    @Test
    public void testCreation() {
        CodeArea.LineBackground lb = new CodeArea.LineBackground(2, Color.YELLOW);
        assertEquals(2, lb.getParagraphIndex());
        assertEquals(Color.YELLOW, lb.getColor());
    }

    @Test
    public void testIndexZero() {
        CodeArea.LineBackground lb = new CodeArea.LineBackground(0, Color.RED);
        assertEquals(0, lb.getParagraphIndex());
        assertEquals(Color.RED, lb.getColor());
    }

    @Test
    public void testLargeIndex() {
        CodeArea.LineBackground lb = new CodeArea.LineBackground(9999, Color.GREEN);
        assertEquals(9999, lb.getParagraphIndex());
    }

    @Test
    public void testCustomColor() {
        Color custom = Color.rgb(200, 50, 50, 0.4);
        CodeArea.LineBackground lb = new CodeArea.LineBackground(5, custom);
        assertEquals(custom, lb.getColor());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeIndex() {
        new CodeArea.LineBackground(-1, Color.BLUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullColor() {
        new CodeArea.LineBackground(0, null);
    }
}
