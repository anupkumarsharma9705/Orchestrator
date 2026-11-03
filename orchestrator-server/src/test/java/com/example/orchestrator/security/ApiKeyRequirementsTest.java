package com.example.orchestrator.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyRequirementsTest {

    @Test
    void rejectsMissingApiKey() {
        assertThrows(IllegalStateException.class, () -> ApiKeyRequirements.requireConfigured(null));
        assertThrows(IllegalStateException.class, () -> ApiKeyRequirements.requireConfigured(""));
        assertThrows(IllegalStateException.class, () -> ApiKeyRequirements.requireConfigured("   "));
    }

    @Test
    void rejectsShortApiKey() {
        String tooShort = "a".repeat(31);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ApiKeyRequirements.requireConfigured(tooShort));
        assertTrue(error.getMessage().contains("at least 32"));
    }

    @Test
    void acceptsLongApiKeyAndTrimsWhitespace() {
        String key = "0123456789abcdef0123456789abcdef";
        assertEquals(key, ApiKeyRequirements.requireConfigured("  " + key + "  "));
    }
}
