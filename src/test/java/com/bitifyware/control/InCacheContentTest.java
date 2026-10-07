package com.bitifyware.control;

import javafx.application.Platform;
import org.junit.BeforeClass;
import org.junit.Assume;
import org.junit.Test;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class InCacheContentTest {
    @BeforeClass public static void initializeToolkit() throws Exception {
        if (System.getProperty("os.name").toLowerCase().contains("linux")) {
            Assume.assumeTrue("JavaFX needs a display for Control initialization",
                    System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null);
        }
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyStarted) {
            ready.countDown();
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @Test public void randomEditsMatchMemoryContent() {
        Random random = new Random(71021);
        InMemoryContent memory = new InMemoryContent();
        try (InCacheContent cache = new InCacheContent()) {
            String[] pieces = {"a", "\n", "\n\n", "人😀\t", "abc\nxyz\n", "\r\u0001X"};
            for (int step = 0; step < 1000; step++) {
                if (memory.length() == 0 || random.nextBoolean()) {
                    int index = random.nextInt(memory.length() + 1);
                    String text = pieces[random.nextInt(pieces.length)];
                    memory.insert(index, text, false);
                    cache.insert(index, text, false);
                } else {
                    int start = random.nextInt(memory.length() + 1);
                    int end = start + random.nextInt(memory.length() - start + 1);
                    memory.delete(start, end, false);
                    cache.delete(start, end, false);
                }
                assertEquals(memory.get(), cache.get());
                assertEquals(memory.length(), cache.length());
                assertEquals(memory.getParagraphList().size(), cache.getParagraphList().size());
                int offset = 0;
                for (int i = 0; i < cache.getParagraphList().size(); i++) {
                    assertEquals(memory.getParagraphList().get(i).toString(),
                            cache.getParagraphList().get(i).toString());
                    assertEquals(offset, cache.getParagraphStart(i));
                    assertEquals(i, cache.getParagraphIndex(offset));
                    offset += cache.getParagraphLength(i) + 1;
                }
                int start = random.nextInt(cache.length() + 1);
                int end = start + random.nextInt(cache.length() - start + 1);
                assertEquals(memory.get(start, end), cache.get(start, end));
            }
        }
    }

    @Test public void streamingAndChangeSnapshotsPreserveEmptyLines() throws Exception {
        try (InCacheContent cache = new InCacheContent(new StringReader("\n人😀\r\n\n"))) {
            assertEquals("\n人😀\n\n", cache.get());
            assertEquals(4, cache.getParagraphList().size());
            List<List<? extends CharSequence>> removed = new ArrayList<>();
            cache.getParagraphList().addListener((javafx.collections.ListChangeListener<CharSequence>) change -> {
                while (change.next()) removed.add(change.getRemoved());
            });
            cache.delete(0, cache.length(), false);
            cache.insert(0, "new", false);
            assertEquals("人😀", removed.getFirst().get(1).toString());
            assertEquals("", removed.getFirst().getLast().toString());
            assertEquals(1, cache.getParagraphList().size());
            assertEquals("new", cache.get());
        }
    }

    @Test public void installedCacheTransfersStorageAndPreservesSubscriptionsAndSnapshots() throws Exception {
        try (InCacheContent cache = new InCacheContent("old\ncontent");
             InCacheContent prepared = new InCacheContent(new StringReader("new\n人😀\n"))) {
            var paragraphs = cache.getParagraphList();
            List<ContentChange> edits = new ArrayList<>();
            List<List<? extends CharSequence>> removed = new ArrayList<>();
            int[] invalidations = {0};
            cache.addContentChangeListener(edits::add);
            cache.addListener((javafx.beans.InvalidationListener) observable -> invalidations[0]++);
            paragraphs.addListener((javafx.collections.ListChangeListener<CharSequence>) change -> {
                while (change.next()) removed.add(change.getRemoved());
            });
            try (var oldCursor = cache.openCursor(); var preparedCursor = prepared.openCursor()) {
                cache.install(prepared);
                assertSame(paragraphs, cache.getParagraphList());
                assertEquals("new\n人😀\n", cache.get());
                assertEquals(1, edits.size());
                assertEquals(1, removed.size());
                assertEquals(1, invalidations[0]);
                assertThrows(java.util.ConcurrentModificationException.class, oldCursor::checkValid);
                assertThrows(IllegalStateException.class, preparedCursor::checkValid);
                assertThrows(IllegalStateException.class, prepared::get);
            }
            prepared.close();
            cache.insert(0, "prefix", true);
            assertEquals("prefixnew\n人😀\n", cache.get());
            assertEquals("old", removed.getFirst().get(0).toString());
            assertEquals("content", removed.getFirst().get(1).toString());
            assertEquals(2, edits.size());
        }
    }

    @Test public void mappingCrossesChunksAndClosesDeterministically() {
        MappedCache cache = new MappedCache();
        Path path = cache.path();
        String text = "人😀".repeat(400000);
        long offset = cache.append(text);
        assertEquals(text, cache.read(offset, text.length()));
        assertTrue(Files.exists(path));
        cache.close();
        cache.close();
        assertFalse(Files.exists(path));
        assertThrows(IllegalStateException.class, () -> cache.read(0, 0));
    }

    @Test public void mappingLeaseRetainsSnapshotsButReleasesStorageWithItsLastOwner() {
        MappedCache cache = new MappedCache();
        Path path = cache.path();
        cache.append("snapshot");
        Object snapshot = new Object();
        var lease = cache.retainFor(snapshot);
        cache.close();
        assertTrue(Files.exists(path));
        assertEquals("snapshot", cache.read(0, 8));
        lease.clean();
        assertFalse(Files.exists(path));
        assertThrows(IllegalStateException.class, () -> cache.read(0, 1));
        java.lang.ref.Reference.reachabilityFence(snapshot);
    }

    @Test public void rangeValidationAndClosedContent() {
        InCacheContent cache = new InCacheContent("x\n");
        assertEquals("", cache.get(cache.length(), cache.length()));
        assertThrows(IndexOutOfBoundsException.class, () -> cache.get(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> cache.insert(0, null, false));
        assertThrows(IllegalArgumentException.class, () -> cache.delete(2, 1, false));
        cache.close();
        assertThrows(IllegalStateException.class, cache::get);
        assertThrows(IllegalStateException.class, cache::length);
    }
}
