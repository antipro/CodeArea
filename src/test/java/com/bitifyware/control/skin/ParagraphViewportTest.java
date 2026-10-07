package com.bitifyware.control.skin;

import com.bitifyware.control.CodeArea;
import com.bitifyware.control.InCacheContentTest;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.paint.Color;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ParagraphViewportTest {
    @BeforeClass public static void toolkit() throws Exception { InCacheContentTest.initializeToolkit(); }

    private static final class CountingArea extends CodeArea {
        int lengthReads;
        CountingArea() { super("row\n".repeat(100000), true); }
        @Override public int getParagraphLength(int index) {
            lengthReads++;
            return super.getParagraphLength(index);
        }
    }

    @Test public void localAndStructuralEditsKeepIndexAndUnchangedMeasurements() throws Exception {
        FutureTask<Void> test = new FutureTask<>(() -> {
            CountingArea area = new CountingArea();
            try {
                ParagraphViewport viewport = new ParagraphViewport();
                area.getEmptyLines().add(new CodeArea.EmptyLine(90000, Color.RED));
                area.getEmptyLines().add(new CodeArea.EmptyLine(100001, Color.RED));
                viewport.reset(area, 10, false);
                viewport.measure(80000, 75);
                Field rows = ParagraphViewport.class.getDeclaredField("rows");
                rows.setAccessible(true);
                Object original = rows.get(viewport);
                int[] notifications = {0};
                area.getParagraphs().addListener((ListChangeListener<CharSequence>) change -> {
                    while (change.next()) {
                        assertTrue(viewport.change(area, change.getFrom(), change.getAddedSize(), change.getRemovedSize(), 10));
                        notifications[0]++;
                    }
                });
                area.lengthReads = 0;
                area.insertText(1, "x");
                assertEquals(1, area.lengthReads);
                assertSame(original, rows.get(viewport));
                assertEquals(75, viewport.y(80001) - viewport.y(80000), 0);
                assertEquals(area.getParagraphStart(80000), viewport.start(80000));
                area.lengthReads = 0;
                area.insertText(1, "\n");
                assertEquals(2, area.lengthReads);
                assertSame(original, rows.get(viewport));
                assertEquals(75, viewport.y(80002) - viewport.y(80001), 0);
                assertEquals(area.getParagraphStart(80001), viewport.start(80001));
                assertEquals(10, viewport.before(90000), 0);
                assertEquals(10, viewport.before(100001), 0);
                area.lengthReads = 0;
                area.deleteText(1, 2);
                assertEquals(1, area.lengthReads);
                assertSame(original, rows.get(viewport));
                assertEquals(75, viewport.y(80001) - viewport.y(80000), 0);
                assertEquals(10, viewport.before(90000), 0);
                assertEquals(100001 * 10.0 + 65 + 20, viewport.height(), 0);
                assertEquals(3, notifications[0]);
                area.lengthReads = 0;
                viewport.reset(area, 12, true);
                assertSame(original, rows.get(viewport));
                assertEquals(0, area.lengthReads);
                assertEquals(75, viewport.y(80001) - viewport.y(80000), 0);
                assertEquals(12, viewport.before(90000), 0);
                // Return to the original font metrics before testing undo/redo.
                viewport.reset(area, 10, true);
                area.undo();
                assertEquals(area.getParagraphStart(80001), viewport.start(80001));
                area.redo();
                assertEquals(area.getParagraphStart(80000), viewport.start(80000));
            } finally {
                area.closeContent();
            }
            return null;
        });
        Platform.runLater(test);
        test.get(30, TimeUnit.SECONDS);
    }
}
