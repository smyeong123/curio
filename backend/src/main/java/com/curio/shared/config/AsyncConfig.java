package com.curio.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    @Bean(name = "digestExecutor")
    public Executor digestExecutor() {
        return buildExecutor("digest-gen-", 4, 8, 100, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        return buildExecutor("email-send-", 4, 10, 200, new ThreadPoolExecutor.CallerRunsPolicy());
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
    public Executor studioExecutor() {
        return buildExecutor("studio-", 2, 4, 50, new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Builds a bounded pool with back-pressure. The scheduled jobs submit a full
     * chunk (up to 500 tasks) at once, which far exceeds maxPool + queue capacity.
     * The batch pools use CallerRunsPolicy so the surplus makes the submitting
     * thread run the task, throttling submission to the pool's real throughput so
     * no work is dropped (with the default AbortPolicy the RejectedExecutionException
     * would propagate out of EmailSendJob and abort every remaining chunk, silently
     * dropping users). The Studio pool instead passes AbortPolicy — see
     * {@link #studioExecutor()} — because there is no submitting loop to throttle and
     * the long task must never run inline on the request thread.
     */
    private Executor buildExecutor(String prefix, int core, int max, int queue,
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
