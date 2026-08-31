package com.bitifyware.control;

import com.bitifyware.control.skin.CodeAreaSkin;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Simple JUnit test for CodeArea using TestFX.
 * Note: This test requires a GUI environment to run.
 * Tests will be skipped on headless environments or unsupported platforms.
 */
public class CodeAreaTest extends ApplicationTest {

    private CodeArea codeArea;
    private StackPane root;

    @BeforeClass
    public static void checkPlatform() {
        // Get OS name and headless property
        String osName = System.getProperty("os.name").toLowerCase();
        boolean isHeadless = Boolean.parseBoolean(System.getProperty("java.awt.headless", "true"));
        boolean hasDisplay = System.getenv("DISPLAY") != null || osName.contains("win") || osName.contains("mac");

        // Skip tests on headless environments
        Assume.assumeFalse("Skipping GUI tests in headless environment", isHeadless);

        // Skip if running on Linux without DISPLAY environment variable
        if (osName.contains("linux")) {
            Assume.assumeTrue("Skipping GUI tests on Linux without DISPLAY", hasDisplay);
        }

        // You can add more platform-specific restrictions here:
        // Example: Skip on specific OS
        // Assume.assumeFalse("Skipping tests on Linux", osName.contains("linux"));

        // Example: Only run on Windows
        // Assume.assumeTrue("Tests only run on Windows", osName.contains("win"));

        // Example: Skip on macOS
        // Assume.assumeFalse("Skipping tests on macOS", osName.contains("mac"));

        System.out.println("Running GUI tests on platform: " + osName);
    }

    @Override
    public void start(Stage stage) {
        codeArea = new CodeArea();
        codeArea.setPrefWidth(600);
        codeArea.setPrefHeight(400);

        root = new StackPane(codeArea);
        Scene scene = new Scene(root, 600, 400);

        stage.setScene(scene);
        stage.show();
    }

    @Test
    public void testCodeAreaInitialization() {
        // Verify that the CodeArea is not null
        assertNotNull(codeArea);

        // Verify that the CodeArea is empty initially
        assertEquals("", codeArea.getText());
    }

    @Test
    public void testCodeAreaSetText() {
        // Set text programmatically
        interact(() -> codeArea.setText("Hello, CodeArea!"));

        // Verify the text was set correctly
        assertEquals("Hello, CodeArea!", codeArea.getText());
    }

    @Test
    public void testCodeAreaAppendText() {
        // Append text to the CodeArea
        interact(() -> {
            codeArea.setText("Hello");
            codeArea.appendText(", World!");
        });

        // Verify the text was appended correctly
        assertEquals("Hello, World!", codeArea.getText());
    }

    @Test
    public void testRepeatedBackspaceWithDiskContent() {
        CodeArea diskArea = new CodeArea("Try editing this text.", true);
        interact(() -> {
            root.getChildren().setAll(diskArea);
            diskArea.applyCss();
            diskArea.layout();
        });
        WaitForAsyncUtils.waitForFxEvents();
        interact(() -> diskArea.positionCaret(diskArea.getLength()));
        interact(() -> {
            for (int i = 0; i < 3; i++) {
                diskArea.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.BACK_SPACE,
                        false, false, false, false));
            }
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("Caret should move after each backspace", "Try editing this te".length(), diskArea.getCaretPosition());
        assertEquals("Try editing this te", diskArea.getText());
        assertEquals("Try editing this te", diskArea.getParagraphs().getFirst().toString());
        TextFlow paragraph = (TextFlow) diskArea.lookup(".paragraph-nodes").lookup("TextFlow");
        String renderedText = paragraph.getChildren().stream()
                .map(Text.class::cast)
                .map(Text::getText)
                .collect(java.util.stream.Collectors.joining());
        assertEquals("Try editing this te", renderedText);
    }

    @Test
    public void testSelectAllImeLocationWithLargeDiskContent() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 1_000; i++) {
            text.append("Line ").append(i).append(": large disk content\n");
        }
        CodeArea diskArea = new CodeArea(text.toString(), true);
        interact(() -> {
            root.getChildren().setAll(diskArea);
            diskArea.applyCss();
            diskArea.layout();
            diskArea.selectAll();
        });
        WaitForAsyncUtils.waitForFxEvents();

        Point2D location = diskArea.getInputMethodRequests().getTextLocation(-1);

        assertNotNull(location);
        assertEquals(0, diskArea.getSelection().getStart());
        assertEquals(diskArea.getLength(), diskArea.getSelection().getEnd());
    }

    @Test
    public void testCodeAreaClear() {
        // Set some text first
        interact(() -> codeArea.setText("Some text"));

        // Clear the text
        interact(() -> codeArea.clear());

        // Verify the text was cleared
        assertEquals("", codeArea.getText());
    }

    @Test
    public void testCodeAreaUserInput() {
        // Click on the CodeArea
        clickOn(codeArea);

        // Type some text
        write("TestFX is awesome!");

        // Verify the text was entered
        assertEquals("TestFX is awesome!", codeArea.getText());
    }

    @Test
    public void testCodeAreaEditability() {
        // Verify CodeArea is editable by default
        assertTrue(codeArea.isEditable());

        // Make it non-editable
        interact(() -> codeArea.setEditable(false));

        // Verify it's no longer editable
        assertFalse(codeArea.isEditable());
    }

    @Test
    public void testCodeAreaMultiLine() {
        // Test multi-line text functionality
        String multiLineText = "Line 1\nLine 2\nLine 3";
        interact(() -> codeArea.setText(multiLineText));

        // Verify multi-line text was set correctly
        assertEquals(multiLineText, codeArea.getText());

        // Verify paragraph count
        assertEquals(3, codeArea.getParagraphs().size());
    }

    @Test
    public void testSelectionBackgroundKeepsWrappedLineOffset() throws Exception {
        interact(() -> {
            codeArea.setWrapText(true);
            codeArea.setText("a".repeat(300));
            codeArea.selectRange(150, 280);
            codeArea.getScene().getRoot().applyCss();
            codeArea.getScene().getRoot().layout();
        });
        WaitForAsyncUtils.waitForFxEvents();

        Field field = CodeAreaSkin.class.getDeclaredField("selectionHighlightGroup");
        field.setAccessible(true);
        Group selectionHighlightGroup = (Group) field.get(codeArea.getSkin());

        assertFalse(selectionHighlightGroup.getChildren().isEmpty());
        Path selectionBackground = (Path) selectionHighlightGroup.getChildren().getFirst();
        double selectionTop = ((MoveTo) selectionBackground.getElements().get(0)).getY();
        double selectionBottom = ((LineTo) selectionBackground.getElements().get(2)).getY();
        assertTrue("Wrapped selection background lost its vertical extent: "
                        + selectionBackground.getElements(),
                selectionBottom > selectionTop);
    }

    @Test
    public void testColumnSelectionCreatesOneClampedRangePerLine() {
        interact(() -> {
            codeArea.setText("abcd\nxy\nwxyz");
            codeArea.beginColumnSelection(1);
            codeArea.updateColumnSelection(11);
        });

        assertTrue(codeArea.isColumnSelectionActive());
        assertEquals(List.of(
                new javafx.scene.control.IndexRange(1, 3),
                new javafx.scene.control.IndexRange(6, 7),
                new javafx.scene.control.IndexRange(9, 11)),
                codeArea.getColumnSelectionRanges());
        assertEquals("bc\ny\nxy", codeArea.getColumnSelectedText());
    }

    @Test
    public void testColumnSelectionRendersEachSelectedLine() throws Exception {
        interact(() -> {
            codeArea.setText("abcd\nabcd\nabcd");
            codeArea.beginColumnSelection(1);
            codeArea.updateColumnSelection(13);
            codeArea.getScene().getRoot().applyCss();
            codeArea.getScene().getRoot().layout();
        });
        WaitForAsyncUtils.waitForFxEvents();

        Field field = CodeAreaSkin.class.getDeclaredField("selectionHighlightGroup");
        field.setAccessible(true);
        Group selectionHighlightGroup = (Group) field.get(codeArea.getSkin());

        assertEquals(3, selectionHighlightGroup.getChildren().size());
    }

    @Test
    public void testMouseColumnSelectionUsesVisualXCoordinates() {
        interact(() -> {
            codeArea.setText("iiiiiiii\nWWWWWWWW");
            codeArea.setEditable(false);
            codeArea.getScene().getRoot().applyCss();
            codeArea.getScene().getRoot().layout();
        });

        Rectangle2D start = codeArea.getCharacterBounds(1);
        Rectangle2D end = codeArea.getCharacterBounds(6);
        CodeAreaSkin skin = (CodeAreaSkin) codeArea.getSkin();
        List<javafx.scene.control.IndexRange> ranges = skin.getColumnSelectionRanges(
                start.getMinX(), end.getMinX(), 1, 10);
        interact(() -> {
            codeArea.beginColumnSelection(1);
            codeArea.updateColumnSelection(10, ranges);
        });

        assertEquals(2, ranges.size());
        assertTrue(codeArea.isColumnSelectionActive());
        assertTrue("Wide glyphs should require fewer characters for the same visual width",
                ranges.get(1).getLength() < ranges.get(0).getLength());
    }

    @Test
    public void testColumnSelectionReplacementAppliesToEveryLine() {
        interact(() -> {
            codeArea.setText("abcd\nxy\nwxyz");
            codeArea.beginColumnSelection(1);
            codeArea.updateColumnSelection(11);
            codeArea.replaceColumnSelection("Q");
        });

        assertEquals("aQd\nxQ\nwQz", codeArea.getText());
        assertFalse(codeArea.isColumnSelectionActive());
    }

    @Test
    public void testAltArrowExtendsColumnSelection() {
        interact(() -> {
            codeArea.setText("abcd\nabcd");
            codeArea.positionCaret(1);
            codeArea.requestFocus();
            codeArea.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT,
                    false, false, true, false));
            codeArea.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN,
                    false, false, true, false));
        });

        assertTrue(codeArea.isColumnSelectionActive());
        assertEquals(List.of(
                new javafx.scene.control.IndexRange(1, 2),
                new javafx.scene.control.IndexRange(6, 7)),
                codeArea.getColumnSelectionRanges());
    }

    @Test
    public void testAltArrowColumnSelectionWorksWhenReadonly() {
        interact(() -> {
            codeArea.setText("abcd\nabcd");
            codeArea.setEditable(false);
            codeArea.positionCaret(1);
            codeArea.requestFocus();
            codeArea.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT,
                    false, false, true, false));
            codeArea.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN,
                    false, false, true, false));
        });

        assertTrue(codeArea.isColumnSelectionActive());
        assertEquals(List.of(
                new javafx.scene.control.IndexRange(1, 2),
                new javafx.scene.control.IndexRange(6, 7)),
                codeArea.getColumnSelectionRanges());
        assertEquals("abcd\nabcd", codeArea.getText());
    }

    @Test
    public void testCodeAreaUndoRedo() {
        // Set initial text
        interact(() -> codeArea.setText("Initial text"));

        // Modify text
        interact(() -> codeArea.appendText(" modified"));

        // Undo the modification
        interact(() -> codeArea.undo());

        // Verify undo worked
        assertEquals("Initial text", codeArea.getText());

        // Redo the modification
        interact(() -> codeArea.redo());

        // Verify redo worked
        assertEquals("Initial text modified", codeArea.getText());
    }
}
