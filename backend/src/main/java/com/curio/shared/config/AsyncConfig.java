package com.curio.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * The three task pools. Beans are typed as {@link ThreadPoolTaskExecutor} (not the
 * bare {@code Executor}) so the batch jobs can size their in-flight bound from the
 * pool's real max size instead of a constant that could drift from it.
 */
@Configuration
public class AsyncConfig {

    /**
     * Batch pools run with core == max: a {@link java.util.concurrent.ThreadPoolExecutor}
     * only adds threads beyond its core size once the queue is full, so a
     * core-4/max-8 pool fed by a bounded submitter would never use more than four
     * threads and the extra submissions would sit in the queue with their per-user
     * clocks already running. Core threads time out when idle, so between runs
     * the pools shrink to nothing.
     */
    @Bean(name = "digestExecutor")
    public ThreadPoolTaskExecutor digestExecutor() {
        return buildBatchExecutor("digest-gen-", 8, 100);
    }

    @Bean(name = "emailExecutor")
    public ThreadPoolTaskExecutor emailExecutor() {
        return buildBatchExecutor("email-send-", 10, 200);
    }

    private ThreadPoolTaskExecutor buildBatchExecutor(String prefix, int threads, int queue) {
        ThreadPoolTaskExecutor executor = buildExecutor(prefix, threads, threads, queue,
                new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setAllowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * Small, isolated pool for user-triggered Digest Studio tasks (one user
     * generating/sending their own digest on demand). Kept separate from the
     * batch-job pools so a burst of self-serve runs can't starve the nightly
     * jobs, and vice-versa.
     *
     * Uses AbortPolicy (not CallerRunsPolicy): a Studio task is a multi-second AI
     * pipeline started behind a fast, non-blocking response, so running it inline
     * on the Tomcat request thread would defeat that contract and tie up request
     * threads under load. On saturation execute() throws RejectedExecutionException,
     * which StudioService catches and surfaces as a "busy, try again" status.
     */
    @Bean(name = "studioExecutor")
    public ThreadPoolTaskExecutor studioExecutor() {
        return buildExecutor("studio-", 2, 4, 50, new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Builds a bounded pool with back-pressure. The batch jobs submit through
     * {@code SubscriberBatch}, which keeps at most maxPool + 2 tasks in flight, so
     * their queues hold at most two tasks in normal operation; CallerRunsPolicy stays
     * as the safety net for any other submitter, so surplus work runs on the
     * submitting thread instead of being dropped (with AbortPolicy a
     * RejectedExecutionException would abort the run and silently skip users). The
     * Studio pool instead passes AbortPolicy — see {@link #studioExecutor()} — because
     * there is no submitting loop to throttle and the long task must never run inline
     * on the request thread.
     */
    private ThreadPoolTaskExecutor buildExecutor(String prefix, int core, int max, int queue,
                                                 RejectedExecutionHandler rejectedHandler) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix(prefix);
        executor.setRejectedExecutionHandler(rejectedHandler);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
