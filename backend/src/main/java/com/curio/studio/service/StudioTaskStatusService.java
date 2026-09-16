package com.curio.studio.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks the live status of a single user's on-demand Digest Studio tasks
 * (digest generation, email send) in Redis, so the status snapshot survives
 * across the polling requests and across a server restart. Mirrors
 * {@code JobStatusRegistry} but is scoped per-user and per-task-type rather than
 * per scheduled job.
 *
 * Note: it is the status <em>snapshot</em> that survives a restart, not the
 * running task itself. A task interrupted by a crash/restart leaves a stale
 * QUEUED/RUNNING record behind; {@link #isActive} treats such a record as
 * inactive (see {@link #STALE_THRESHOLD}) so a fresh run can start rather than
 * the user being wedged until the record's TTL expires.
 *
 * Key pattern: {@code studio:task:{type}:{userId}} for the status snapshot,
 * {@code studio:task:lock:{type}:{userId}} for the double-start mutex.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudioTaskStatusService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_PREFIX = "studio:task:";
    /** Status is ephemeral — drop it a couple of hours after the run finishes. */
    private static final Duration TTL = Duration.ofHours(2);
    /**
     * A QUEUED/RUNNING record not touched within this window is assumed orphaned
     * (worker crashed or the server restarted mid-run) and no longer counts as
     * active. Chosen generously: there are heartbeat-free stretches (the "Writing
     * your quiz…" phase, the final AI/email calls) where a legitimately-running
     * task doesn't write for a while, so a too-short bound risks a second run.
     */
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(10);
    /**
     * TTL on the double-start mutex. Bounded so a crashed worker that never hit
     * its {@code finally} release can't deadlock the slot forever. MUST equal
     * {@link #STALE_THRESHOLD}: the moment {@link #isActive} declares a run dead,
     * {@link #tryAcquire} has to be willing to hand out the slot again — a longer
     * lock TTL would leave a window where the UI says "not running" but every
     * start silently no-ops until the lock expires.
     */
    private static final Duration LOCK_TTL = STALE_THRESHOLD;

    public enum TaskType {
        DIGEST("digest"),
        EMAIL("email");

        private final String slug;
        TaskType(String slug) { this.slug = slug; }
        public String slug() { return slug; }
    }

    /** Lifecycle states a task moves through. */
    public static final String IDLE = "IDLE";
    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String SKIPPED = "SKIPPED";
    public static final String FAILED = "FAILED";

    private String key(UUID userId, TaskType type) {
        return KEY_PREFIX + type.slug() + ":" + userId;
    }

    public void markQueued(UUID userId, TaskType type) {
        Map<String, Object> record = baseRecord(userId, type, QUEUED);
        record.put("phase", "Queued…");
        record.put("startedAt", LocalDateTime.now().toString());
        write(userId, type, record);
    }

    public void markRunning(UUID userId, TaskType type, String phase, int current, int total) {
        Map<String, Object> record = baseRecord(userId, type, RUNNING);
        record.put("phase", phase);
        record.put("current", current);
        record.put("total", total);
        preserveStartedAt(userId, type, record);
        write(userId, type, record);
    }

    public void markSuccess(UUID userId, TaskType type, String message, Map<String, Object> extra) {
        finish(userId, type, SUCCESS, message, extra);
    }

    public void markSkipped(UUID userId, TaskType type, String message) {
        finish(userId, type, SKIPPED, message, null);
    }

    public void markFailed(UUID userId, TaskType type, String message) {
        finish(userId, type, FAILED, message, null);
    }

    private void finish(UUID userId, TaskType type, String state, String message, Map<String, Object> extra) {
        Map<String, Object> record = baseRecord(userId, type, state);
        record.put("message", message);
        record.put("finishedAt", LocalDateTime.now().toString());
        preserveStartedAt(userId, type, record);
        if (extra != null) {
            record.putAll(extra);
        }
        write(userId, type, record);
    }

    /**
     * True while a task is genuinely queued or running — used as a fast
     * idempotency check before the {@link #tryAcquire} mutex. A QUEUED/RUNNING
     * record whose {@code updatedAt} is older than {@link #STALE_THRESHOLD} is
     * treated as a zombie (worker died / server restarted mid-run) and reported
     * as inactive so a fresh run can start.
     */
    public boolean isActive(UUID userId, TaskType type) {
        Map<String, Object> status = get(userId, type);
        String state = (String) status.get("state");
        if (!QUEUED.equals(state) && !RUNNING.equals(state)) {
            return false;
        }
        Object updatedAt = status.get("updatedAt");
        if (updatedAt instanceof String s) {
            try {
                LocalDateTime updated = LocalDateTime.parse(s);
                if (updated.isBefore(LocalDateTime.now().minus(STALE_THRESHOLD))) {
                    return false; // stale record — no worker is actually running
                }
            } catch (Exception ignored) {
                // unparseable timestamp — err on the side of "active"
            }
        }
        return true;
    }

    private String lockKey(UUID userId, TaskType type) {
        return KEY_PREFIX + "lock:" + type.slug() + ":" + userId;
    }

    /**
     * Atomically claim the run slot for this user+task. Backed by a Redis
     * {@code SET key val NX EX}, so of two near-simultaneous starts only one
     * acquires — closing the TOCTOU window that {@link #isActive} alone leaves
     * open (double digest generation / double email). The caller MUST
     * {@link #release} in a {@code finally}. Fails open if Redis is unreachable:
     * without Redis the whole status system is degraded anyway, so we'd rather let
     * the user run than wedge them. That fail-open is only safe because actual
     * duplicate side effects are prevented at the DB layer regardless — the V23
     * one-digest-per-user-per-day unique index for generation, and the atomic
     * {@code claimForEmailSend} conditional UPDATE for sends. Don't remove those
     * backstops without revisiting this.
     */
    public boolean tryAcquire(UUID userId, TaskType type) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.opsForValue().setIfAbsent(lockKey(userId, type), "1", LOCK_TTL));
        } catch (Exception e) {
            log.warn("Failed to acquire studio task lock for user {} ({}): {} — allowing start",
                    userId, type.slug(), e.getMessage());
            return true;
        }
    }

    /** Release the run slot claimed by {@link #tryAcquire}. Best-effort. */
    public void release(UUID userId, TaskType type) {
        try {
            redisTemplate.delete(lockKey(userId, type));
        } catch (Exception e) {
            log.warn("Failed to release studio task lock for user {} ({}): {}",
                    userId, type.slug(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> get(UUID userId, TaskType type) {
        try {
            Object value = redisTemplate.opsForValue().get(key(userId, type));
            if (value instanceof Map) {
                return (Map<String, Object>) value;
            }
        } catch (Exception e) {
            log.warn("Failed to read studio task status for user {} ({}): {}",
                    userId, type.slug(), e.getMessage());
        }
        Map<String, Object> idle = baseRecord(userId, type, IDLE);
        idle.put("phase", null);
        return idle;
    }

    private Map<String, Object> baseRecord(UUID userId, TaskType type, String state) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("type", type.slug());
        record.put("state", state);
        record.put("updatedAt", LocalDateTime.now().toString());
        return record;
    }

    private void preserveStartedAt(UUID userId, TaskType type, Map<String, Object> record) {
        if (record.containsKey("startedAt")) return;
        try {
            Object existing = redisTemplate.opsForValue().get(key(userId, type));
            if (existing instanceof Map<?, ?> m && m.get("startedAt") != null) {
                record.put("startedAt", m.get("startedAt"));
            }
        } catch (Exception ignored) {
            // best-effort; startedAt is cosmetic
        }
    }

    private void write(UUID userId, TaskType type, Map<String, Object> record) {
        try {
            redisTemplate.opsForValue().set(key(userId, type), record, TTL);
        } catch (Exception e) {
            log.warn("Failed to persist studio task status for user {} ({}): {}",
                    userId, type.slug(), e.getMessage());
        }
    }
}
