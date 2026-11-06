package com.example.orchestrator.model.router;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EgressFilterTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "egress-filter-test");
        thread.setDaemon(true);
        return thread;
    });

    @AfterEach
    void shutDown() {
        executor.shutdownNow();
    }

    @Test
    void blocksLoopbackPrivateAndMetadataAddresses() {
        assertThrows(IllegalArgumentException.class,
            () -> EgressFilter.validateEgress("http://127.0.0.1:8080"));
        assertThrows(IllegalArgumentException.class,
            () -> EgressFilter.validateEgress("http://10.0.0.5"));
        assertThrows(IllegalArgumentException.class,
            () -> EgressFilter.validateEgress("http://192.168.1.10"));
        assertThrows(IllegalArgumentException.class,
            () -> EgressFilter.validateEgress("http://169.254.169.254/latest/meta-data"));
        assertThrows(IllegalArgumentException.class,
            () -> EgressFilter.validateEgress("http://localhost:8080"));
        assertThrows(IllegalArgumentException.class,
            () -> EgressFilter.validateEgress("http://kubernetes.default.svc"));
    }

    @Test
    void allowsPublicLiteralAddress() {
        assertDoesNotThrow(() -> EgressFilter.validateEgress("https://8.8.8.8"));
    }

    @Test
    void blocksHostnameThatResolvesToPrivateAddress() throws Exception {
        EgressDnsResolver resolver = new EgressDnsResolver(
            host -> new InetAddress[] {InetAddress.getByName("10.1.2.3")},
            500, 60, executor);

        assertThrows(IllegalArgumentException.class,
            () -> EgressFilter.validateEgress("https://public.example/v1", resolver));
    }

    @Test
    void allowsHostnameThatResolvesToPublicAddress() throws Exception {
        EgressDnsResolver resolver = new EgressDnsResolver(
            host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")},
            500, 60, executor);

        assertDoesNotThrow(() ->
            EgressFilter.validateEgress("https://public.example/v1", resolver));
    }

    @Test
    void rejectsMissingUrl() {
        assertThrows(IllegalArgumentException.class, () -> EgressFilter.validateEgress(""));
        assertThrows(IllegalArgumentException.class, () -> EgressFilter.validateEgress(null));
    }
}
