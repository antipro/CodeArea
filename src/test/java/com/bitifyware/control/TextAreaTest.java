package com.bitifyware.control;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;

import static org.junit.Assert.*;

/**
 * Simple JUnit test for CodeArea using TestFX.
 * Note: This test requires a GUI environment to run.
 * Tests will be skipped on headless environments or unsupported platforms.
 */
public class TextAreaTest extends ApplicationTest {

    private CodeArea codeArea;

    @BeforeClass
    public static void checkPlatform() {
        // Get OS name and headless property
        String osName = System.getProperty("os.name").toLowerCase();
        boolean isHeadless = Boolean.parseBoolean(System.getProperty("java.awt.headless", "false"));
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

        StackPane root = new StackPane(codeArea);
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

