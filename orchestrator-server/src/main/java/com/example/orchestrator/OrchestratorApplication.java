package com.example.orchestrator;

import com.example.orchestrator.config.PipelineProperties;
import com.example.orchestrator.model.router.ModelRouterConfig;
import com.example.orchestrator.project.WorkspaceProperties;
import com.example.orchestrator.security.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootApplication
@EnableConfigurationProperties({PipelineProperties.class, ModelRouterConfig.class,
        WorkspaceProperties.class, SecurityProperties.class})
// Scan only the server's repositories here. engine-store's
// StoreAutoConfiguration registers com.example.orchestrator.store.repository;
// listing it in both places creates duplicate bean definitions.
@EnableJpaRepositories(basePackages = "com.example.orchestrator.persistence.repo")
// Boot 4 no longer derives entity packages when @EnableJpaRepositories is
// explicit. engine-store keeps its own @EntityScan in StoreAutoConfiguration.
@EntityScan(basePackages = "com.example.orchestrator.persistence.entity")
public class OrchestratorApplication {

    public static void main(String[] args) {
        // Spring Boot installs its own shutdown hook and drains in-flight
        // requests when server.shutdown=graceful.
        SpringApplication.run(OrchestratorApplication.class, args);
    }

    @Bean
    public SmartLifecycle gracefulShutdownLifecycle() {
        return new GracefulShutdownLifecycle();
    }

    /**
     * Logs shutdown progress. Actual draining is handled by Spring Boot's
     * graceful shutdown support; blocking here would delay context close on a
     * latch that no request path counts down.
     */
    public static class GracefulShutdownLifecycle implements SmartLifecycle {
        private final AtomicBoolean running = new AtomicBoolean(false);
        private volatile boolean shuttingDown = false;

        @Override
        public void start() {
            running.set(true);
            System.out.println("Application started - graceful shutdown lifecycle active");
        }

        @Override
        public void stop() {
            if (shuttingDown) {
                return;
            }
            shuttingDown = true;
            running.set(false);
            System.out.println("Graceful shutdown initiated - allowing Spring Boot to drain in-flight requests");
        }

        @Override
        public boolean isRunning() {
            return running.get();
        }

        @Override
        public int getPhase() {
            // Run early on startup, late on shutdown
            return Integer.MAX_VALUE;
        }
    }
}