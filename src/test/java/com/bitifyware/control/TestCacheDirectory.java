package com.bitifyware.control;

import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;

/** One temporary cache root per test JVM, matching the cache owner's JVM lifetime. */
final class TestCacheDirectory {
    private static TemporaryFolder temporary;

    static synchronized Path initialize() throws IOException {
        if (temporary == null) {
            TemporaryFolder created = new TemporaryFolder();
            created.create();
            try { CacheDirectory.current(created.getRoot().toPath()); }
            catch (IOException | RuntimeException e) { created.delete(); throw e; }
            temporary = created;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { CacheDirectory.initialized().close(); }
                catch (IOException e) { System.err.println("Cannot close test cache owner: " + e); }
                created.delete();
            }, "codearea-test-cache-cleanup"));
        }
        return temporary.getRoot().toPath();
    }
}
