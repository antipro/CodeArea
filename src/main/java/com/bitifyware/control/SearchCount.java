package com.bitifyware.control;

import java.util.ConcurrentModificationException;
import java.util.OptionalLong;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Pollable asynchronous count. UNKNOWN is suitable for rendering as '?'. */
public final class SearchCount implements AutoCloseable {
    public enum State { UNKNOWN, COMPLETE, STALE, CANCELLED, FAILED }
    public record Result(State state, OptionalLong count, long version, Throwable failure) { }
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "codearea-search-count");
        thread.setDaemon(true);
        return thread;
    });
    private final TextSource source;
    private final long version;
    private final AtomicReference<Result> result;
    private final CompletableFuture<Result> completion = new CompletableFuture<>();
    private final Future<?> task;

    SearchCount(SearchCursor cursor) {
        this(cursor, WORKERS);
    }

    SearchCount(SearchCursor cursor, Executor executor) {
        source = cursor.source();
        version = cursor.version();
        result = new AtomicReference<>(new Result(State.UNKNOWN, OptionalLong.empty(), version, null));
        FutureTask<Void> work = new FutureTask<>(() -> {
            try (cursor) {
                long count = 0;
                while (cursor.next().isPresent()) count++;
                synchronized (source) {
                    source.checkOpen();
                    if (source.version() != version) throw new ConcurrentModificationException();
                }
                finish(new Result(State.COMPLETE, OptionalLong.of(count), version, null));
            } catch (CancellationException cancelled) {
                finish(new Result(State.CANCELLED, OptionalLong.empty(), version, null));
            } catch (ConcurrentModificationException | IllegalStateException stale) {
                finish(new Result(State.STALE, OptionalLong.empty(), version, null));
            } catch (Throwable failure) {
                finish(new Result(State.FAILED, OptionalLong.empty(), version, failure));
            }
        }, null);
        task = work;
        try {
            executor.execute(work);
        } catch (RuntimeException | Error rejected) {
            cursor.close();
            throw rejected;
        }
    }

    private void finish(Result value) {
        Result previous = result.get();
        if (previous.state() == State.UNKNOWN && result.compareAndSet(previous, value)) {
            completion.complete(value);
        }
    }

    /** Rechecks the version even after completion; results never silently remain valid after an edit. */
    public Result result() {
        Result value;
        boolean invalidated = false;
        synchronized (source) {
            value = result.get();
            if (value.state() != State.CANCELLED && value.state() != State.STALE) {
                try {
                    source.checkOpen();
                    if (source.version() != version) throw new ConcurrentModificationException();
                } catch (IllegalStateException | ConcurrentModificationException stale) {
                    value = new Result(State.STALE, OptionalLong.empty(), version, null);
                    result.set(value);
                    invalidated = true;
                }
            }
        }
        // Never invoke completion callbacks while holding the model monitor.
        if (invalidated) {
            completion.complete(value);
            task.cancel(true);
        }
        return value;
    }

    /** Callbacks may run on a worker (or the registering thread if already complete).
     * UI callers must dispatch to the FX thread and recheck result(). */
    public CompletionStage<Result> completion() { return completion.minimalCompletionStage(); }

    @Override public void close() {
        Result cancelled = new Result(State.CANCELLED, OptionalLong.empty(), version, null);
        result.updateAndGet(old -> old.state() == State.UNKNOWN ? cancelled : old);
        completion.complete(result.get());
        task.cancel(true);
    }
}
