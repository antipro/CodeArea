package com.bitifyware.control;

import javafx.scene.paint.Color;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for CodeArea.IntraLineHighlight inner class.
 * These tests do not require a GUI environment.
 */
public class IntraLineHighlightTest {

    @Test
    public void testCreation() {
        CodeArea.IntraLineHighlight h = new CodeArea.IntraLineHighlight(5, 10, Color.LIGHTGREEN);
        assertEquals(5, h.getStart());
        assertEquals(10, h.getEnd());
        assertEquals(Color.LIGHTGREEN, h.getColor());
    }

    @Test
    public void testStartAtZero() {
        CodeArea.IntraLineHighlight h = new CodeArea.IntraLineHighlight(0, 1, Color.BLUE);
        assertEquals(0, h.getStart());
        assertEquals(1, h.getEnd());
    }

    @Test
    public void testAdjacentChars() {
        CodeArea.IntraLineHighlight h = new CodeArea.IntraLineHighlight(3, 4, Color.RED);
        assertEquals(3, h.getStart());
        assertEquals(4, h.getEnd());
    }

    @Test
    public void testCustomColor() {
        Color custom = Color.rgb(100, 200, 100, 0.5);
        CodeArea.IntraLineHighlight h = new CodeArea.IntraLineHighlight(0, 5, custom);
        assertEquals(custom, h.getColor());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeStart() {
        new CodeArea.IntraLineHighlight(-1, 5, Color.BLUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEndEqualStart() {
        new CodeArea.IntraLineHighlight(5, 5, Color.BLUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEndLessThanStart() {
        new CodeArea.IntraLineHighlight(10, 5, Color.BLUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullColor() {
        new CodeArea.IntraLineHighlight(0, 5, null);
    }
}
