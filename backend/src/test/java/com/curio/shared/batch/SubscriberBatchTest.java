package com.curio.shared.batch;

import com.curio.shared.batch.SubscriberBatch.Outcome;
import com.curio.shared.batch.SubscriberBatch.Spec;
import com.curio.shared.batch.SubscriberBatch.Tally;
import com.curio.user.entity.User;
import com.curio.user.port.out.UserPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriberBatchTest {

    @Mock private UserPort userPort;

    private static final Executor INLINE = Runnable::run;

    private static User user(String email) {
        return User.builder().id(UUID.randomUUID()).email(email).build();
    }

    private static Spec spec(Executor executor, Duration perUser) {
        return new Spec("test-job", executor, perUser, 4, 2);
    }

    private static Outcome sleepThenSucceed(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return Outcome.success();
    }

    @Test
    void countsEachOutcomeOnce_andSamplesFailures() {
        User ok = user("ok@example.com");
        User skip = user("skip@example.com");
        User boom = user("boom@example.com");
        User boom2 = user("boom2@example.com");
        User boom3 = user("boom3@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ok, skip, boom, boom2, boom3)));

        Tally tally = new SubscriberBatch(userPort).run(spec(INLINE, Duration.ofSeconds(1)), null, u -> true, u -> {
            if (u == ok) return Outcome.success();
            if (u == skip) return Outcome.skipped("nothing to do");
            if (u == boom) throw new IllegalStateException("provider down: 503");
            return Outcome.failed("RuntimeException: circuit open");
        });

        assertThat(tally.success()).isEqualTo(1);
        assertThat(tally.skipped()).isEqualTo(1);
        assertThat(tally.failed()).isEqualTo(3);
        assertThat(tally.included()).isEqualTo(5);
        assertThat(tally.scanned()).isEqualTo(5L);
        assertThat(tally.chunks()).isEqualTo(1);

        Map<String, Object> fields = tally.commonFields(42L);
        assertThat(fields).containsEntry("usersProcessed", 5).containsEntry("usersScanned", 5L)
                .containsEntry("chunks", 1).containsEntry("chunkSize", SubscriberBatch.CHUNK_SIZE)
                .containsEntry("durationMs", 42L);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> samples = (List<Map<String, String>>) fields.get("sampleErrors");
        assertThat(samples).hasSize(2); // sampleLimit
        assertThat(samples.get(0)).containsEntry("userEmail", "boom@example.com")
                .containsEntry("message", "IllegalStateException: provider down: 503");
        @SuppressWarnings("unchecked")
        Map<String, Integer> byType = (Map<String, Integer>) fields.get("errorsByType");
        assertThat(byType).containsEntry("RuntimeException", 2);
        assertThat(byType.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);
    }

    @Test
    void includeFilter_skipsUsersEntirely_andOnChunkSeesTheWholeChunk() {
        User in = user("in@example.com");
        User out = user("out@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(in, out)));
        List<List<User>> chunks = new ArrayList<>();
        List<User> ran = new ArrayList<>();

        Tally tally = new SubscriberBatch(userPort).run(spec(INLINE, Duration.ofSeconds(1)), chunks::add,
                u -> u == in, u -> { ran.add(u); return Outcome.success(); });

        assertThat(chunks).containsExactly(List.of(in, out));
        assertThat(ran).containsExactly(in);
        assertThat(tally.included()).isEqualTo(1);
        assertThat(tally.scanned()).isEqualTo(2L);
        assertThat(tally.commonFields(0)).containsEntry("usersProcessed", 1).containsEntry("usersScanned", 2L);
    }

    @Test
    void pagesThroughEveryChunk() {
        User a = user("a@example.com");
        User b = user("b@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a), PageRequest.of(0, SubscriberBatch.CHUNK_SIZE), SubscriberBatch.CHUNK_SIZE + 1))
                .thenReturn(new PageImpl<>(List.of(b), PageRequest.of(1, SubscriberBatch.CHUNK_SIZE), SubscriberBatch.CHUNK_SIZE + 1));

        Tally tally = new SubscriberBatch(userPort).run(spec(INLINE, Duration.ofSeconds(1)), null, u -> true, u -> Outcome.success());

        assertThat(tally.chunks()).isEqualTo(2);
        assertThat(tally.success()).isEqualTo(2);
    }

    @Test
    void aFailingPreload_skipsThatChunk_andContinuesWithTheNext() {
        User a = user("a@example.com");
        User b = user("b@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a), PageRequest.of(0, SubscriberBatch.CHUNK_SIZE), SubscriberBatch.CHUNK_SIZE + 1))
                .thenReturn(new PageImpl<>(List.of(b), PageRequest.of(1, SubscriberBatch.CHUNK_SIZE), SubscriberBatch.CHUNK_SIZE + 1));
        AtomicInteger preloads = new AtomicInteger();
        List<User> ran = new ArrayList<>();

        Tally tally = new SubscriberBatch(userPort).run(spec(INLINE, Duration.ofSeconds(1)),
                chunk -> { if (preloads.getAndIncrement() == 0) throw new IllegalStateException("prefs query failed"); },
                u -> true, u -> { ran.add(u); return Outcome.success(); });

        assertThat(ran).containsExactly(b);
        assertThat(tally.chunkFailures()).hasSize(1);
        assertThat(tally.chunkFailures().get(0).chunkIndex()).isZero();
        assertThat(tally.chunkFailures().get(0).cause()).hasMessage("prefs query failed");
        assertThat(tally.chunks()).isEqualTo(2);
        assertThat(tally.scanned()).isEqualTo(2L);
        assertThat(tally.included()).isEqualTo(1);
        assertThat(tally.success()).isEqualTo(1);
    }

    @Test
    void aTimedOutUser_isCountedAsFailedExactlyOnce() throws Exception {
        User slow = user("slow@example.com");
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(slow)));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Tally tally = new SubscriberBatch(userPort).run(spec(pool, Duration.ofMillis(50)), null, u -> true,
                    u -> sleepThenSucceed(300));
            // The late completion must not add a success on top of the timeout failure.
            Thread.sleep(400);
            assertThat(tally.failed()).isEqualTo(1);
            assertThat(tally.success()).isZero();
            assertThat(tally.commonFields(0).get("errorsByType").toString()).contains("timed out");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aQueuedUsersClock_startsWhenTheirTaskCanRun_notWhenTheChunkWasSubmitted() {
        List<User> users = List.of(user("1@example.com"), user("2@example.com"), user("3@example.com"), user("4@example.com"));
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(users));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // One slot on one thread: each task takes 80ms, the timeout is 150ms. Timed
            // from chunk submission the last user would wait 240ms and be failed; timed
            // from their own submission every user finishes in time.
            Spec spec = new Spec("test-job", pool, Duration.ofMillis(150), 1, 2);
            Tally tally = new SubscriberBatch(userPort).run(spec, null, u -> true, u -> sleepThenSucceed(80));

            assertThat(tally.failed()).isZero();
            assertThat(tally.success()).isEqualTo(4);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void neverHasMoreThanMaxInFlightTasksSubmitted() {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            users.add(user(i + "@example.com"));
        }
        when(userPort.findByDeliveryEnabledTrue(any(Pageable.class))).thenReturn(new PageImpl<>(users));
        ExecutorService pool = Executors.newCachedThreadPool();
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        try {
            Spec spec = new Spec("test-job", pool, Duration.ofSeconds(5), 2, 2);
            Tally tally = new SubscriberBatch(userPort).run(spec, null, u -> true, u -> {
                peak.accumulateAndGet(running.incrementAndGet(), Math::max);
                try {
                    return sleepThenSucceed(30);
                } finally {
                    running.decrementAndGet();
                }
            });

            assertThat(tally.success()).isEqualTo(8);
            assertThat(peak.get()).isLessThanOrEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }
}
