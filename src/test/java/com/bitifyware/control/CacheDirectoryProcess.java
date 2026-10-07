package com.bitifyware.control;

import java.nio.file.Files;
import java.nio.file.Path;

/** Separate JVM fixture: real OS-held owner lock and abrupt process termination. */
public class CacheDirectoryProcess {
    public static void main(String[] args) throws Exception {
        CacheDirectory owner = new CacheDirectory(Path.of(args[0]));
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
