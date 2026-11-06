package com.example.orchestrator.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the client IP used for security decisions.
 *
 * <p>Forwarding headers are attacker-controlled unless the server is deployed
 * behind a proxy that overwrites them. They are therefore ignored by default.
 * When {@code orchestrator.security.trust-forwarded-headers} is enabled, the
 * <em>right-most</em> {@code X-Forwarded-For} hop is used: that is the value
 * appended by the proxy closest to this server. The left-most hop is supplied
 * by the client and must never be used for rate limiting.</p>
 */
public final class ClientIpResolver {

    public static final int MAX_CLIENT_IP_LENGTH = 64;

    private final boolean trustForwardedHeaders;

    public ClientIpResolver(boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    /**
     * Resolves the client IP for the request.
     *
     * @param request current servlet request
     * @return a non-null, length-capped client identifier
     */
    public String resolve(HttpServletRequest request) {
        String resolved = request.getRemoteAddr();
        if (!trustForwardedHeaders) {
            return normalise(resolved);
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] hops = forwardedFor.split(",");
            for (int i = hops.length - 1; i >= 0; i--) {
                String hop = hops[i].trim();
                if (!hop.isEmpty()) {
                    return normalise(hop);
                }
            }
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return normalise(realIp);
        }
        return normalise(resolved);
    }

    private static String normalise(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String trimmed = value.trim();
        return trimmed.length() > MAX_CLIENT_IP_LENGTH
            ? trimmed.substring(0, MAX_CLIENT_IP_LENGTH)
            : trimmed;
    }
}
