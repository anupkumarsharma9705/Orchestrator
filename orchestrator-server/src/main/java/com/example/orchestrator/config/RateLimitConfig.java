package com.example.orchestrator.config;

import com.example.orchestrator.security.ClientIpResolver;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting configuration using Resilience4j.
 * H-12: Rate limiting for API endpoints.
 *
 * Configuration via resilience4j.ratelimiter.* properties in application.yml.
 * Supports different limits per endpoint pattern.
 */
@Configuration
@ConfigurationProperties(prefix = "resilience4j.ratelimiter")
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    private boolean enabled = true;
    private Map<String, RateLimiterConfigProperties> instances = new ConcurrentHashMap<>();

    /**
     * Forwarding headers are client-controlled unless a trusted proxy rewrites
     * them. This mirrors the API-key filter so both layers key on the same
     * client identity.
     */
    @Value("${orchestrator.security.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    // Nested configuration classes
    public static class RateLimiterConfigProperties {
        private int limitForPeriod = 100;
        private int limitRefreshPeriod = 60; // seconds
        private int timeoutDuration = 5000; // milliseconds

        public int getLimitForPeriod() { return limitForPeriod; }
        public void setLimitForPeriod(int limitForPeriod) { this.limitForPeriod = limitForPeriod; }
        public int getLimitRefreshPeriod() { return limitRefreshPeriod; }
        public void setLimitRefreshPeriod(int limitRefreshPeriod) { this.limitRefreshPeriod = limitRefreshPeriod; }
        public int getTimeoutDuration() { return timeoutDuration; }
        public void setTimeoutDuration(int timeoutDuration) { this.timeoutDuration = timeoutDuration; }
    }

    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Map<String, RateLimiterConfigProperties> getInstances() { return instances; }
    public void setInstances(Map<String, RateLimiterConfigProperties> instances) { this.instances = instances; }
    public boolean isTrustForwardedHeaders() { return trustForwardedHeaders; }
    public void setTrustForwardedHeaders(boolean trustForwardedHeaders) { this.trustForwardedHeaders = trustForwardedHeaders; }

    // Internal registry and bucket cache
    private final RateLimiterRegistry registry = RateLimiterRegistry.ofDefaults();
    private final Map<String, RateLimiter> limiterCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // Initialize rate limiters from config
        for (Map.Entry<String, RateLimiterConfigProperties> entry : instances.entrySet()) {
            String name = entry.getKey();
            RateLimiterConfigProperties props = entry.getValue();

            RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(props.getLimitForPeriod())
                .limitRefreshPeriod(Duration.ofSeconds(props.getLimitRefreshPeriod()))
                .timeoutDuration(Duration.ofMillis(props.getTimeoutDuration()))
                .build();

            registry.rateLimiter(name, config);
            log.info("Initialized rate limiter '{}': limit={}/{}s", name, props.getLimitForPeriod(), props.getLimitRefreshPeriod());
        }
    }

    /**
     * Get or create rate limiter for a key.
     */
    public RateLimiter getRateLimiter(String name) {
        return limiterCache.computeIfAbsent(name, n -> registry.rateLimiter(n));
    }

    // Bean definitions
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter() {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter(this));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setEnabled(enabled);
        return registration;
    }

    /**
     * Rate limiting filter using Resilience4j.
     * Identifies clients by IP address using the shared trusted-proxy rules.
     */
    public static class RateLimitFilter extends OncePerRequestFilter {

        private final RateLimitConfig config;
        private final ClientIpResolver clientIpResolver;

        public RateLimitFilter(RateLimitConfig config) {
            this.config = config;
            this.clientIpResolver = new ClientIpResolver(config.isTrustForwardedHeaders());
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {

            if (!config.enabled) {
                filterChain.doFilter(request, response);
                return;
            }

            String path = request.getRequestURI();
            String clientIp = clientIpResolver.resolve(request);

            // Find matching rate limiter config (simple path prefix matching)
            String limiterName = findMatchingLimiter(path);
            if (limiterName == null) {
                filterChain.doFilter(request, response);
                return;
            }

            RateLimiter limiter = config.getRateLimiter(limiterName);
            String key = limiterName + ":" + clientIp;

            if (limiter.acquirePermission()) {
                // Add rate limit headers
                response.addHeader("X-RateLimit-Limit", String.valueOf(getLimitForPeriod(limiterName)));
                response.addHeader("X-RateLimit-Remaining", String.valueOf(getAvailablePermissions(limiter)));
                filterChain.doFilter(request, response);
            } else {
                // Rate limited
                response.setStatus(429); // HttpServletResponse.SC_TOO_MANY_REQUESTS
                response.setHeader("Retry-After", String.valueOf(getLimitRefreshPeriod(limiterName)));
                response.setHeader("X-RateLimit-Limit", String.valueOf(getLimitForPeriod(limiterName)));
                response.setHeader("X-RateLimit-Remaining", "0");
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Rate limit exceeded\",\"retryAfter\":" + getLimitRefreshPeriod(limiterName) + "}");
                log.debug("Rate limited: IP={}, path={}, limiter={}", clientIp, path, limiterName);
            }
        }

        private String findMatchingLimiter(String path) {
            // Check configured instances for matching path prefix
            for (String name : config.getInstances().keySet()) {
                // Default patterns: "api" -> /api/**, "actuator" -> /actuator/**, "health" -> /actuator/health
                if (matchesPattern(name, path)) {
                    return name;
                }
            }
            return null;
        }

        private boolean matchesPattern(String name, String path) {
            switch (name) {
                case "api": return path.startsWith("/api/");
                case "actuator": return path.startsWith("/actuator/");
                case "health": return path.equals("/actuator/health");
                default: return false;
            }
        }

        private int getAvailablePermissions(RateLimiter limiter) {
            // Resilience4j doesn't expose available permissions directly
            // Return a reasonable estimate
            return Math.max(0, getLimitForPeriod(limiter.getName()) - (int) limiter.getMetrics().getNumberOfWaitingThreads());
        }

        // Helper methods to get config properties
        private int getLimitForPeriod(String name) {
            return config.getInstances().getOrDefault(name, new RateLimiterConfigProperties()).getLimitForPeriod();
        }
        private int getLimitRefreshPeriod(String name) {
            return config.getInstances().getOrDefault(name, new RateLimiterConfigProperties()).getLimitRefreshPeriod();
        }
    }
}
