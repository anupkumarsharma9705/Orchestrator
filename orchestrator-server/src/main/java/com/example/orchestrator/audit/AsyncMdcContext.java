package com.example.orchestrator.audit;

import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Utility for propagating MDC (Mapped Diagnostic Context) to asynchronous tasks.
 * 
 * H-14: Ensures correlation IDs and other logging context are available in async threads.
 * 
 * Usage:
 *   // Wrap a Runnable for async execution
 *   executor.execute(AsyncMdcContext.wrap(runnable));
 *   
 *   // Wrap a Callable
 *   CompletableFuture.supplyAsync(AsyncMdcContext.wrap(supplier));
 *   
 *   // Create an executor that automatically propagates MDC
 *   ExecutorService mdcExecutor = AsyncMdcContext.mdcExecutor();
 */
public final class AsyncMdcContext {

    private AsyncMdcContext() {}

    /**
     * Wraps a Runnable to capture current MDC context and restore it in the execution thread.
     */
    public static Runnable wrap(Runnable task) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                task.run();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }

    /**
     * Wraps a Callable to capture current MDC context and restore it in the execution thread.
     */
    public static <T> Callable<T> wrap(Callable<T> task) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                return task.call();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }

    /**
     * Wraps a Supplier to capture current MDC context and restore it in the execution thread.
     */
    public static <T> Supplier<T> wrap(Supplier<T> task) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                return task.get();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }

    /**
     * Creates an Executor that automatically propagates MDC context to all submitted tasks.
     */
    public static Executor mdcExecutor() {
        return mdcExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Creates an ExecutorService that automatically propagates MDC context to all submitted tasks.
     */
    public static ExecutorService mdcExecutor(ExecutorService delegate) {
        return new MdcPropagatingExecutorService(delegate);
    }

    /**
     * Creates an ExecutorService with virtual threads that automatically propagates MDC context.
     * Java 21+ recommended for high-concurrency async workloads.
     */
    public static ExecutorService virtualThreadMdcExecutor() {
        return mdcExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Runnable that captures and restores MDC context.
     */
    private static class MdcRunnable implements Runnable {
        private final Runnable delegate;
        private final Map<String, String> contextMap;

        MdcRunnable(Runnable delegate, Map<String, String> contextMap) {
            this.delegate = delegate;
            this.contextMap = contextMap;
        }

        @Override
        public void run() {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                delegate.run();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        }
    }

    /**
     * Callable that captures and restores MDC context.
     */
    private static class MdcCallable<T> implements Callable<T> {
        private final Callable<T> delegate;
        private final Map<String, String> contextMap;

        MdcCallable(Callable<T> delegate, Map<String, String> contextMap) {
            this.delegate = delegate;
            this.contextMap = contextMap;
        }

        @Override
        public T call() throws Exception {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                return delegate.call();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        }
    }

    /**
     * ExecutorService wrapper that propagates MDC context.
     */
    private static class MdcPropagatingExecutorService implements ExecutorService {
        private final ExecutorService delegate;

        MdcPropagatingExecutorService(ExecutorService delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(AsyncMdcContext.wrap(command));
        }

        @Override
        public <T> java.util.concurrent.Future<T> submit(Callable<T> task) {
            return delegate.submit(AsyncMdcContext.wrap(task));
        }

        @Override
        public <T> java.util.concurrent.Future<T> submit(Runnable task, T result) {
            return delegate.submit(AsyncMdcContext.wrap(task), result);
        }

        @Override
        public java.util.concurrent.Future<?> submit(Runnable task) {
            return delegate.submit(AsyncMdcContext.wrap(task));
        }

        // Delegate remaining methods
        @Override public void shutdown() { delegate.shutdown(); }
        @Override public java.util.List<java.lang.Runnable> shutdownNow() { return delegate.shutdownNow(); }
        @Override public boolean isShutdown() { return delegate.isShutdown(); }
        @Override public boolean isTerminated() { return delegate.isTerminated(); }
        @Override public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException { return delegate.awaitTermination(timeout, unit); }
        @Override public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks) throws InterruptedException { return delegate.invokeAll(tasks.stream().map(AsyncMdcContext::wrap).toList()); }
        @Override public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks, long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException { return delegate.invokeAll(tasks.stream().map(AsyncMdcContext::wrap).toList(), timeout, unit); }
        @Override public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks) throws InterruptedException, java.util.concurrent.ExecutionException { return delegate.invokeAny(tasks.stream().map(AsyncMdcContext::wrap).toList()); }
        @Override public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks, long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException, java.util.concurrent.ExecutionException, java.util.concurrent.TimeoutException { return delegate.invokeAny(tasks.stream().map(AsyncMdcContext::wrap).toList(), timeout, unit); }
    }
}