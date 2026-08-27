package com.bitifyware.control;

import javafx.scene.Scene;
import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.After;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InDiskCodeAreaTest extends ApplicationTest {

    private DiskBackedCodeArea codeArea;

    @BeforeClass
    public static void requireDisplay() {
        String os = System.getProperty("os.name").toLowerCase();
        Assume.assumeFalse(Boolean.parseBoolean(System.getProperty("java.awt.headless", "true")));
        Assume.assumeTrue(!os.contains("linux") || System.getenv("DISPLAY") != null);
    }

    @Override
    public void start(Stage stage) {
        codeArea = new DiskBackedCodeArea();
        stage.setScene(new Scene(new StackPane(codeArea), 600, 400));
        stage.show();
    }

    @After
    public void closeContent() {
        if (codeArea != null) {
            interact(codeArea::closeContent);
        }
    }

    @Test
    public void editsLargeDiskBackedContentThroughCodeArea() {
        String text = "0123456789\n".repeat(20_000) + "last";
        interact(() -> {
            codeArea.setText(text);
            codeArea.insertText(5, "inserted\n");
            codeArea.deleteText(2, 4);
        });

        String expected = new StringBuilder(text)
                .insert(5, "inserted\n")
                .delete(2, 4)
                .toString();
        assertEquals(expected, codeArea.getText());
        assertEquals(20_002, codeArea.getParagraphs().size());
        assertEquals("014inserted", codeArea.getParagraphs().get(0).toString());
    }

    @Test(timeout = 30_000)
    public void rendersOnlyViewportParagraphsAndRepaintsSelectAllWhileScrolling() throws Exception {
        String text = "line content\n".repeat(20_000) + "last";
        interact(() -> {
            codeArea.setText(text);
            codeArea.getScene().getRoot().applyCss();
            codeArea.getScene().getRoot().layout();
        });
        WaitForAsyncUtils.waitForFxEvents();

        Group paragraphNodes = paragraphNodes();
        assertTrue("Rendered the complete document", paragraphNodes.getChildren().size() < 100);

        interact(() -> {
            codeArea.selectAll();
            codeArea.scrollToLine(15_000);
            codeArea.getScene().getRoot().layout();
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(0, codeArea.getSelection().getStart());
        assertEquals(text.length(), codeArea.getSelection().getEnd());
        assertFalse("Ctrl+A eagerly materialized the complete selected text",
                isSelectedTextMaterialized());
        assertTrue("Viewport did not move to the requested paragraph",
                firstRenderedParagraph() > 14_000);
        assertTrue("Rendered node count grew with document size",
                paragraphNodes.getChildren().size() < 100);
        assertTrue("Visible selection was not repainted after scrolling",
                selectionHighlightGroup().getChildren().size() > 0);
    }

    private Object skinField(String name) throws Exception {
        Field field = codeArea.getSkin().getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(codeArea.getSkin());
    }

    private Group paragraphNodes() throws Exception {
        return (Group) skinField("paragraphNodes");
    }

    private Group selectionHighlightGroup() throws Exception {
        return (Group) skinField("selectionHighlightGroup");
    }

    private int firstRenderedParagraph() throws Exception {
        return (int) skinField("firstRenderedParagraph");
    }

    private boolean isSelectedTextMaterialized() throws Exception {
        Field propertyField = CodeInputControl.class.getDeclaredField("selectedText");
        propertyField.setAccessible(true);
        Object property = propertyField.get(codeArea);
        Field validField = property.getClass().getDeclaredField("valid");
        validField.setAccessible(true);
        return validField.getBoolean(property);
    }

    private static final class DiskBackedCodeArea extends CodeArea {
        private DiskBackedCodeArea() {
            super("", true);
        }

        private void closeContent() {
            ((InDiskContent) getContent()).close();
        }
    }
}
