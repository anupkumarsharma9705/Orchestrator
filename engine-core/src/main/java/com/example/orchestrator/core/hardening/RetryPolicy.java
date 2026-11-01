package com.example.orchestrator.core.hardening;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Retry mechanism with exponential backoff, timeout enforcement, and configurable predicates.
 * 
 * M5, M11: Fixed unbounded thread pool - now uses bounded ThreadPoolExecutor with
 * configurable core/max pool size and bounded queue to prevent OOM under load.
 */
@Component
public class RetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(RetryPolicy.class);

    // M5, M11: Bounded thread pool to prevent OOM under load
    // Core: 4 threads, Max: 16 threads, Queue: 100 tasks
    private final ExecutorService executor = new ThreadPoolExecutor(
        4,                          // core pool size
        16,                         // max pool size
        60L, TimeUnit.SECONDS,      // keep-alive time
        new LinkedBlockingQueue<>(100), // bounded queue (100 tasks max)
        new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "retry-policy-" + threadNumber.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        },
        new ThreadPoolExecutor.AbortPolicy() // reject new tasks when queue full
    );

    /**
     * Configuration for retry behavior.
     */
    public record Config(
        int maxAttempts,
        Duration initialDelay,
        Duration maxDelay,
        double multiplier,
        Duration timeout,
        Predicate<Throwable> retryablePredicate
    ) {
        public static Config defaults() {
            return new Config(
                3,
                Duration.ofSeconds(1),
                Duration.ofSeconds(60),
                2.0,
                Duration.ofMinutes(5),
                t -> !(t instanceof IllegalArgumentException || t instanceof IllegalStateException)
            );
        }
    }

    /**
     * Executes a callable with retry logic.
     */
    public <T> T execute(Callable<T> task, Config config) throws Exception {
        Exception lastException = null;
        Duration delay = config.initialDelay();

        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            try {
                // Execute with timeout
                CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return task.call();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, executor);

                return future.get(config.timeout().toMillis(), TimeUnit.MILLISECONDS);

            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                lastException = new Exception(cause);

                boolean isLastAttempt = attempt == config.maxAttempts();
                boolean retryable = config.retryablePredicate().test(cause);

                if (isLastAttempt || !retryable) {
                    log.error("Retry exhausted after {} attempts or non-retryable error: {}", 
                        attempt, cause.getMessage(), cause);
                    throw lastException;
                }

                log.warn("Attempt {}/{} failed: {}. Retrying in {}ms...", 
                    attempt, config.maxAttempts(), cause.getMessage(), delay.toMillis());

                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedException("Retry interrupted");
                }

                // Exponential backoff with cap
                delay = Duration.ofMillis(Math.min(
                    (long) (delay.toMillis() * config.multiplier()),
                    config.maxDelay().toMillis()
                ));
            }
        }

        throw lastException;
    }

    /**
     * Executes with default configuration.
     */
    public <T> T execute(Callable<T> task) throws Exception {
        return execute(task, Config.defaults());
    }

    /**
     * Shuts down the internal executor.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}