package com.example.orchestrator.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Version and compatibility endpoint for CLI handshake.
 */
@RestController
@RequestMapping("/api")
public class VersionController {

    @Value("${orchestrator.version:unknown}")
    private String serverVersion;

    @Value("${orchestrator.api.min-compatible-client-version:0.0.1}")
    private String minCompatibleClientVersion;

    @GetMapping("/version")
    public VersionResponse version() {
        return new VersionResponse(
            serverVersion,
            minCompatibleClientVersion,
            List.of("v1"),
            Instant.now()
        );
    }

    public record VersionResponse(
        String serverVersion,
        String minCompatibleClientVersion,
        List<String> supportedApiVersions,
        Instant timestamp
    ) {}
}