package com.bitifyware.control;

import javafx.scene.control.IndexRange;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class ContentCursorTest {
    @BeforeClass public static void toolkit() throws Exception { InCacheContentTest.initializeToolkit(); }

    /** Fails any accidental full-string request and records the largest range read. */
    private static class StringlessContent extends CodeInputControl.ContentBase {
        final StringBuilder value;
        int maxRead;
        StringlessContent(String value) { this.value = new StringBuilder(value); }
        @Override public synchronized String get(int start, int end) {
            maxRead = Math.max(maxRead, end - start);
            return value.substring(start, end);
        }
        @Override public synchronized int length() { return value.length(); }
        @Override public String get() { throw new AssertionError("Full document String requested"); }
        @Override public String getValue() { return get(); }
        @Override public synchronized void insert(int index, String text, boolean notify) {
            if (text.isEmpty()) return;
            markContentModified();
            value.insert(index, text);
            publishContentChange(index, 0, text.length());
            if (notify) fireValueChangedEvent();
        }
        @Override public synchronized void delete(int start, int end, boolean notify) {
            if (start == end) return;
            markContentModified();
            value.delete(start, end);
            publishContentChange(start, end - start, 0);
            if (notify) fireValueChangedEvent();
        }
    }

    private static final class StringlessControl extends CodeInputControl {
        StringlessControl(StringlessContent content) { super(content); }
    }

    @Test public void cursorReadsAcrossBlocksAndForksHaveIndependentPositions() {
        String expected = "a".repeat(4095) + "人😀\n" + "bc".repeat(5000);
        StringlessContent content = new StringlessContent(expected);
        try (TextCursor cursor = content.openCursor(4094)) {
            assertEquals('a', cursor.next());
            assertEquals('人', cursor.next());
            assertEquals('人', cursor.previous());
            try (TextCursor fork = cursor.fork()) {
                fork.seek(0);
                assertEquals(4095, cursor.position());
                char[] chars = new char[expected.length()];
                assertEquals(chars.length, fork.read(chars, 0, chars.length));
                assertEquals(expected, new String(chars));
                assertEquals(-1, fork.read(chars, 0, 1));
                assertEquals(0, fork.read(chars, 0, 0));
            }
            CharSequence sequence = cursor.asCharSequence();
            assertEquals(expected.substring(4094, 4101), sequence.subSequence(4094, 4101).toString());
            var iterator = cursor.asCharacterIterator();
            iterator.setIndex(4095);
            var clone = (java.text.CharacterIterator) iterator.clone();
            clone.first();
            assertEquals(4095, iterator.getIndex());
            assertTrue(content.maxRead <= 4096);
        }
    }

    @Test public void bothBackendsSearchAcrossParagraphsAndBlocks() {
        String value = "x".repeat(4093) + "Ab\nCd 人😀 cat scatter cat_ CAT\nlast cat";
        InMemoryContent memory = new InMemoryContent();
        memory.insert(0, value, false);
        try (InCacheContent cache = new InCacheContent(value)) {
            for (var content : List.of(memory, cache)) {
                SearchQuery query = new SearchQuery("ab\ncd", false, false, false, false);
                try (SearchCursor search = content.search(query)) {
                    assertEquals(new IndexRange(4093, 4098), search.next().orElseThrow());
                    assertTrue(search.next().isEmpty());
                    assertTrue(search.next().isEmpty());
                    assertEquals(new IndexRange(4093, 4098), search.previous().orElseThrow());
                }
                SearchQuery word = new SearchQuery("cat", false, false, true, false);
                List<IndexRange> expected = regexMatches(value, "\\bcat\\b", Pattern.CASE_INSENSITIVE);
                try (SearchCursor search = content.search(word)) {
                    assertEquals(expected, drain(search));
                    search.seek(value.length());
                    for (int i = expected.size() - 1; i >= 0; i--) {
                        assertEquals(expected.get(i), search.previous().orElseThrow());
                    }
                    assertTrue(search.previous().isEmpty());
                }
                try (SearchCursor search = content.search(SearchQuery.regex("(?s)(?<=Ab).*?(?= last|$)"))) {
                    assertEquals(regexMatches(value, "(?s)(?<=Ab).*?(?= last|$)", 0), drain(search));
                }
            }
        }
    }

    private static List<IndexRange> regexMatches(String value, String pattern, int flags) {
        List<IndexRange> matches = new ArrayList<>();
        var matcher = Pattern.compile(pattern, flags).matcher(value);
        while (matcher.find()) matches.add(new IndexRange(matcher.start(), matcher.end()));
        return matches;
    }

    private static List<IndexRange> drain(SearchCursor search) {
        List<IndexRange> matches = new ArrayList<>();
        var match = search.next();
        while (match.isPresent()) {
            matches.add(match.orElseThrow());
            match = search.next();
        }
        return matches;
    }

    @Test public void zeroLengthRegexWrapDirectionsAndBounds() {
        StringlessContent content = new StringlessContent("a\na");
        try (SearchCursor search = content.search(SearchQuery.regex("(?m)^|$"))) {
            List<IndexRange> expected = regexMatches("a\na", "(?m)^|$", 0);
            assertEquals(expected, drain(search));
            assertTrue(search.next().isEmpty());
            search.seek(3);
            for (int i = expected.size() - 1; i >= 0; i--) {
                assertEquals(expected.get(i), search.previous().orElseThrow());
            }
            assertTrue(search.previous().isEmpty());
            assertTrue(search.previous().isEmpty());
        }
        try (SearchCursor search = content.search(SearchQuery.literal("a").withWrap(true))) {
            assertEquals(new IndexRange(0, 1), search.next().orElseThrow());
            assertEquals(new IndexRange(2, 3), search.next().orElseThrow());
            assertEquals(new IndexRange(0, 1), search.next().orElseThrow());
            assertEquals(new IndexRange(2, 3), search.previous().orElseThrow());
        }
        try (SearchCursor search = content.search(SearchQuery.regex("(?<=\\n)a$"), 2, 3, 2)) {
            assertEquals(new IndexRange(2, 3), search.next().orElseThrow());
        }
        assertThrows(IllegalArgumentException.class, () -> SearchQuery.literal(""));
        assertThrows(java.util.regex.PatternSyntaxException.class, () -> content.search(SearchQuery.regex("[")));
        StringlessContent repeated = new StringlessContent("x".repeat(4094) + "cat\ncat");
        try (SearchCursor search = repeated.search(SearchQuery.regex("(cat)\\n\\1"))) {
            assertEquals(new IndexRange(4094, 4101), search.next().orElseThrow());
            assertTrue(search.next().isEmpty());
        }
    }

    @Test public void versionsAndStringlessEventsIncludeSilentEdits() {
        for (CodeInputControl.ContentBase content : List.of(new InMemoryContent(), new InCacheContent())) {
            try {
                List<ContentChange> events = new ArrayList<>();
                Consumer<ContentChange> listener = change -> {
                    assertEquals(change.version(), content.version());
                    assertEquals(3, content.length());
                    events.add(change);
                };
                content.addContentChangeListener(listener);
                long version = content.version();
                content.insert(0, "abc", false);
                assertEquals(version + 1, content.version());
                assertEquals(new ContentChange(version + 1, 0, 0, 3), events.getFirst());
                content.removeContentChangeListener(listener);
                try (TextCursor cursor = content.openCursor(); SearchCursor search = content.search(SearchQuery.literal("a"))) {
                    assertEquals('a', cursor.charAt(0));
                    content.delete(1, 2, false);
                    assertThrows(ConcurrentModificationException.class, cursor::next);
                    assertThrows(ConcurrentModificationException.class, () -> cursor.charAt(0));
                    assertThrows(ConcurrentModificationException.class, search::next);
                }
                long after = content.version();
                content.insert(0, "", true);
                content.delete(0, 0, true);
                assertEquals(after, content.version());
                assertEquals(1, events.size());
            } finally {
                if (content instanceof InCacheContent cache) cache.close();
            }
        }
    }

    @Test public void countStartsUnknownCompletesSeparatelyAndBecomesStale() throws Exception {
        StringlessContent content = new StringlessContent("cat ".repeat(10000));
        AtomicReference<Runnable> work = new AtomicReference<>();
        try (SearchCursor search = content.search(SearchQuery.literal("cat").withWrap(true));
             SearchCount count = search.countAsync(work::set)) {
            assertEquals(SearchCount.State.UNKNOWN, count.result().state());
            assertEquals(OptionalLong.empty(), count.result().count());
            assertEquals(new IndexRange(0, 3), search.next().orElseThrow());
            Thread worker = new Thread(work.get(), "test-count-worker");
            worker.start();
            assertEquals(OptionalLong.of(10000), count.completion().toCompletableFuture().get(10, TimeUnit.SECONDS).count());
            worker.join(10000);
            assertEquals(SearchCount.State.COMPLETE, count.result().state());
            content.insert(0, "cat ", false);
            assertEquals(SearchCount.State.STALE, count.result().state());
            assertFalse(count.result().count().isPresent());
            assertTrue(content.maxRead <= 4096);
        }
    }

    @Test public void queuedCountsCancelOrInvalidateWithoutPublishingAResult() {
        StringlessContent content = new StringlessContent("abc");
        AtomicReference<Runnable> work = new AtomicReference<>();
        try (SearchCursor search = content.search(SearchQuery.literal("a"))) {
            SearchCount count = search.countAsync(work::set);
            count.close();
            work.get().run();
            assertEquals(SearchCount.State.CANCELLED, count.result().state());
        }
        try (SearchCursor search = content.search(SearchQuery.literal("a")); SearchCount count = search.countAsync(work::set)) {
            content.insert(0, "b", false);
            assertEquals(SearchCount.State.STALE, count.result().state());
            work.get().run();
            assertFalse(count.result().count().isPresent());
        }
    }

    @Test public void streamingOutputIsBoundedAndDetectsMidWriteEdits() throws Exception {
        String value = "人😀\n".repeat(10000);
        StringlessContent content = new StringlessContent(value);
        StringWriter writer = new StringWriter();
        content.writeTo(writer);
        assertEquals(value, writer.toString());
        assertTrue(content.maxRead <= 4096);
        assertThrows(ConcurrentModificationException.class, () -> content.writeTo(new Writer() {
            @Override public void write(char[] chars, int start, int count) { content.insert(0, "x", false); }
            @Override public void flush() { }
            @Override public void close() { fail("Must not close the supplied writer"); }
        }));
        assertThrows(IOException.class, () -> content.writeTo(new Writer() {
            @Override public void write(char[] chars, int start, int count) throws IOException { throw new IOException("test"); }
            @Override public void flush() { }
            @Override public void close() { fail("Must not close the supplied writer"); }
        }));
    }

    @Test public void navigationAndBreakIteratorsDoNotReadFullStrings() {
        StringlessContent content = new StringlessContent("a😀e\u0301 word next");
        StringlessControl control = new StringlessControl(content);
        control.positionCaret(1);
        control.forward();
        assertEquals(3, control.getCaretPosition());
        control.backward();
        assertEquals(1, control.getCaretPosition());
        control.selectForward();
        assertEquals("😀", control.getSelectedText());
        control.positionCaret(1);
        assertTrue(control.deleteNextChar());
        assertEquals("ae\u0301 word next", content.value.toString());
        control.positionCaret(3);
        assertTrue(control.deletePreviousChar());
        assertEquals("ae word next", content.value.toString());
        control.nextWord();
        control.previousWord();
        control.endOfNextWord();
        try (TextCursor cursor = content.openCursor()) {
            BreakIterator legacy = new CodeBreakIterator();
            legacy.setText(content.value.toString());
            BreakIterator lazy = new CodeBreakIterator();
            lazy.setText(cursor.asCharacterIterator());
            for (int i = 0; i <= content.length(); i++) {
                assertEquals(legacy.following(i), lazy.following(i));
                assertEquals(legacy.preceding(i), lazy.preceding(i));
            }
        }
    }

    @Test public void cacheCloseInvalidatesReadersAndPendingCounts() {
        InCacheContent content = new InCacheContent("abc");
        AtomicReference<Runnable> work = new AtomicReference<>();
        try (TextCursor cursor = content.openCursor();
             SearchCursor search = content.search(SearchQuery.literal("a"));
             SearchCount count = search.countAsync(work::set)) {
            content.close();
            assertThrows(IllegalStateException.class, cursor::next);
            assertEquals(SearchCount.State.STALE, count.result().state());
            work.get().run();
            assertFalse(count.result().count().isPresent());
        }
    }

    @Test public void modelSynchronizationSerializesReadsWithWrites() throws Exception {
        CountDownLatch reading = new CountDownLatch(1), release = new CountDownLatch(1);
        CountDownLatch writing = new CountDownLatch(1);
        StringlessContent content = new StringlessContent("a".repeat(100000)) {
            private boolean first = true;
            @Override public synchronized String get(int start, int end) {
                if (first) {
                    first = false;
                    reading.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Read gate timed out");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new CancellationException();
                    }
                }
                return super.get(start, end);
            }
        };
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try (SearchCursor search = content.search(SearchQuery.literal("missing"))) {
            Future<?> read = workers.submit(() -> {
                try {
                    assertTrue(search.next().isEmpty());
                } catch (ConcurrentModificationException expected) {
                    // The writer invalidated this version, not a torn read.
                }
            });
            assertTrue(reading.await(10, TimeUnit.SECONDS));
            Future<?> edit = workers.submit(() -> {
                writing.countDown();
                content.insert(0, "b", false);
            });
            assertTrue(writing.await(10, TimeUnit.SECONDS));
            assertFalse("Writer must wait while a bounded read holds the monitor", edit.isDone());
            release.countDown();
            edit.get(10, TimeUnit.SECONDS);
            read.get(10, TimeUnit.SECONDS);
            assertThrows(ConcurrentModificationException.class, search::next);
            assertEquals('b', content.value.charAt(0));
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    @Test public void existingTextChangeListenersAndNewInvalidationsBothWork() {
        StringlessControl control = new StringlessControl(new StringlessContent("abc"));
        List<ContentChange> deltas = new ArrayList<>();
        control.addContentChangeListener(deltas::add);
        int[] invalidations = {0};
        javafx.beans.InvalidationListener invalidation = observable -> invalidations[0]++;
        control.textProperty().addListener(invalidation);
        control.replaceText(1, 2, "d");
        assertEquals(2, deltas.size()); // Original replacement path deletes, then inserts.
        assertTrue(invalidations[0] > 0);
        control.textProperty().removeListener(invalidation);
        CodeArea area = new CodeArea();
        List<String> changes = new ArrayList<>();
        area.textProperty().addListener((observable, oldText, newText) -> changes.add(oldText + ":" + newText));
        area.setText("old");
        area.setText("new");
        assertTrue(changes.contains(":old"));
        assertTrue(changes.contains("old:new"));
    }

    @Test public void realBackendsCountOnWorkersAndStreamWithoutConcatenation() throws Exception {
        String value = "before\n人😀 after\n".repeat(10000);
        InMemoryContent memory = new InMemoryContent();
        memory.insert(0, value, false);
        try (InCacheContent cache = new InCacheContent(value)) {
            for (CodeInputControl.ContentBase content : List.of(memory, cache)) {
                try (SearchCursor search = content.search(SearchQuery.regex("(?<=before\\n)人😀"));
                     SearchCount count = search.countAsync()) {
                    SearchCount.Result result = count.completion().toCompletableFuture().get(10, TimeUnit.SECONDS);
                    assertEquals(SearchCount.State.COMPLETE, result.state());
                    assertEquals(OptionalLong.of(10000), result.count());
                    StringWriter writer = new StringWriter();
                    content.writeTo(writer);
                    assertEquals(value, writer.toString());
                }
            }
        }
        try (InDiskContent legacy = new InDiskContent("a\na\na")) {
            try (SearchCursor search = legacy.search(SearchQuery.literal("a"))) {
                assertEquals(3, drain(search).size());
                legacy.insert(0, "x", false);
                assertThrows(ConcurrentModificationException.class, search::next);
            }
        }
    }
}
