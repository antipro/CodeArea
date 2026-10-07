package com.bitifyware.control;

import com.bitifyware.control.skin.CodeAreaSkin;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextFlow;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.io.StringReader;

import static org.junit.Assert.*;

public class CodeAreaViewportTest extends ApplicationTest {
    private CodeArea area;
    private StackPane root;

    @BeforeClass public static void requireDisplay() {
        Assume.assumeFalse(Boolean.parseBoolean(System.getProperty("java.awt.headless", "true")));
    }

    @Override public void start(Stage stage) {
        area = new CodeArea("row\n".repeat(100000), true);
        root = new StackPane(area);
        stage.setScene(new Scene(root, 600, 400));
        stage.setWidth(600);
        stage.setHeight(430);
        stage.show();
    }

    @Override public void stop() {
        root.getChildren().clear();
        area.setSkin(null);
        area.closeContent();
    }

    private Group nodes() throws Exception {
        Field field = CodeAreaSkin.class.getDeclaredField("paragraphNodes");
        field.setAccessible(true);
        return (Group) field.get(area.getSkin());
    }

    private void settle() {
        WaitForAsyncUtils.waitForFxEvents();
        interact(() -> { root.applyCss(); root.layout(); });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test public void onlyViewportNodesAndAbsoluteHitOffsets() throws Exception {
        settle();
        assertTrue(nodes().getChildren().size() < 100);
        double targetY = ((CodeAreaSkin) area.getSkin()).getLineYPosition(50000);
        interact(() -> area.setScrollTop(targetY));
        settle();
        assertTrue(nodes().getChildren().size() < 100);
        interact(() -> {
            CodeAreaSkin skin = (CodeAreaSkin) area.getSkin();
            TextFlow flow;
            try { flow = (TextFlow) nodes().getChildren().get(10); }
            catch (Exception e) { throw new AssertionError(e); }
            int position = skin.getIndex(flow.getLayoutX(), flow.getLayoutY() + 1).getInsertionIndex();
            assertTrue("Hit must use document rather than viewport offsets: " + position, position > 190000);
            assertEquals(0, position % 4);
        });
        interact(() -> area.positionCaret(399996));
        settle();
        assertTrue(nodes().getChildren().size() < 100);
        Point2D caret = area.caretPointProperty().get();
        assertTrue("caret=" + caret + ", scroll=" + area.getScrollTop(), caret.getY() >= area.getScrollTop());
        double viewportHeight = ((ScrollPane) area.lookup(".scroll-pane")).getViewportBounds().getHeight();
        assertTrue("caret=" + caret + ", scroll=" + area.getScrollTop(),
                caret.getY() <= area.getScrollTop() + viewportHeight);
        interact(() -> area.replaceText(0, 0, "prefix\n"));
        settle();
        assertEquals("prefix\nrow", area.getText(0, 10));
        assertTrue(nodes().getChildren().size() < 100);
    }

    @Test public void wrappedParagraphsAndOffscreenSelection() throws Exception {
        interact(() -> {
            area.setText(("long content ".repeat(15) + "\n").repeat(1000));
            area.setWrapText(true);
        });
        settle();
        assertTrue(nodes().getChildren().size() < 100);
        interact(() -> area.setScrollTop(((CodeAreaSkin) area.getSkin()).getLineYPosition(500)));
        settle();
        assertTrue(nodes().getChildren().size() < 100);
        interact(() -> area.selectRange(0, area.getLength()));
        settle();
        assertTrue(nodes().getChildren().size() < 100);
        assertEquals(area.getLength(), area.getSelection().getLength());
    }

    @Test public void phantomRowsAndDecorationsUseDocumentIndices() throws Exception {
        interact(() -> {
            area.getEmptyLines().add(new CodeArea.EmptyLine(100, Color.RED));
            area.getEmptyLines().add(new CodeArea.EmptyLine(50000, Color.BLUE));
            area.getLineBackgrounds().add(new CodeArea.LineBackground(50000, Color.GREEN));
            area.getIntraLineHighlights().add(new CodeArea.IntraLineHighlight(200000, 200002, Color.YELLOW));
            area.getErrorPosList().add(200001);
            area.getErrorPosList().add(399999);
        });
        settle();
        interact(() -> area.setScrollTop(((CodeAreaSkin) area.getSkin()).getLineYPosition(50000)));
        settle();
        assertTrue(nodes().getChildren().size() < 100);
        assertTrue(nodes().getChildren().stream().map(TextFlow.class::cast)
                .anyMatch(flow -> flow.getBackground() != null
                        && flow.getBackground().getFills().getFirst().getFill().equals(Color.GREEN)));
        Field field = CodeAreaSkin.class.getDeclaredField("intraHighlightGroup");
        field.setAccessible(true);
        assertFalse(((Group) field.get(area.getSkin())).getChildren().isEmpty());
        interact(() -> assertFalse(area.lookupAll(".error-line").isEmpty()));
    }

    @Test public void streamedFactoryKeepsLengthAndSelectionConsistent() throws Exception {
        InCacheContent content = new InCacheContent(new StringReader("abc\n人😀\n"));
        interact(() -> {
            root.getChildren().clear();
            area.setSkin(null);
            area.closeContent();
            area = CodeArea.fromCache(content);
            root.getChildren().add(area);
            assertEquals(8, area.getLength());
            area.selectRange(4, 7);
            assertEquals("人😀", area.getSelectedText());
            area.replaceSelection("new");
            assertEquals("abc\nnew\n", area.getText());
            area.undo();
            assertEquals("abc\n人😀\n", area.getText());
        });
        settle();
    }

    @Test public void columnSelectionIncludesOffscreenRowsWithoutRetainingNodes() throws Exception {
        settle();
        interact(() -> {
            try {
                TextFlow first = (TextFlow) nodes().getChildren().getFirst();
                double x = first.getLayoutX();
                double firstX = x + CodeAreaSkin.computeTextWidth("r", area.getFont(), 0, 4);
                double lastX = x + CodeAreaSkin.computeTextWidth("ro", area.getFont(), 0, 4);
                var ranges = ((CodeAreaSkin) area.getSkin()).getColumnSelectionRanges(firstX, lastX, 0, 400);
                assertEquals(101, ranges.size());
                assertEquals(new javafx.scene.control.IndexRange(401, 402), ranges.getLast());
                assertTrue(nodes().getChildren().size() < 100);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private void assertGutterMatchesParagraphs() throws Exception {
        Field field = CodeAreaSkin.class.getDeclaredField("gutter");
        field.setAccessible(true);
        Pane gutter = (Pane) field.get(area.getSkin());
        var paragraphs = nodes().getChildren();
        assertEquals(paragraphs.size(), gutter.getChildren().size());
        for (int i = 0; i < paragraphs.size(); i++) {
            TextFlow flow = (TextFlow) paragraphs.get(i);
            Label label = (Label) gutter.getChildren().get(i);
            String description = "gutter row " + label.getText();
            assertNotNull(description + " must have a skin in this layout pass", label.getSkin());
            assertEquals(description + " y", flow.getLayoutY(), label.getLayoutY(), 0.5);
            assertEquals(description + " height", flow.getPrefHeight(), label.getHeight(), 0.5);
            assertTrue(description + " width", label.getWidth() >= label.prefWidth(-1));
        }
    }

    @Test public void gutterStaysAlignedDuringScrollResizeAndFontChanges() throws Exception {
        interact(() -> area.setStyle("-fx-font-family: 'Monospace'; -fx-font-size: 18px;"));
        settle();
        // Check the same layout pass, rather than allowing a later pulse to
        // conceal stale VBox positions or newly created labels without skins.
        for (int row : new int[]{2303, 8000, 20, 99990, 50000}) {
            interact(() -> {
                Stage stage = (Stage) area.getScene().getWindow();
                stage.setHeight(row == 2303 ? 800 : 600);
                area.setScrollTop(((CodeAreaSkin) area.getSkin()).getLineYPosition(row));
                root.applyCss();
                root.layout();
                try { assertGutterMatchesParagraphs(); }
                catch (Exception e) { throw new AssertionError(e); }
            });
            settle();
        }
        interact(() -> {
            area.setText("line\n".repeat(10000));
            area.setStyle("-fx-font-family: 'Monospace'; -fx-font-size: 24px;");
        });
        settle();
        interact(() -> {
            area.setScrollTop(((CodeAreaSkin) area.getSkin()).getLineYPosition(2303));
            root.applyCss();
            root.layout();
            try { assertGutterMatchesParagraphs(); }
            catch (Exception e) { throw new AssertionError(e); }
        });
    }
}
