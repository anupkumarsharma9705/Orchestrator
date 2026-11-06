package com.example.orchestrator.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Spring Security configuration for the Orchestrator Server.
 *
 * <p>Every route is authenticated except a small explicit public set. The
 * bootstrap key can act as USER, APPROVER and ADMIN; additional keys may carry
 * a narrower role set. Method security is enabled so {@code @PreAuthorize} is
 * actually enforced.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final List<ApiKeyCredential> credentials;
    private final ClientIpResolver clientIpResolver;

    // Rate limiting: max 10 failed attempts per minute per IP
    private static final int MAX_FAILED_ATTEMPTS = 10;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000; // 1 minute

    // Track failed attempts per IP
    private final ConcurrentMap<String, FailedAttemptTracker> failedAttempts = new ConcurrentHashMap<>();

    public SecurityConfig(
            @Value("${orchestrator.api-key:}") String bootstrapApiKey,
            SecurityProperties properties) {
        this.clientIpResolver = new ClientIpResolver(properties.isTrustForwardedHeaders());
        this.credentials = buildCredentials(bootstrapApiKey, properties);
    }

    private static List<ApiKeyCredential> buildCredentials(String bootstrapApiKey,
                                                           SecurityProperties properties) {
        String validatedBootstrapKey = ApiKeyRequirements.requireConfigured(bootstrapApiKey);
        List<ApiKeyCredential> result = new ArrayList<>();
        result.add(credential(validatedBootstrapKey, "api",
            List.of("USER", "APPROVER", "ADMIN")));

        Set<String> seenKeys = new LinkedHashSet<>();
        seenKeys.add(validatedBootstrapKey);
        for (SecurityProperties.ApiKey configured : properties.getApiKeys()) {
            if (configured == null || configured.getKey() == null || configured.getKey().isBlank()) {
                continue;
            }
            String validatedKey = ApiKeyRequirements.requireConfigured(configured.getKey());
            if (!seenKeys.add(validatedKey)) {
                continue;
            }
            String subject = configured.getSubject() == null || configured.getSubject().isBlank()
                ? "api" : configured.getSubject().trim();
            result.add(credential(validatedKey, subject, configured.getRoles()));
        }
        return List.copyOf(result);
    }

    private static ApiKeyCredential credential(String key, String subject, List<String> roles) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (roles != null) {
            for (String role : roles) {
                if (role == null || role.isBlank()) {
                    continue;
                }
                String normalised = role.trim().toUpperCase(Locale.ROOT);
                if (!normalised.startsWith("ROLE_")) {
                    normalised = "ROLE_" + normalised;
                }
                authorities.add(new SimpleGrantedAuthority(normalised));
            }
        }
        return new ApiKeyCredential(
            key.getBytes(StandardCharsets.UTF_8),
            subject,
            List.copyOf(authorities));
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/version").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(apiKeyFilter(), UsernamePasswordAuthenticationFilter.class);

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
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**",
                    "/actuator/info", "/actuator/info/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(apiKeyFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Catch-all chain. Without this, any controller mapped outside /api and
     * /actuator would be reachable without authentication.
     */
    @Bean
    @Order(3)
    public SecurityFilterChain fallbackSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/version", "/actuator/health",
                    "/actuator/health/**", "/actuator/info", "/actuator/info/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(apiKeyFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private ApiKeyAuthenticationFilter apiKeyFilter() {
        return new ApiKeyAuthenticationFilter(credentials, clientIpResolver, failedAttempts);
    }

    private record ApiKeyCredential(
        byte[] key,
        String subject,
        List<GrantedAuthority> authorities
    ) {
        @Override
        public String toString() {
            return "ApiKeyCredential[subject=" + subject + "]";
        }
    }

    /**
     * Filter to validate API keys from the Authorization or X-API-Key header
     * with per-client rate limiting.
     */
    private static class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

        private final List<ApiKeyCredential> credentials;
        private final ClientIpResolver clientIpResolver;
        private final ConcurrentMap<String, FailedAttemptTracker> failedAttempts;

        public ApiKeyAuthenticationFilter(List<ApiKeyCredential> credentials,
                                          ClientIpResolver clientIpResolver,
                                          ConcurrentMap<String, FailedAttemptTracker> failedAttempts) {
            this.credentials = credentials;
            this.clientIpResolver = clientIpResolver;
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

            String clientIp = clientIpResolver.resolve(request);

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

            ApiKeyCredential matched = null;
            byte[] presented = apiKey.getBytes(StandardCharsets.UTF_8);
            // Compare against every credential so timing does not reveal which
            // key (if any) matched early.
            for (ApiKeyCredential credential : credentials) {
                if (MessageDigest.isEqual(credential.key(), presented)) {
                    matched = credential;
                }
            }

            if (matched == null) {
                recordFailedAttempt(clientIp);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Invalid API key\",\"message\":\"The provided API key is not valid\"}");
                return;
            }

            // Successful authentication - clear failed attempts for this IP
            clearFailedAttempts(clientIp);

            Authentication auth = new PreAuthenticatedAuthenticationToken(
                matched.subject(), null, matched.authorities());
            SecurityContextHolder.getContext().setAuthentication(auth);

            filterChain.doFilter(request, response);
        }

        private boolean isPublicEndpoint(String path) {
            return path.equals("/api/version")
                || path.equals("/actuator/health")
                || path.startsWith("/actuator/health/")
                || path.equals("/actuator/info")
                || path.startsWith("/actuator/info/");
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
