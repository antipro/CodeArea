package com.bitifyware.control;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.UUID;

/** Process-owned mapped caches. OS locks, not PID checks, determine whether an owner is alive. */
final class CacheDirectory implements AutoCloseable {
    private static CacheDirectory current;
    private static final String OWNER_LOCK = ".owner.lock";
    private final Path directory;
    private final FileChannel channel;
    private final FileLock lock;

    static synchronized CacheDirectory current() throws IOException {
        if (current == null) {
            current = new CacheDirectory(Path.of(System.getProperty("user.home"), ".sqlife", "mapfile"));
        }
        return current;
    }

    // Explicit root is for isolated tests; production always uses the fixed user-home directory.
    CacheDirectory(Path root) throws IOException {
        synchronized (CacheDirectory.class) {
            Files.createDirectories(root);
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Mapped cache root must be a directory, not a symbolic link: " + root);
            }
            // Serialize directory publication/deletion so a new owner cannot be mistaken for an orphan.
            try (FileChannel coordinator = FileChannel.open(root.resolve(".cleanup.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 FileLock ignored = coordinator.lock()) {
                cleanup(root);
                directory = Files.createDirectory(root.resolve(
                        "process-" + ProcessHandle.current().pid() + "-" + UUID.randomUUID()));
                FileChannel owner = FileChannel.open(directory.resolve(OWNER_LOCK),
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                try {
                    lock = owner.lock();
                    channel = owner;
                } catch (IOException | RuntimeException | Error e) {
                    owner.close();
                    throw e;
                }
            }
        }
    }

    Path createCache() throws IOException {
        return Files.createTempFile(directory, "codearea-cache-", ".bin");
    }

    Path directory() { return directory; }

    private static void cleanup(Path root) throws IOException {
        try (var directories = Files.newDirectoryStream(root, "process-*")) {
            for (Path candidate : directories) {
                String name = candidate.getFileName().toString();
                if (!name.matches("process-[0-9]+-[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                        || !Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    cleanupOrphan(candidate);
                } catch (IOException | OverlappingFileLockException e) {
                    // Busy/inaccessible/unknown directories are never grounds for speculative deletion.
                    if (!(e instanceof OverlappingFileLockException)) {
                        System.err.println("Cannot inspect/remove orphan mapped cache: " + candidate + ": " + e);
                    }
                }
            }
        }
    }

    private static void cleanupOrphan(Path directory) throws IOException {
        var caches = new ArrayList<Path>();
        // Do not recurse, follow links, or remove unrelated files even inside a matching directory.
        try (var entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) return;
                if (name.equals(OWNER_LOCK)) continue;
                if (!name.startsWith("codearea-cache-") || !name.endsWith(".bin")) return;
                caches.add(entry);
            }
        }
        Path ownerPath = directory.resolve(OWNER_LOCK);
        try (FileChannel owner = FileChannel.open(ownerPath, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            try (FileLock orphan = owner.tryLock()) {
                if (orphan == null) return;
                for (Path cache : caches) Files.deleteIfExists(cache);
            }
        }
        // Close first for Windows. The root coordinator still excludes other startup cleanups.
        Files.deleteIfExists(ownerPath);
        Files.delete(directory);
    }

    // Production keeps the owner locked for the JVM lifetime, including retained mapping leases.
    // Closing is used only by isolated tests to simulate a process that has exited.
    @Override public void close() throws IOException {
        try {
            lock.release();
        } finally {
            channel.close();
        }
    }
}
