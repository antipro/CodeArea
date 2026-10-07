package com.bitifyware.control;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.Cleaner;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Append-only UTF-16 cache. FFM arenas, rather than GC, own the mappings. */
final class MappedCache implements AutoCloseable {
    private static final Cleaner CLEANER = Cleaner.create();
    private static final int CHUNK_BYTES = 1024 * 1024;
    private static final int CHUNK_CHARS = CHUNK_BYTES / Character.BYTES;
    private final State state;
    private final Cleaner.Cleanable cleanable;
    private long nextChar;

    MappedCache() {
        try {
            state = new State();
            cleanable = CLEANER.register(this, state);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    long append(CharSequence text) {
        checkOpen();
        long start = nextChar;
        for (int i = 0; i < text.length(); i++) {
            int chunk = Math.toIntExact(nextChar / CHUNK_CHARS);
            if (chunk == state.buffers.size()) {
                state.mapChunk();
            }
            state.buffers.get(chunk).putChar((int) (nextChar % CHUNK_CHARS) * 2, text.charAt(i));
            nextChar++;
        }
        return start;
    }

    String read(long start, int length) {
        checkOpen();
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            long pos = start + i;
            chars[i] = state.buffers.get(Math.toIntExact(pos / CHUNK_CHARS))
                    .getChar((int) (pos % CHUNK_CHARS) * 2);
        }
        return new String(chars);
    }

    Path path() {
        return state.path;
    }

    void checkOpen() {
        if (state.closed) {
            throw new IllegalStateException("Content cache is closed");
        }
    }

    @Override public void close() {
        cleanable.clean();
    }

    /* JDK 21 exposes FFM as preview, JDK 22+ as final. Reflect only at the API
       boundary so the library remains ordinary Java 21 bytecode, usable on both
       without enabling preview in every application/plugin that depends on it.
       This invokes FileChannel.map(..., Arena), not the legacy map overload. */
    private static final class Foreign {
        static final Method OF_SHARED;
        static final Method MAP;
        static final Method AS_BUFFER;
        static final Method CLOSE;
        static {
            try {
                Class<?> arena = Class.forName("java.lang.foreign.Arena");
                Class<?> segment = Class.forName("java.lang.foreign.MemorySegment");
                OF_SHARED = arena.getMethod("ofShared");
                CLOSE = arena.getMethod("close");
                MAP = FileChannel.class.getMethod("map", FileChannel.MapMode.class,
                        long.class, long.class, arena);
                AS_BUFFER = segment.getMethod("asByteBuffer");
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        static Object invoke(Method method, Object receiver, Object... args) {
            try {
                return method.invoke(receiver, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException io) throw new UncheckedIOException(io);
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw new IllegalStateException(cause);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static final class State implements Runnable {
        final Path path;
        final FileChannel channel;
        final List<Object> arenas = new ArrayList<>();
        final List<ByteBuffer> buffers = new ArrayList<>();
        boolean closed;

        State() throws IOException {
            path = Files.createTempFile("codearea-cache-", ".bin");
            try {
                channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE);
            } catch (IOException e) {
                Files.deleteIfExists(path);
                throw e;
            }
        }

        void mapChunk() {
            Object arena = Foreign.invoke(Foreign.OF_SHARED, null);
            try {
                long offset = (long) buffers.size() * CHUNK_BYTES;
                channel.write(ByteBuffer.wrap(new byte[1]), offset + CHUNK_BYTES - 1);
                Object segment = Foreign.invoke(Foreign.MAP, channel,
                        FileChannel.MapMode.READ_WRITE, offset, (long) CHUNK_BYTES, arena);
                buffers.add((ByteBuffer) Foreign.invoke(Foreign.AS_BUFFER, segment));
                arenas.add(arena);
            } catch (IOException | RuntimeException | Error e) {
                Foreign.invoke(Foreign.CLOSE, arena);
                if (e instanceof IOException io) throw new UncheckedIOException(io);
                if (e instanceof RuntimeException runtime) throw runtime;
                throw (Error) e;
            }
        }

        @Override public void run() {
            if (closed) return;
            closed = true;
            try {
                for (Object arena : arenas) Foreign.invoke(Foreign.CLOSE, arena);
            } finally {
                buffers.clear();
                arenas.clear();
                try {
                    channel.close();
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    System.err.println("Cannot remove CodeArea cache: " + path);
                }
            }
        }
    }
}
