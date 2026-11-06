package com.example.orchestrator.health;

import com.example.orchestrator.sandbox.SandboxService;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Exposes the real sandbox readiness state through {@code /actuator/health}.
 *
 * <p>The compose health check used to prove only that TLS was listening. This
 * contributor fails health when the Docker daemon is unreachable, the sandbox
 * image is missing, or the workspace filesystem is unhealthy. It can be
 * disabled with {@code management.health.sandbox.enabled=false} (the test
 * profile does this because tests do not run a Docker daemon).</p>
 */
@Component
@ConditionalOnEnabledHealthIndicator("sandbox")
public class SandboxHealthIndicator implements HealthIndicator {

    private final SandboxService sandboxService;

    public SandboxHealthIndicator(SandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    @Override
    public Health health() {
        SandboxService.HealthCheckResult result = sandboxService.healthCheck();
        Health.Builder builder = result.healthy() ? Health.up() : Health.down();
        return builder
            .withDetail("message", result.message())
            .withDetail("checkedAt", result.timestamp().toString())
            .build();
    }
}
