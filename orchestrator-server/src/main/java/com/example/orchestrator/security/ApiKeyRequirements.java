package com.example.orchestrator.security;

/**
 * Central validation for the server API key.
 *
 * <p>There is deliberately no fallback: a server that silently invents a
 * development key is trivially insecure and impossible to configure from a
 * client. Startup fails with an actionable message instead.</p>
 */
public final class ApiKeyRequirements {

    /** Minimum length for a shared secret that protects the whole REST API. */
    private static final int MIN_LENGTH = 32;

    private ApiKeyRequirements() {
    }

    public static String requireConfigured(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "ORCHESTRATOR_API_KEY is not set. Generate one with "
                + "'openssl rand -hex 32' (or run scripts/dev-up) and pass it as an environment variable.");
        }

        String trimmed = apiKey.trim();
        if (trimmed.length() < MIN_LENGTH) {
            throw new IllegalStateException(
                "ORCHESTRATOR_API_KEY must be at least " + MIN_LENGTH
                + " characters. Generate one with 'openssl rand -hex 32'.");
        }
        return trimmed;
    }
}
