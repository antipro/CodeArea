package com.bitifyware.control;

import org.junit.Test;

import java.nio.file.Files;
import java.util.Random;

import static org.junit.Assert.*;

public class MappedCacheTest {
    @Test public void bulkReadWriteCrossesMappingAndTransferBoundaries() throws Exception {
        Random random = new Random(731);
        char[] chars = new char[3 * 524288 + 137];
        for (int i = 0; i < chars.length; i++) chars[i] = (char) random.nextInt(65536);
        String text = new String(chars); // Includes NUL, non-ASCII, and unpaired surrogate code units.
        java.nio.file.Path path;
        try (MappedCache cache = new MappedCache()) {
            path = cache.path();
            assertEquals(0, cache.append("prefix"));
            assertEquals(6, cache.append(text));
            assertEquals(text.length() + 6L, cache.append(new StringBuilder("人😀suffix")));
            assertEquals(text, cache.read(6, text.length()));
            for (int boundary : new int[]{8192, 524288, 1048576, 1572864}) {
                assertEquals(text.substring(boundary - 17, boundary + 23),
                        cache.read(6L + boundary - 17, 40));
            }
            assertEquals("人😀suffix", cache.read(text.length() + 6L, 9));
            assertEquals("", cache.read(0, 0));
            assertEquals(text.length() + 15L, cache.append(""));
        }
        assertFalse(Files.exists(path));
    }

    @Test public void arbitraryCharSequencesAndSmallAppendsPreservePreviousData() {
        try (MappedCache cache = new MappedCache()) {
            StringBuilder expected = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                CharSequence piece = java.nio.CharBuffer.wrap("a人😀\n");
                assertEquals(expected.length(), cache.append(piece));
                expected.append(piece);
            }
            assertEquals(expected.toString(), cache.read(0, expected.length()));
            assertEquals("a人😀\n", cache.read(5, 5));
        }
    }

    @Test public void concurrentReadsDoNotShareBufferPositions() throws Exception {
        try (MappedCache cache = new MappedCache()) {
            String text = "a人😀\n".repeat(220000);
            cache.append(text);
            var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
            try {
                var tasks = new java.util.ArrayList<java.util.concurrent.Callable<Void>>();
                for (int worker = 0; worker < 4; worker++) {
                    final int offset = worker * 190003;
                    tasks.add(() -> {
                        for (int i = 0; i < 50; i++) {
                            assertEquals(text.substring(offset, offset + 10000), cache.read(offset, 10000));
                        }
                        return null;
                    });
                }
                for (var result : executor.invokeAll(tasks)) result.get();
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
