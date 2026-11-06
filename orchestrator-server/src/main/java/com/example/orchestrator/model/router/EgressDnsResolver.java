package com.example.orchestrator.model.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * DNS resolver used by {@link EgressFilter} with a bounded wait and a small
 * positive/negative cache.
 *
 * <p>{@link InetAddress#getAllByName(String)} has no timeout API and can block
 * for the operating system resolver timeout (often tens of seconds). The lookup
 * therefore runs on a daemon worker and the caller waits only for the configured
 * budget. Successful and negative results are cached to avoid repeating lookups
 * for every provider initialization.</p>
 *
 * <p>Configuration (system property or environment variable), read once at
 * startup:</p>
 * <ul>
 *   <li>{@code orchestrator.egress.dns-timeout-ms} /
 *       {@code ORCHESTRATOR_EGRESS_DNS_TIMEOUT_MS} (default 2000 ms)</li>
 *   <li>{@code orchestrator.egress.dns-cache-ttl-seconds} /
 *       {@code ORCHESTRATOR_EGRESS_DNS_CACHE_TTL_SECONDS} (default 300 s)</li>
 * </ul>
 */
final class EgressDnsResolver {

    private static final Logger log = LoggerFactory.getLogger(EgressDnsResolver.class);

    private static final long DEFAULT_TIMEOUT_MS = 2_000L;
    private static final long DEFAULT_CACHE_TTL_SECONDS = 300L;
    private static final long NEGATIVE_CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(30);

    @FunctionalInterface
    interface HostLookup {
        InetAddress[] lookup(String host) throws UnknownHostException;
    }

    private final HostLookup lookup;
    private final long timeoutNanos;
    private final long timeoutMs;
    private final long cacheTtlNanos;
    private final ExecutorService executor;
    private final java.util.concurrent.ConcurrentMap<String, CacheEntry> cache =
        new java.util.concurrent.ConcurrentHashMap<>();

    private record CacheEntry(InetAddress[] addresses, long expiresAtNanos) {}

    EgressDnsResolver(HostLookup lookup, long timeoutMs, long cacheTtlSeconds) {
        this(lookup, timeoutMs, cacheTtlSeconds, newExecutor());
    }

    EgressDnsResolver(HostLookup lookup, long timeoutMs, long cacheTtlSeconds, ExecutorService executor) {
        this.lookup = lookup;
        this.timeoutMs = Math.max(1L, timeoutMs);
        this.timeoutNanos = TimeUnit.MILLISECONDS.toNanos(this.timeoutMs);
        this.cacheTtlNanos = TimeUnit.SECONDS.toNanos(Math.max(1L, cacheTtlSeconds));
        this.executor = executor;
    }

    static EgressDnsResolver production() {
        long timeoutMs = readLong(
            "orchestrator.egress.dns-timeout-ms",
            "ORCHESTRATOR_EGRESS_DNS_TIMEOUT_MS",
            DEFAULT_TIMEOUT_MS);
        long ttlSeconds = readLong(
            "orchestrator.egress.dns-cache-ttl-seconds",
            "ORCHESTRATOR_EGRESS_DNS_CACHE_TTL_SECONDS",
            DEFAULT_CACHE_TTL_SECONDS);
        return new EgressDnsResolver(InetAddress::getAllByName, timeoutMs, ttlSeconds);
    }

    /**
     * Resolves a host within the configured budget.
     *
     * @return resolved addresses, or {@code null} when the host is unknown
     * @throws DnsResolutionTimeoutException when the lookup exceeds the budget
     * @throws IllegalStateException for lower-level resolver failures
     */
    InetAddress[] resolve(String host) {
        long now = System.nanoTime();
        CacheEntry cached = cache.get(host);
        if (cached != null && now < cached.expiresAtNanos()) {
            return copy(cached.addresses());
        }

        Future<InetAddress[]> future;
        try {
            future = executor.submit(() -> lookup.lookup(host));
        } catch (RejectedExecutionException e) {
            throw new IllegalStateException("DNS resolver is not available for host " + host, e);
        }

        try {
            InetAddress[] addresses = future.get(timeoutNanos, TimeUnit.NANOSECONDS);
            cache.put(host, new CacheEntry(copy(addresses), now + cacheTtlNanos));
            return copy(addresses);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("DNS lookup timed out after {} ms for host {}", timeoutMs, host);
            throw new DnsResolutionTimeoutException(host, timeoutMs);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new DnsResolutionTimeoutException(host, timeoutMs);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof UnknownHostException) {
                cache.put(host, new CacheEntry(null, now + NEGATIVE_CACHE_TTL_NANOS));
                return null;
            }
            throw new IllegalStateException("DNS resolution failed for host " + host, cause);
        }
    }

    /**
     * Clears cached results. Intended for tests and for operational refreshes
     * after DNS changes.
     */
    void clearCache() {
        cache.clear();
    }

    private static InetAddress[] copy(InetAddress[] addresses) {
        return addresses == null ? null : addresses.clone();
    }

    private static ExecutorService newExecutor() {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "egress-dns-resolver");
            thread.setDaemon(true);
            return thread;
        };
        // Two daemon workers tolerate one stuck lookup without unbounded thread
        // creation. Callers still fail closed when both workers are occupied.
        return Executors.newFixedThreadPool(2, factory);
    }

    private static long readLong(String property, String environment, long defaultValue) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environment);
        }
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Ignoring invalid {} value '{}'; using {} ms/seconds default",
                property, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Raised when the operating-system resolver does not answer within the
     * configured budget. Callers must fail closed.
     */
    static final class DnsResolutionTimeoutException extends RuntimeException {
        DnsResolutionTimeoutException(String host, long timeoutMs) {
            super("DNS lookup for " + host + " exceeded " + timeoutMs + " ms");
        }
    }
}
