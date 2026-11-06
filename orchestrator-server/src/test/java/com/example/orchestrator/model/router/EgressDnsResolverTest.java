package com.example.orchestrator.model.router;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class EgressDnsResolverTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "egress-dns-test");
        thread.setDaemon(true);
        return thread;
    });

    @AfterEach
    void shutDown() {
        executor.shutdownNow();
    }

    @Test
    void cachesSuccessfulLookups() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        EgressDnsResolver resolver = new EgressDnsResolver(host -> {
            lookups.incrementAndGet();
            return new InetAddress[] {InetAddress.getByName("93.184.216.34")};
        }, 500, 60, executor);

        assertEquals("93.184.216.34", resolver.resolve("public.example")[0].getHostAddress());
        assertEquals("93.184.216.34", resolver.resolve("public.example")[0].getHostAddress());
        assertEquals(1, lookups.get());
    }

    @Test
    void cachesNegativeLookups() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        EgressDnsResolver resolver = new EgressDnsResolver(host -> {
            lookups.incrementAndGet();
            throw new UnknownHostException(host);
        }, 500, 60, executor);

        assertNull(resolver.resolve("missing.example"));
        assertNull(resolver.resolve("missing.example"));
        assertEquals(1, lookups.get());
    }

    @Test
    void slowLookupFailsWithinConfiguredBudget() {
        EgressDnsResolver resolver = new EgressDnsResolver(host -> {
            try {
                Thread.sleep(Duration.ofSeconds(5).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UnknownHostException("interrupted");
            }
            return new InetAddress[] {InetAddress.getLoopbackAddress()};
        }, 100, 60, executor);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
            assertThrows(EgressDnsResolver.DnsResolutionTimeoutException.class,
                () -> resolver.resolve("slow.example")));
    }
}
