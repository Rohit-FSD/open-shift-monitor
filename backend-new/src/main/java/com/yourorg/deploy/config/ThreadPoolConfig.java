package com.yourorg.deploy.config;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ThreadPoolConfig {

    /**
     * Bounded executor for parallel pod-log fetching.
     * 5 core / 15 max covers typical dashboard load (10 pods × 2 containers)
     * without risking thread exhaustion. The 100-task queue prevents rejection
     * under burst load while keeping memory bounded.
     */
    @Bean(name = "logFetchExecutor")
    public Executor logFetchExecutor() {
        return new ThreadPoolExecutor(
                5,   // corePoolSize
                15,  // maximumPoolSize
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                r -> {
                    Thread t = new Thread(r, "log-fetch-" + System.nanoTime());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
