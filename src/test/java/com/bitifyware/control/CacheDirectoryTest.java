package com.bitifyware.control;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class CacheDirectoryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void cleansReleasedOwnersButPreservesSameJvmLiveOwner() throws Exception {
        Path root = temporary.newFolder("mapfile").toPath();
        Path orphan;
        try (CacheDirectory exited = new CacheDirectory(root)) {
            orphan = exited.createCache();
            Files.writeString(orphan, "orphan");
        }
        try (CacheDirectory live = new CacheDirectory(root)) {
            assertFalse(Files.exists(orphan.getParent()));
            Path active = live.createCache();
            try (CacheDirectory another = new CacheDirectory(root)) {
                assertTrue(Files.exists(active));
                assertNotEquals(live.directory(), another.directory());
            }
        }
    }

    @Test public void preservesUnknownFilesAndUnrelatedDirectories() throws Exception {
        Path root = temporary.newFolder("mapfile").toPath();
        Path unrelated = Files.createDirectory(root.resolve("other"));
        Path data = Files.writeString(unrelated.resolve("important.bin"), "user data");
        Path unknown = Files.createDirectory(root.resolve("process-123-" + UUID.randomUUID()));
        Path unknownFile = Files.writeString(unknown.resolve("keep.txt"), "unknown");
        Path partial = Files.createDirectory(root.resolve("process-456-" + UUID.randomUUID()));
        Files.writeString(partial.resolve("codearea-cache-partial.bin"), "crashed before lock publication");
        try (CacheDirectory owner = new CacheDirectory(root)) {
            assertTrue(Files.exists(data));
            assertTrue(Files.exists(unknownFile));
            assertFalse(Files.exists(partial));
        }
    }

    @Test public void cleanupNeverFollowsSymbolicLinks() throws Exception {
        Path root = temporary.newFolder("mapfile").toPath();
        Path outside = temporary.newFolder("outside").toPath();
        Path external = Files.writeString(outside.resolve("codearea-cache-user.bin"), "user data");
        Path linkedDirectory = root.resolve("process-789-" + UUID.randomUUID());
        try {
            Files.createSymbolicLink(linkedDirectory, outside);
        } catch (UnsupportedOperationException | java.io.IOException e) {
            org.junit.Assume.assumeNoException("Symbolic links unavailable", e);
        }
        Path owner = Files.createDirectory(root.resolve("process-987-" + UUID.randomUUID()));
        Path linkedFile = Files.createSymbolicLink(owner.resolve("codearea-cache-linked.bin"), external);
        try (CacheDirectory current = new CacheDirectory(root)) {
            assertTrue(Files.isSymbolicLink(linkedDirectory));
            assertTrue(Files.isSymbolicLink(linkedFile));
            assertEquals("user data", Files.readString(external));
        }
    }

    @Test public void liveProcessIsProtectedAndForcedExitIsCleanedOnNextStartup() throws Exception {
        Path root = temporary.newFolder("mapfile").toPath();
        Path ready = temporary.getRoot().toPath().resolve("ready");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = Path.of(CacheDirectoryProcess.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI()) + File.pathSeparator
                + Path.of(CacheDirectory.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Process process = new ProcessBuilder(java, "-cp", classpath, CacheDirectoryProcess.class.getName(),
                root.toString(), ready.toString()).redirectErrorStream(true)
                .redirectOutput(temporary.getRoot().toPath().resolve("child.log").toFile()).start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!Files.exists(ready) && process.isAlive() && System.nanoTime() < deadline) Thread.sleep(20);
            assertTrue("Child must publish a locked cache", Files.exists(ready));
            Path active = Path.of(Files.readString(ready));
            try (CacheDirectory another = new CacheDirectory(root)) {
                assertTrue("Other JVM cache must survive startup cleanup", Files.exists(active));
            }
            process.destroyForcibly();
            assertTrue(process.waitFor(15, TimeUnit.SECONDS));
            assertTrue("OS exit releases the lock, not the file", Files.exists(active));
            try (CacheDirectory restarted = new CacheDirectory(root)) {
                assertFalse("Next startup removes the crashed owner's directory", Files.exists(active.getParent()));
            }
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(15, TimeUnit.SECONDS);
            }
        }
    }
}
