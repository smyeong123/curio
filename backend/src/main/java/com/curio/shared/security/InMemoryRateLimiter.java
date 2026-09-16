package com.curio.shared.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reusable per-key token-bucket limiter for authenticated, app-layer throttling
 * (e.g. BYOK validate / re-auth) where the IP-based {@link RateLimitingFilter}
 * (which only guards {@code /api/v1/auth/}) does not reach.
 *
 * <p>Buckets are held in-process keyed by an arbitrary string (typically
 * {@code scope:userId}). The first call for a key fixes its capacity/refill;
 * later calls with the same key reuse that bucket regardless of the limits
 * passed, so always use a stable {@code (capacity, refill)} per scope.
 *
 * <p>Like {@link RateLimitingFilter}, idle buckets are swept periodically so the
 * map can't grow without bound. NOTE: per-instance state — multiple replicas
 * need a shared store (e.g. Redis-backed Bucket4j).
 */
@Component
public class InMemoryRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRateLimiter.class);

    /** A bucket untouched this long is dropped; by then it has fully refilled anyway. */
    private static final Duration IDLE_TTL = Duration.ofMinutes(30);

    private final Map<String, Entry> buckets = new ConcurrentHashMap<>();

    private static final class Entry {
        final Bucket bucket;
        volatile long lastAccessNanos;
        Entry(Bucket bucket, long nowNanos) {
            this.bucket = bucket;
            this.lastAccessNanos = nowNanos;
        }
    }

    /**
     * Try to consume one token from the bucket identified by {@code key}.
     *
     * @return {@code true} if allowed, {@code false} if the bucket is exhausted.
     */
    public boolean tryConsume(String key, int capacity, Duration refill) {
        Entry entry = buckets.computeIfAbsent(key,
                k -> new Entry(newBucket(capacity, refill), System.nanoTime()));
        entry.lastAccessNanos = System.nanoTime();
        return entry.bucket.tryConsume(1);
    }

    private Bucket newBucket(int capacity, Duration refill) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, refill)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    void evictIdleBuckets() {
        long cutoff = System.nanoTime() - IDLE_TTL.toNanos();
        int before = buckets.size();
        buckets.values().removeIf(e -> e.lastAccessNanos < cutoff);
        int removed = before - buckets.size();
        if (removed > 0) {
            log.debug("InMemoryRateLimiter eviction: removed {} idle buckets, {} remain",
                    removed, buckets.size());
        }
    }
}
