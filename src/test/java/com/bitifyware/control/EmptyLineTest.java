package com.bitifyware.control;

import javafx.scene.paint.Color;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for CodeArea.EmptyLine inner class.
 * These tests do not require a GUI environment.
 */
public class EmptyLineTest {

    @Test
    public void testEmptyLineCreation() {
        CodeArea.EmptyLine emptyLine = new CodeArea.EmptyLine(3, Color.GREEN);
        assertEquals(3, emptyLine.getParagraphIndex());
        assertEquals(Color.GREEN, emptyLine.getColor());
    }

    @Test
    public void testEmptyLineAtIndexZero() {
        CodeArea.EmptyLine emptyLine = new CodeArea.EmptyLine(0, Color.RED);
        assertEquals(0, emptyLine.getParagraphIndex());
        assertEquals(Color.RED, emptyLine.getColor());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyLineNegativeIndex() {
        new CodeArea.EmptyLine(-1, Color.GREEN);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyLineNullColor() {
        new CodeArea.EmptyLine(0, null);
    }

    @Test
    public void testEmptyLineWithCustomColor() {
        Color customColor = Color.rgb(50, 100, 50, 0.3);
        CodeArea.EmptyLine emptyLine = new CodeArea.EmptyLine(5, customColor);
        assertEquals(5, emptyLine.getParagraphIndex());
        assertEquals(customColor, emptyLine.getColor());
    }
}
