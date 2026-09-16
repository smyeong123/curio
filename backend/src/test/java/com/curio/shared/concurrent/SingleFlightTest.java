package com.curio.shared.concurrent;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SingleFlightTest {

    @Test
    void returnsValueFromBody() {
        SingleFlight singleFlight = new SingleFlight();
        assertThat(singleFlight.call("k", () -> "result")).isEqualTo("result");
    }

    @Test
    void sameKey_neverRunsBodyConcurrently() throws Exception {
        SingleFlight singleFlight = new SingleFlight();
        int threads = 8;
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    singleFlight.call("topic:2026-06-30", () -> {
                        int now = inFlight.incrementAndGet();
                        maxInFlight.accumulateAndGet(now, Math::max);
                        try { Thread.sleep(20); } catch (InterruptedException ignored) { }
                        inFlight.decrementAndGet();
                        return null;
                    });
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // Callers sharing a key are serialized: the herd never enters the body together.
        assertThat(maxInFlight.get()).isEqualTo(1);
    }

    @Test
    void differentKeys_runBodyConcurrently() throws Exception {
        SingleFlight singleFlight = new SingleFlight();
        int threads = 4;
        AtomicInteger reachedTogether = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            String key = "key-" + i;
            pool.submit(() -> {
                try {
                    singleFlight.call(key, () -> {
                        try {
                            // Releases only if all distinct-key bodies are in flight at once.
                            barrier.await(2, TimeUnit.SECONDS);
                            reachedTogether.incrementAndGet();
                        } catch (Exception e) {
                            // barrier timed out → they were NOT concurrent
                        }
                        return null;
                    });
                } finally {
                    done.countDown();
                }
            });
        }
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(reachedTogether.get()).isEqualTo(threads);
    }
}
