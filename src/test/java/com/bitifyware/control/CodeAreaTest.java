package com.bitifyware.control;

import com.bitifyware.control.skin.CodeAreaSkin;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.geometry.Point2D;
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
