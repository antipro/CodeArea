package com.bitifyware.control;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class SelectedTextTest {
    @BeforeClass public static void toolkit() throws Exception {
        InCacheContentTest.initializeToolkit();
    }

    private static class CountingContent extends CodeInputControl.ContentBase {
        final StringBuilder data = new StringBuilder();
        int rangeReads;
        @Override public String get(int start, int end) {
            // Selecting validates surrogate boundaries with single-character reads.
            if (end - start > 1) rangeReads++;
            return data.substring(start, end);
        }
        @Override public String get() { return data.toString(); }
        @Override public String getValue() { return get(); }
        @Override public int length() { return data.length(); }
        @Override public void insert(int index, String text, boolean notify) {
            data.insert(index, text);
            if (notify) fireValueChangedEvent();
        }
        @Override public void delete(int start, int end, boolean notify) {
            data.delete(start, end);
            if (notify) fireValueChangedEvent();
        }
    }

    private static class Input extends CodeInputControl {
        Input(CountingContent content) { super(content); }
    }

    private static void onFx(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(15, TimeUnit.SECONDS);
    }

    @Test public void largeSelectionsAreLazyAndRepeatedReadsAreCached() throws Exception {
        onFx(() -> {
            CountingContent content = new CountingContent();
            Input input = new Input(content);
            input.setText("x".repeat(8 * 1024 * 1024));
            content.rangeReads = 0;
            for (int end = 100000; end <= input.getLength(); end += 100000) input.selectRange(0, end);
            input.selectAll();
            assertEquals(0, content.rangeReads);
            String selected = input.getSelectedText();
            assertEquals(input.getLength(), selected.length());
            assertSame(selected, input.selectedTextProperty().get());
            assertEquals(1, content.rangeReads);
            input.positionCaret(2);
            assertEquals("", input.getSelectedText());
            assertEquals(1, content.rangeReads);
            assertSame(input, input.selectedTextProperty().getBean());
            assertEquals("selectedText", input.selectedTextProperty().getName());
        });
    }

    @Test public void changeListenersBindingsAndRemovalRemainCompatible() throws Exception {
        onFx(() -> {
            CountingContent content = new CountingContent();
            Input input = new Input(content);
            input.setText("abcdef");
            List<String> changes = new ArrayList<>();
            ChangeListener<String> listener = (observable, oldValue, newValue) ->
                    changes.add(oldValue + "->" + newValue);
            input.selectedTextProperty().addListener(listener);
            input.selectRange(1, 4);
            input.selectRange(4, 1);
            input.selectRange(2, 5);
            assertEquals(List.of("->bcd", "bcd->cde"), changes);
            SimpleStringProperty bound = new SimpleStringProperty();
            bound.bind(input.selectedTextProperty());
            assertEquals("cde", bound.get());
            input.selectRange(0, 2);
            assertEquals("ab", bound.get());
            bound.unbind();
            input.selectedTextProperty().removeListener(listener);
            content.rangeReads = 0;
            input.selectAll();
            assertEquals(0, content.rangeReads);
            assertEquals("abcdef", input.getSelectedText());
        });
    }

    @Test public void invalidationIsLazyAndDirectEditsInvalidateCachedText() throws Exception {
        onFx(() -> {
            CountingContent content = new CountingContent();
            Input input = new Input(content);
            input.setText("abcdef");
            input.getSelectedText();
            int[] events = {0};
            input.selectedTextProperty().addListener((InvalidationListener) observable -> events[0]++);
            content.rangeReads = 0;
            input.selectRange(1, 4);
            input.selectRange(2, 5);
            assertEquals(1, events[0]); // Standard lazy-property invalidation coalesces until read.
            assertEquals(0, content.rangeReads);
            assertEquals("cde", input.getSelectedText());
            content.delete(2, 3, false);
            content.insert(2, "X", true);
            assertEquals("Xde", input.getSelectedText());
            input.replaceSelection("hi");
            assertEquals("", input.getSelectedText());
            input.undo();
            assertEquals("abXdef", input.getText());
            input.redo();
            assertEquals("abhif", input.getText());
            input.setText(null);
            assertEquals("", input.getSelectedText());
        });
    }

    @Test public void reloadAndIdenticalTextChangesProduceCorrectValues() throws Exception {
        onFx(() -> {
            CountingContent content = new CountingContent();
            Input input = new Input(content);
            input.setText("abcdef");
            input.selectRange(1, 4);
            assertEquals("bcd", input.getSelectedText());
            List<String> values = new ArrayList<>();
            input.selectedTextProperty().addListener((observable, oldValue, newValue) -> values.add(newValue));
            content.delete(1, 2, false);
            content.insert(1, "b", true);
            assertTrue("Equal text must not trigger a change notification", values.isEmpty());
            input.setText("replacement");
            assertEquals("", input.getSelectedText());
            input.selectRange(0, 3);
            assertEquals("rep", input.getSelectedText());
            assertEquals(List.of("", "rep"), values);
        });
    }
}
