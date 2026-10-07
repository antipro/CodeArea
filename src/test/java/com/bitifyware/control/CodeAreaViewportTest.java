package com.bitifyware.control;

import com.bitifyware.control.skin.CodeAreaSkin;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollBar;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextFlow;
import javafx.scene.paint.Color;
import javafx.scene.input.ScrollEvent;
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

    @Test public void preparedCacheInstallsOnceWithoutReplacingEditorOrSkin() throws Exception {
        settle();
        Object skin = area.getSkin();
        var paragraphs = area.getParagraphs();
        var content = area.getContent();
        interact(() -> {
            area.insertText(0, "edited");
            assertTrue(area.isUndoable());
            area.selectRange(0, 1000);
        });
        int[] events = {0, 0, 0};
        interact(() -> {
            area.addContentChangeListener(change -> events[0]++);
            area.textProperty().addListener((javafx.beans.InvalidationListener) observable -> events[1]++);
            paragraphs.addListener((javafx.collections.ListChangeListener<CharSequence>) change -> {
                while (change.next()) events[2]++;
            });
        });
        try (var prepared = new InCacheContent(new StringReader("loaded\n".repeat(100000)))) {
            Field storage = InCacheContent.class.getDeclaredField("cache");
            storage.setAccessible(true);
            Object mapping = storage.get(prepared);
            try (var cursor = area.openCursor()) {
                interact(() -> area.loadCache(prepared));
                assertThrows(java.util.ConcurrentModificationException.class, cursor::checkValid);
            }
            assertSame(mapping, storage.get(content));
            assertThrows(IllegalStateException.class, prepared::length);
        }
        settle();
        assertSame(skin, area.getSkin());
        assertSame(content, area.getContent());
        assertSame(paragraphs, area.getParagraphs());
        assertArrayEquals(new int[]{1, 1, 1}, events);
        interact(() -> {
            assertEquals("loaded\nloaded", area.getText(0, 13));
            assertEquals(0, area.getCaretPosition());
            assertEquals(0, area.getSelection().getLength());
            assertFalse(area.isUndoable());
            assertFalse(area.isRedoable());
            area.insertText(0, "x");
            area.undo();
            assertEquals("loaded", area.getText(0, 6));
            area.redo();
            assertEquals("xloaded", area.getText(0, 7));
        });
        settle();
        assertTrue(nodes().getChildren().size() < 100);
    }

    @Test public void shorterCacheReloadAtDocumentEndResetsScrollAndImmediateHitGeometry() throws Exception {
        settle();
        interact(() -> area.positionCaret(area.getLength()));
        settle();
        assertTrue(area.getScrollTop() > 0);
        try (var prepared = new InCacheContent("new\nend")) {
            interact(() -> {
                area.loadCache(prepared);
                // IME/accessibility may query before the next layout pulse;
                // the old window's first paragraph is outside the new document.
                assertNotNull(((CodeAreaSkin) area.getSkin()).getCharacterBounds(0));
            });
        }
        settle();
        interact(() -> {
            assertEquals("new\nend", area.getText());
            assertEquals(0, area.getCaretPosition());
            assertEquals(0, area.getScrollTop(), 0.01);
            assertEquals(2, area.getParagraphs().size());
        });
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

    @Test public void wrappedHeightMeasurementsKeepScrollAnchorStable() throws Exception {
        interact(() -> {
            area.setText(("long wrapped content ".repeat(35) + "\n").repeat(3000));
            area.setWrapText(true);
        });
        settle();
        interact(() -> area.setScrollTop(((CodeAreaSkin) area.getSkin()).getLineYPosition(1500)));
        settle();
        double[] offset = new double[1];
        interact(() -> offset[0] = ((CodeAreaSkin) area.getSkin()).getLineYPosition(1500) - area.getScrollTop());
        assertEquals("Measuring overscan must not move the paragraph at the top of the viewport", 0, offset[0], 1);
        double[] position = new double[1];
        interact(() -> position[0] = area.getScrollTop());
        for (int i = 0; i < 12; i++) {
            settle();
            interact(() -> assertEquals("Idle layout must not change the scroll position", position[0], area.getScrollTop(), 0.5));
        }
        for (int paragraph : new int[]{1800, 2100, 1800}) {
            interact(() -> area.setScrollTop(((CodeAreaSkin) area.getSkin()).getLineYPosition(paragraph) + 7));
            settle();
            interact(() -> assertEquals("Scrolling must retain the pixel offset within wrapped paragraph " + paragraph,
                    -7, ((CodeAreaSkin) area.getSkin()).getLineYPosition(paragraph) - area.getScrollTop(), 1));
        }
        for (int i = 0; i < 3; i++) settle();
        interact(() -> {
            double before = area.getScrollTop();
            ScrollPane pane = (ScrollPane) area.lookup(".scroll-pane");
            pane.setVvalue(pane.getVvalue() + 0.001);
            assertTrue("User scrolling must still change the editor offset", area.getScrollTop() > before);
        });
        settle();
        assertTrue(nodes().getChildren().size() < 100);
    }

    @Test public void memoryWrappedHeightMeasurementsKeepScrollAnchorStable() throws Exception {
        interact(() -> {
            root.getChildren().clear();
            area.setSkin(null);
            area.closeContent();
            area = new CodeArea();
            root.getChildren().add(area);
        });
        wrappedHeightMeasurementsKeepScrollAnchorStable();
    }

    @Test public void scrollbarKeepsRenderingNewParagraphsWithPendingHeightDifferences() throws Exception {
        interact(() -> {
            area.setText(("select wrapped column ".repeat(12) + "\n").repeat(2000));
            area.setWrapText(true);
        });
        settle();
        // Fractional/temporarily stale layout dimensions must not permanently
        // disable user scrolling. The old listener rejected every such event.
        interact(() -> {
            ScrollPane pane = (ScrollPane) area.lookup(".scroll-pane");
            javafx.scene.layout.Region content = (javafx.scene.layout.Region) pane.getContent();
            content.setPrefHeight(content.getHeight() + 2);
            double before = area.getScrollTop();
            pane.setVvalue(0.25);
            assertTrue("Height differences must not disconnect the scrollbar", area.getScrollTop() > before + 1);
            content.setPrefHeight(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
        });
        settle();
        for (double value : new double[]{0.4, 0.6, 0.8, 1.0}) {
            interact(() -> ((ScrollPane) area.lookup(".scroll-pane")).setVvalue(value));
            settle();
            settle();
            interact(() -> {
                try {
                    Group rendered = nodes();
                    TextFlow last = (TextFlow) rendered.getChildren().getLast();
                    assertTrue("The rendered window must reach the visible bottom: last=" + (last.getLayoutY() + last.getPrefHeight())
                                    + ", top=" + area.getScrollTop() + ", extent="
                                    + ((ScrollPane) area.lookup(".scroll-pane")).getContent().getLayoutBounds().getHeight(),
                            last.getLayoutY() + last.getPrefHeight()
                                    + ((javafx.scene.layout.Region) ((javafx.scene.layout.HBox)
                                            ((ScrollPane) area.lookup(".scroll-pane")).getContent()).getChildren().get(1))
                                            .getPadding().getBottom()
                                    >= area.getScrollTop()
                                    + ((ScrollPane) area.lookup(".scroll-pane")).getViewportBounds().getHeight() - 1);
                } catch (Exception e) { throw new AssertionError(e); }
            });
        }
        interact(() -> {
            try {
                Field field = CodeAreaSkin.class.getDeclaredField("firstParagraph");
                field.setAccessible(true);
                assertTrue("Scrolling to the end must render the final paragraphs: first=" + field.getInt(area.getSkin())
                        + ", top=" + area.getScrollTop() + ", value=" + ((ScrollPane) area.lookup(".scroll-pane")).getVvalue(),
                        field.getInt(area.getSkin()) > 1950);
            } catch (Exception e) { throw new AssertionError(e); }
        });
        interact(() -> {
            ScrollPane pane = (ScrollPane) area.lookup(".scroll-pane");
            double before = area.getScrollTop();
            pane.getContent().fireEvent(new ScrollEvent(ScrollEvent.SCROLL, 10, 10, 10, 10,
                    false, false, false, false, false, false,
                    0, 60, 0, 60, ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                    ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null));
            assertTrue("Mouse-wheel input must still move away from the bottom", area.getScrollTop() < before);
        });
        settle();
    }

    @Test public void smallUpwardWheelAndScrollbarStepsAreNotRestoredByLayout() throws Exception {
        interact(() -> {
            area.setText(("small scroll wrapped paragraph ".repeat(20) + "\n").repeat(2000));
            area.setWrapText(true);
        });
        settle();
        interact(() -> area.setScrollTop(((CodeAreaSkin) area.getSkin()).getLineYPosition(1000) + 40));
        settle();
        double[] previousOffset = new double[1];
        interact(() -> previousOffset[0] = ((CodeAreaSkin) area.getSkin()).getLineYPosition(1000) - area.getScrollTop());
        for (int i = 0; i < 8; i++) {
            interact(() -> {
                ScrollPane pane = (ScrollPane) area.lookup(".scroll-pane");
                // Reproduce a sub-pixel preferred/actual discrepancy without
                // resizing first. A small user step then numerically resembles
                // the size-only ratio conversion used by the old heuristic.
                try {
                    Field view = CodeAreaSkin.class.getDeclaredField("contentView");
                    view.setAccessible(true);
                    javafx.scene.layout.Region content = (javafx.scene.layout.Region) view.get(area.getSkin());
                    Field height = CodeAreaSkin.class.getDeclaredField("computedPrefHeight");
                    height.setAccessible(true);
                    double original = height.getDouble(area.getSkin());
                    height.setDouble(area.getSkin(), content.getHeight() + 0.75);
                    try {
                        content.fireEvent(new ScrollEvent(ScrollEvent.SCROLL, 10, 10, 10, 10,
                                false, false, false, false, false, false,
                                0, 0.5, 0, 0.5, ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                                ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null));
                    } finally { height.setDouble(area.getSkin(), original); }
                } catch (Exception e) { throw new AssertionError(e); }
            });
            settle();
            interact(() -> {
                double offset = ((CodeAreaSkin) area.getSkin()).getLineYPosition(1000) - area.getScrollTop();
                assertTrue("A half-pixel upward wheel step must not return to its old position",
                        offset > previousOffset[0] + 0.1);
                previousOffset[0] = offset;
            });
        }
        Node[] button = new Node[1];
        interact(() -> {
            ScrollPane pane = (ScrollPane) area.lookup(".scroll-pane");
            ScrollBar bar = (ScrollBar) pane.lookup(".scroll-bar:vertical");
            assertNotNull(bar);
            bar.setUnitIncrement(0.00001);
            button[0] = bar.lookup(".decrement-button");
            assertNotNull(button[0]);
        });
        for (int i = 0; i < 3; i++) {
            clickOn(button[0]);
            settle();
            interact(() -> {
                double offset = ((CodeAreaSkin) area.getSkin()).getLineYPosition(1000) - area.getScrollTop();
                assertTrue("A small scrollbar arrow step must survive layout", offset > previousOffset[0] + 0.01);
                previousOffset[0] = offset;
            });
        }
    }

    @Test public void highlightedWrappedContentMovesUpVisuallyAsWellAsInTheModel() throws Exception {
        interact(() -> {
            area.setStyle("-fx-font-size: 24px;");
            area.setSyntaxHighlighter(new com.bitifyware.control.syntax.DemoSyntax());
            StringBuilder sql = new StringBuilder();
            for (int i = 0; i < 2000; i++) {
                sql.append("select column_name ".repeat(3 + i % 6)).append("from table_name;\n");
            }
            area.setText(sql.toString());
            area.setWrapText(true);
        });
        settle();
        interact(() -> area.setScrollTop(((CodeAreaSkin) area.getSkin()).getLineYPosition(1000) + 40));
        settle();
        settle();
        double[] marker = new double[1];
        interact(() -> marker[0] = ((CodeAreaSkin) area.getSkin()).getLineYPosition(1000) - area.getScrollTop());
        for (int i = 0; i < 12; i++) {
            interact(() -> {
                ScrollPane pane = (ScrollPane) area.lookup(".scroll-pane");
                pane.getContent().fireEvent(new ScrollEvent(ScrollEvent.SCROLL, 10, 10, 10, 10,
                        false, false, false, false, false, false,
                        0, 12, 0, 12, ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                        ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null));
            });
            settle();
            settle();
            interact(() -> {
                ScrollPane pane = (ScrollPane) area.lookup(".scroll-pane");
                Node viewport = pane.lookup(".viewport");
                double actualTop = viewport.localToScene(0, 0).getY()
                        - pane.getContent().localToScene(0, 0).getY();
                ScrollBar debugBar = (ScrollBar) pane.lookup(".scroll-bar:vertical");
                assertEquals("Editor offsets must match the actual scrolled HBox: v=" + pane.getVvalue()
                                + ", bar=" + debugBar.getValue() + ", visible=" + debugBar.getVisibleAmount()
                                + ", viewport=" + pane.getViewportBounds() + ", content=" + pane.getContent().getLayoutBounds(),
                        area.getScrollTop(), actualTop, 1);
                double offset = ((CodeAreaSkin) area.getSkin()).getLineYPosition(1000) - actualTop;
                assertTrue("Each upward wheel step must move the actual content down", offset > marker[0] + 1);
                marker[0] = offset;
            });
        }
        Node[] decrement = new Node[1];
        interact(() -> decrement[0] = area.lookup(".scroll-pane .scroll-bar:vertical .decrement-button"));
        assertNotNull(decrement[0]);
        for (int i = 0; i < 3; i++) {
            clickOn(decrement[0]);
            settle();
            settle();
            interact(() -> {
                ScrollPane pane = (ScrollPane) area.lookup(".scroll-pane");
                double actualTop = pane.lookup(".viewport").localToScene(0, 0).getY()
                        - pane.getContent().localToScene(0, 0).getY();
                assertEquals(area.getScrollTop(), actualTop, 1);
                double offset = ((CodeAreaSkin) area.getSkin()).getLineYPosition(1000) - actualTop;
                assertTrue("Scrollbar arrow clicks must move highlighted content upward", offset > marker[0] + 1);
                marker[0] = offset;
            });
        }
    }

    @Test public void memoryHighlightedWrappedContentMovesUpVisuallyAsWellAsInTheModel() throws Exception {
        interact(() -> {
            root.getChildren().clear();
            area.setSkin(null);
            area.closeContent();
            area = new CodeArea();
            root.getChildren().add(area);
        });
        highlightedWrappedContentMovesUpVisuallyAsWellAsInTheModel();
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
