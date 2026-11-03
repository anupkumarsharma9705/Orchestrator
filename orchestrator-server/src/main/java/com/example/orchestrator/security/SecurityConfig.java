package com.example.orchestrator.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Spring Security configuration for the Orchestrator Server.
 * Enforces API key authentication on all endpoints with rate limiting.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final String apiKey;
    private final boolean isProduction;

    // Rate limiting: max 10 failed attempts per minute per IP
    private static final int MAX_FAILED_ATTEMPTS = 10;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000; // 1 minute
    
    // Track failed attempts per IP
    private final ConcurrentMap<String, FailedAttemptTracker> failedAttempts = new ConcurrentHashMap<>();

    public SecurityConfig(
            @Value("${orchestrator.api-key:}") String apiKey,
            @Value("${spring.profiles.active:dev}") String activeProfile) {
        this.apiKey = ApiKeyRequirements.requireConfigured(apiKey);
        this.isProduction = "prod".equals(activeProfile) || "production".equals(activeProfile);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {        http
            .securityMatcher("/api/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/version").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/info").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new ApiKeyAuthenticationFilter(apiKey, failedAttempts), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/actuator/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new ApiKeyAuthenticationFilter(apiKey, failedAttempts), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Filter to validate API key from Authorization header or custom header with rate limiting.
     */
    private static class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

        private final byte[] configuredApiKey;
        private final ConcurrentMap<String, FailedAttemptTracker> failedAttempts;

        public ApiKeyAuthenticationFilter(String apiKey, ConcurrentMap<String, FailedAttemptTracker> failedAttempts) {
            this.configuredApiKey = apiKey.getBytes(StandardCharsets.UTF_8);
            this.failedAttempts = failedAttempts;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {

            String path = request.getRequestURI();
            
            // Skip authentication for public endpoints
            if (isPublicEndpoint(path)) {
                filterChain.doFilter(request, response);
                return;
            }

            String clientIp = getClientIp(request);
            
            // Check rate limiting before authentication
            if (isRateLimited(clientIp)) {
                response.setStatus(429); // Too Many Requests
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Rate limited\",\"message\":\"Too many failed authentication attempts. Try again later.\"}");
                return;
            }

            String apiKey = extractApiKey(request);
            
            if (apiKey == null || apiKey.isBlank()) {
                recordFailedAttempt(clientIp);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"API key required\",\"message\":\"Provide API key via Authorization: Bearer <key> or X-API-Key header\"}");
                return;
            }

            if (!MessageDigest.isEqual(configuredApiKey, apiKey.getBytes(StandardCharsets.UTF_8))) {
                recordFailedAttempt(clientIp);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Invalid API key\",\"message\":\"The provided API key is not valid\"}");
                return;
            }

            // Successful authentication - clear failed attempts for this IP
            clearFailedAttempts(clientIp);

            // Create authentication and set in security context
            Authentication auth = new PreAuthenticatedAuthenticationToken(apiKey, apiKey, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);
            
            filterChain.doFilter(request, response);
        }

        private boolean isPublicEndpoint(String path) {
            return path.equals("/api/version") 
                || path.startsWith("/actuator/health")
                || path.startsWith("/actuator/info");
        }

        private String getClientIp(HttpServletRequest request) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return xRealIp.trim();
            }
            return request.getRemoteAddr();
        }

        private boolean isRateLimited(String clientIp) {
            FailedAttemptTracker tracker = failedAttempts.get(clientIp);
            if (tracker == null) return false;
            
            long now = Instant.now().toEpochMilli();
            // Clean old attempts
            tracker.cleanOldAttempts(now);
            
            return tracker.getCount() >= MAX_FAILED_ATTEMPTS;
        }

        private void recordFailedAttempt(String clientIp) {
            failedAttempts.computeIfAbsent(clientIp, k -> new FailedAttemptTracker())
                .recordAttempt(Instant.now().toEpochMilli());
        }

        private void clearFailedAttempts(String clientIp) {
            failedAttempts.remove(clientIp);
        }

        private String extractApiKey(HttpServletRequest request) {
            // Check Authorization header: Bearer <key>
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7).trim();
            }
            
            // Check X-API-Key header
            String apiKeyHeader = request.getHeader("X-API-Key");
            if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
                return apiKeyHeader.trim();
            }
            
            return null;
        }
    }

    /**
     * Tracks failed authentication attempts with time-based window.
     */
    private static class FailedAttemptTracker {
        private final ConcurrentMap<Long, Long> attempts = new ConcurrentHashMap<>();
        
        void recordAttempt(long timestamp) {
            attempts.put(timestamp, timestamp);
        }
        
        void cleanOldAttempts(long now) {
            long cutoff = now - RATE_LIMIT_WINDOW_MS;
            attempts.entrySet().removeIf(entry -> entry.getKey() < cutoff);
        }
        
        int getCount() {
            return attempts.size();
        }
    }
}