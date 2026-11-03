package com.example.orchestrator.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ApiKeyAuthService {

    private final Set<String> validApiKeys = ConcurrentHashMap.newKeySet();

    public ApiKeyAuthService(@Value("${orchestrator.api-key:}") String apiKey) {
        validApiKeys.add(ApiKeyRequirements.requireConfigured(apiKey));
    }

    public boolean validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        return validApiKeys.contains(apiKey);
    }

    public void addApiKey(String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            validApiKeys.add(apiKey);
        }
    }

    public void removeApiKey(String apiKey) {
        validApiKeys.remove(apiKey);
    }

    public boolean isConfigured() {
        return !validApiKeys.isEmpty();
    }
}
