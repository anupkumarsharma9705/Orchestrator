package com.example.orchestrator.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional role-bearing API keys.
 *
 * <p>The bootstrap key ({@code orchestrator.api-key} / {@code ORCHESTRATOR_API_KEY})
 * always exists and carries USER, APPROVER and ADMIN. Additional keys can be
 * configured here with a narrower role set so the API is not all-or-nothing.</p>
 */
@ConfigurationProperties(prefix = "orchestrator.security")
public class SecurityProperties {

    /**
     * Only enable when the server is behind a proxy you control. When false,
     * forwarding headers are ignored and the socket peer address is used.
     */
    private boolean trustForwardedHeaders = false;

    private List<ApiKey> apiKeys = new ArrayList<>();

    public boolean isTrustForwardedHeaders() {
        return trustForwardedHeaders;
    }

    public void setTrustForwardedHeaders(boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    public List<ApiKey> getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(List<ApiKey> apiKeys) {
        this.apiKeys = apiKeys == null ? new ArrayList<>() : apiKeys;
    }

    public static class ApiKey {
        private String key;
        private String subject = "api";
        private List<String> roles = new ArrayList<>(List.of("USER"));

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles == null ? new ArrayList<>() : roles;
        }
    }
}
