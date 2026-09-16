package com.curio.shared.concurrent;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * In-process single-flight: serializes concurrent work that shares a key so the
 * expensive body runs once per key while other callers wait for the winner.
 *
 * <p>Motivating case: the daily {@code DigestGenerationJob} fans many users out
 * across {@code digestExecutor}. Without coordination, every user who shares a
 * topic that isn't cached yet fires its own (cold-cache) Claude/Gemini/OpenAI
 * call for that topic — up to one duplicate per executor thread. Wrapping the
 * cache-miss → generate → cache-put sequence in {@link #call} collapses those to
 * a single API call; the rest re-check the cache under the lock and hit it.
 *
 * <p>Scope is per-JVM. The deployment runs a single backend container, so this
 * fully de-duplicates the herd. If the app is ever scaled to multiple instances,
 * promote this to a distributed lock (e.g. Redis {@code SETNX}); the call sites
 * would not need to change.
 *
 * <p>Locks are interned per key and reference-counted: a key's lease is removed
 * only once the last referencing thread releases it, so the map stays bounded as
 * date-stamped keys roll over daily. Because both acquire (refcount increment) and
 * release/eviction happen inside the same per-key {@code compute} critical section,
 * a lease can never be evicted while another thread is between acquiring it and
 * locking it — which would otherwise let two threads run the same key under
 * different locks.
 */
@Component
public class SingleFlight {

    /** Per-key lock plus a reference count guarding its lifecycle. */
    private static final class Lease {
        final ReentrantLock lock = new ReentrantLock();
        int refs;
    }

    private final ConcurrentHashMap<String, Lease> locks = new ConcurrentHashMap<>();

    /**
     * Runs {@code work} while holding the lock for {@code key}. Concurrent callers
     * with the same key run one at a time; different keys never block each other.
     * The supplier should itself re-check any cache first (double-checked locking)
     * so the threads that waited return the winner's result instead of recomputing.
     */
    public <T> T call(String key, Supplier<T> work) {
        // Acquire a reference to the key's lease under the map's per-key lock so the
        // lease we obtain cannot be evicted before we lock it.
        Lease lease = locks.compute(key, (k, v) -> {
            if (v == null) {
                v = new Lease();
            }
            v.refs++;
            return v;
        });
        lease.lock.lock();
        try {
            return work.get();
        } finally {
            lease.lock.unlock();
            // Release our reference; evict the lease only when no thread references it
            // anymore. Decrement happens in the same critical section as acquire, so
            // eviction never races an acquirer that hasn't locked yet.
            locks.compute(key, (k, v) -> (v == null || --v.refs == 0) ? null : v);
        }
    }
}
