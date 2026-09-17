package com.curio.shared.batch;

import com.curio.shared.exception.RootCauses;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Runs one task per delivery-enabled subscriber, the way every batch in Curio
 * needs to: page through users in bounded chunks (never the whole table), keep
 * at most {@link Spec#maxInFlight()} tasks submitted at once so a user's timeout
 * clock starts when their task is about to run rather than when it was queued,
 * wait for the chunk before loading the next one, and count each user exactly
 * once — the first of the task's own completion and the timeout handler to
 * arrive wins the count, the other only logs.
 *
 * <p>Used by the scheduled digest and email jobs and by the admin-triggered
 * runs, so their counters, error sampling and timeouts stay identical.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriberBatch {

    public static final int CHUNK_SIZE = 500;

    private final UserPort userPort;

    /** How one user's task ended. */
    public enum Status { SUCCESS, SKIPPED, FAILED }

    /**
     * A failure's message is sampled for the admin UI and bucketed by its prefix
     * (the text before the first colon). A skip's reason only reaches the debug
     * log: skips are counted, never alerted on.
     */
    public record Outcome(Status status, String message) {
        public static Outcome success() {
            return new Outcome(Status.SUCCESS, null);
        }

        public static Outcome skipped() {
            return new Outcome(Status.SKIPPED, null);
        }

        public static Outcome skipped(String reason) {
            return new Outcome(Status.SKIPPED, reason);
        }

        public static Outcome failed(String message) {
            return new Outcome(Status.FAILED, message);
        }
    }

    /**
     * Execution limits for one run. {@code perUserTimeout} bounds a single task
     * from the moment it is submitted; {@code maxInFlight} caps how many tasks are
     * submitted but not yet finished, which is what keeps that clock honest;
     * {@code sampleLimit} caps how many per-user failure messages are kept for
     * the admin UI.
     */
    public record Spec(String jobName, Executor executor, Duration perUserTimeout, int maxInFlight, int sampleLimit) {

        /**
         * Submissions kept ahead of the pool's threads: enough that a thread which
         * finishes finds the next task already queued, few enough that a queued
         * task waits behind at most a handful of running ones.
         */
        static final int IN_FLIGHT_HEADROOM = 2;

        /** A spec sized to the pool the tasks will run on. */
        public static Spec forPool(String jobName, ThreadPoolTaskExecutor pool, Duration perUserTimeout, int sampleLimit) {
            return new Spec(jobName, pool, perUserTimeout, pool.getMaxPoolSize() + IN_FLIGHT_HEADROOM, sampleLimit);
        }
    }

    /** Thread-safe per-run counters. */
    public static final class Tally {
        private final AtomicInteger success = new AtomicInteger();
        private final AtomicInteger skipped = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();
        private final AtomicInteger included = new AtomicInteger();
        private final List<Map<String, String>> sampleErrors = new CopyOnWriteArrayList<>();
        private final ConcurrentHashMap<String, Integer> errorsByType = new ConcurrentHashMap<>();
        private final List<ChunkFailure> chunkFailures = new CopyOnWriteArrayList<>();
        private final String jobName;
        private final int sampleLimit;
        private long scanned;
        private int chunks;

        Tally(String jobName, int sampleLimit) {
            this.jobName = jobName;
            this.sampleLimit = sampleLimit;
        }

        public int success() { return success.get(); }
        public int skipped() { return skipped.get(); }
        public int failed() { return failed.get(); }
        /** Users that passed the include filter, i.e. were attempted (their task ran). */
        public int included() { return included.get(); }
        /** Every delivery-enabled user paged through, including those the filter excluded. */
        public long scanned() { return scanned; }
        public int chunks() { return chunks; }
        public List<ChunkFailure> chunkFailures() { return chunkFailures; }

        /** A chunk whose preload hook threw; none of its users were attempted. */
        public record ChunkFailure(int chunkIndex, Exception cause) { }

        void record(User user, Outcome outcome) {
            switch (outcome.status()) {
                case SUCCESS -> success.incrementAndGet();
                case SKIPPED -> {
                    skipped.incrementAndGet();
                    if (outcome.message() != null) {
                        log.debug("{} skipped user {}: {}", jobName, user.getId(), outcome.message());
                    }
                }
                case FAILED -> {
                    failed.incrementAndGet();
                    String message = outcome.message() != null ? outcome.message() : "failed";
                    errorsByType.merge(message.split(":", 2)[0].trim(), 1, Integer::sum);
                    if (sampleErrors.size() < sampleLimit) {
                        Map<String, String> sample = new LinkedHashMap<>();
                        sample.put("userEmail", user.getEmail());
                        sample.put("message", message);
                        sampleErrors.add(sample);
                    }
                }
            }
        }

        /**
         * The fields every batch's job-status record carries — the admin dashboard
         * reads them by these exact names — plus the failure detail it renders when
         * something went wrong.
         */
        public Map<String, Object> commonFields(long durationMs) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("durationMs", durationMs);
            fields.put("usersProcessed", included.get());
            fields.put("usersScanned", scanned);
            fields.put("chunks", chunks);
            fields.put("chunkSize", CHUNK_SIZE);
            if (!sampleErrors.isEmpty()) {
                fields.put("sampleErrors", new ArrayList<>(sampleErrors));
            }
            if (!errorsByType.isEmpty()) {
                fields.put("errorsByType", new LinkedHashMap<>(errorsByType));
            }
            return fields;
        }
    }

    /**
     * @param onChunk optional hook run once per loaded chunk before its tasks
     *                start — for batch-loading per-user data (avoids N+1 lookups).
     *                If it throws, that chunk is recorded as a {@link Tally.ChunkFailure}
     *                and skipped; the run continues with the next chunk
     * @param include which users of the chunk get a task at all
     * @param task    the per-user work; a thrown exception counts as FAILED with
     *                the root cause as the sampled message
     */
    public Tally run(Spec spec, Consumer<List<User>> onChunk, Predicate<User> include, Function<User, Outcome> task) {
        Tally tally = new Tally(spec.jobName(), spec.sampleLimit());
        Semaphore inFlight = new Semaphore(spec.maxInFlight());
        int pageIndex = 0;
        Page<User> page;
        do {
            page = userPort.findByDeliveryEnabledTrue(PageRequest.of(pageIndex, CHUNK_SIZE, Sort.by("id")));
            List<User> chunk = page.getContent();
            if (chunk.isEmpty()) {
                break;
            }
            log.info("{} chunk {} ({} users, total so far {})", spec.jobName(), pageIndex, chunk.size(), tally.scanned);
            if (preload(spec, tally, pageIndex, chunk, onChunk)) {
                runChunk(spec, tally, inFlight, chunk, include, task);
            }
            tally.scanned += chunk.size();
            tally.chunks++;
            pageIndex++;
        } while (page.hasNext());
        return tally;
    }

    /** @return whether the chunk's tasks may run; false records the failure and skips the chunk */
    private static boolean preload(Spec spec, Tally tally, int pageIndex, List<User> chunk, Consumer<List<User>> onChunk) {
        if (onChunk == null) {
            return true;
        }
        try {
            onChunk.accept(chunk);
            return true;
        } catch (Exception e) {
            log.error("{} chunk {} preload failed — skipping its {} users", spec.jobName(), pageIndex, chunk.size(), e);
            tally.chunkFailures.add(new Tally.ChunkFailure(pageIndex, e));
            return false;
        }
    }

    private static void runChunk(Spec spec, Tally tally, Semaphore inFlight, List<User> chunk,
                                 Predicate<User> include, Function<User, Outcome> task) {
        List<CompletableFuture<Void>> futures = new ArrayList<>(chunk.size());
        for (User user : chunk) {
            if (!include.test(user)) {
                continue;
            }
            tally.included.incrementAndGet();
            acquireSlot(inFlight, spec);
            AtomicBoolean counted = new AtomicBoolean(false);
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    Outcome outcome;
                    try {
                        outcome = task.apply(user);
                    } catch (Exception e) {
                        String message = RootCauses.describe(e);
                        log.error("{} failed for user {}: {}", spec.jobName(), user.getId(), message);
                        outcome = Outcome.failed(message);
                    }
                    if (counted.compareAndSet(false, true)) {
                        tally.record(user, outcome);
                    } else {
                        log.warn("{} task for user {} completed after being counted as timed out",
                                spec.jobName(), user.getId());
                    }
                } finally {
                    // A timed-out task keeps its slot until it really finishes, so the
                    // pool never holds more than maxInFlight tasks, counted or not.
                    inFlight.release();
                }
            }, spec.executor())
                    .orTimeout(spec.perUserTimeout().toMillis(), TimeUnit.MILLISECONDS)
                    // orTimeout completes the future outside the task's own error
                    // handling — without this a timed-out user is counted nowhere.
                    .exceptionally(ex -> {
                        if (counted.compareAndSet(false, true)) {
                            tally.record(user, Outcome.failed("timed out: " + RootCauses.describe(ex)));
                            log.error("{} task timed out or failed for user {}", spec.jobName(), user.getId(), ex);
                        }
                        return null;
                    }));
        }
        // Every future resolves within perUserTimeout of its own submission, so
        // this wait is bounded without a separate chunk clock.
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /** Blocks until a task slot is free; an interrupt (shutdown) aborts the run rather than leaking it. */
    private static void acquireSlot(Semaphore inFlight, Spec spec) {
        try {
            inFlight.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(spec.jobName() + " interrupted while waiting for a free task slot", e);
        }
    }
}
