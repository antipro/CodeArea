package com.bitifyware.control;

import java.nio.file.Files;
import java.nio.file.Path;

/** Separate JVM fixture: real OS-held owner lock and abrupt process termination. */
public class CacheDirectoryProcess {
    public static void main(String[] args) throws Exception {
        try {
            CacheDirectory.initialized();
            throw new AssertionError("Cache use before explicit initialization must be rejected");
        } catch (IllegalStateException expected) { /* No implicit application-specific path. */ }
        Path root = Path.of(args[0]);
        CacheDirectory owner = CacheDirectory.current(root);
        if (CacheDirectory.initialized() != owner) throw new AssertionError("Explicit owner must be used for cache access");
        if (CacheDirectory.current(root) != owner) throw new AssertionError("Same cache root must reuse the owner");
        try {
            CacheDirectory.current(root.resolve("other"));
            throw new AssertionError("Changing a live cache root must be rejected");
        } catch (IllegalStateException expected) { /* Never relocate live mappings. */ }
        Path cache = owner.createCache();
        Files.writeString(cache, "fixture cache");
        Path ready = Path.of(args[1]);
        Path preparing = ready.resolveSibling(ready.getFileName() + ".tmp");
        Files.writeString(preparing, cache.toString());
        Files.move(preparing, ready);
        // Keep the lock alive until the parent kills us, without normal cache cleanup.
        System.in.read();
        Runtime.getRuntime().halt(0);
    }
}
