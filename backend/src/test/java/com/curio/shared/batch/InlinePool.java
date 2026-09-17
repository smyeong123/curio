package com.curio.shared.batch;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * A pool that runs every task on the caller's thread, so batch tests stay
 * sequential and deterministic while still handing the batch a real
 * {@link ThreadPoolTaskExecutor} to size its in-flight bound from.
 */
public final class InlinePool {

    private InlinePool() {}

    public static ThreadPoolTaskExecutor inline() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor() {
            @Override
            public void execute(Runnable task) {
                task.run();
            }
        };
        pool.setMaxPoolSize(8);
        return pool;
    }
}
