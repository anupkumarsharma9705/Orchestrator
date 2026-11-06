package com.example.orchestrator.audit;

import com.example.orchestrator.security.ClientIpResolver;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Structured audit logging configuration.
 * H-13: Structured audit logging with correlation IDs and searchable fields.
 * 
 * Features:
 * - Correlation ID propagation (X-Correlation-ID header)
 * - Structured JSON logging with searchable fields
 * - Request/response audit trail
 * - Security event categorization
 */
@Configuration
@ConfigurationProperties(prefix = "audit")
public class AuditConfig {

    private boolean enabled = true;
    private boolean logRequestBody = false;
    private boolean logResponseBody = false;
    private int maxBodyLength = 10000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isLogRequestBody() { return logRequestBody; }
    public void setLogRequestBody(boolean logRequestBody) { this.logRequestBody = logRequestBody; }
    public boolean isLogResponseBody() { return logResponseBody; }
    public void setLogResponseBody(boolean logResponseBody) { this.logResponseBody = logResponseBody; }
    public int getMaxBodyLength() { return maxBodyLength; }
    public void setMaxBodyLength(int maxBodyLength) { this.maxBodyLength = maxBodyLength; }

    @Bean
    public FilterRegistrationBean<AuditFilter> auditFilter(
            @Value("${orchestrator.security.trust-forwarded-headers:false}") boolean trustForwardedHeaders) {
        FilterRegistrationBean<AuditFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AuditFilter(this, new ClientIpResolver(trustForwardedHeaders)));
        // Servlet spec path mappings end with a single '*'. "/api/**" is not
        // a valid mapping and silently matches nothing.
        registration.addUrlPatterns("/api/*", "/actuator/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.setEnabled(enabled);
        return registration;
    }

    /**
     * Audit filter that logs structured audit events for each request.
     */
    public static class AuditFilter extends OncePerRequestFilter {

        private final AuditConfig config;
        private final ClientIpResolver clientIpResolver;
        private final ObjectMapper objectMapper = new ObjectMapper();
        private static final Logger log = LoggerFactory.getLogger(AuditFilter.class);

        public AuditFilter(AuditConfig config, ClientIpResolver clientIpResolver) {
            this.config = config;
            this.clientIpResolver = clientIpResolver;
            this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws IOException, ServletException {

            if (!config.enabled) {
                filterChain.doFilter(request, response);
                return;
            }

            String correlationId = request.getHeader("X-Correlation-ID");
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            
            // Add correlation ID to response header for client tracking
            response.setHeader("X-Correlation-ID", correlationId);

            // Add correlation ID to MDC for log correlation
            org.slf4j.MDC.put("correlationId", correlationId);

            Instant startTime = Instant.now();
            String requestId = UUID.randomUUID().toString();

            // Build audit event
            AuditEvent auditEvent = new AuditEvent();
            auditEvent.setRequestId(requestId);
            auditEvent.setCorrelationId(correlationId);
            auditEvent.setTimestamp(startTime);
            auditEvent.setMethod(request.getMethod());
            auditEvent.setPath(request.getRequestURI());
            auditEvent.setQueryString(request.getQueryString());
            auditEvent.setClientIp(clientIpResolver.resolve(request));
            auditEvent.setUserAgent(request.getHeader("User-Agent"));
            auditEvent.setAuthType(getAuthType(request));

            // Log request body if enabled (for non-GET requests)
            if (config.isLogRequestBody() && !"GET".equals(request.getMethod())) {
                String body = readRequestBody(request);
                if (body != null && !body.isBlank()) {
                    auditEvent.setRequestBody(truncate(body, config.getMaxBodyLength()));
                }
            }

            // Capture response using a wrapper
            AuditResponseWrapper responseWrapper = new AuditResponseWrapper(response);
            Instant requestTime = Instant.now();

            try {
                filterChain.doFilter(request, response);
            } finally {
                Instant endTime = Instant.now();
                long durationMs = java.time.Duration.between(requestTime, endTime).toMillis();

                // Complete audit event
                auditEvent.setStatusCode(response.getStatus());
                auditEvent.setDurationMs(durationMs);
                auditEvent.setResponseSize(responseWrapper.getContentSize());
                auditEvent.setOutcome(determineOutcome(response.getStatus()));
                auditEvent.setCategory(categorizeEvent(request.getRequestURI(), response.getStatus()));

                // Log response body if enabled and error
                if (config.isLogResponseBody() && response.getStatus() >= 400) {
                    String body = responseWrapper.getBody();
                    if (body != null && !body.isBlank()) {
                        auditEvent.setResponseBody(truncate(body, config.getMaxBodyLength()));
                    }
                }

                // Log structured audit event
                logAuditEvent(auditEvent);
                
                // Clear MDC
                org.slf4j.MDC.remove("correlationId");
            }
        }

        private String getAuthType(HttpServletRequest request) {
            String auth = request.getHeader("Authorization");
            if (auth == null) return "NONE";
            if (auth.startsWith("Bearer ")) return "BEARER";
            if (auth.startsWith("Basic ")) return "BASIC";
            if (auth.startsWith("ApiKey ")) return "API_KEY";
            return "OTHER";
        }

        private String readRequestBody(HttpServletRequest request) {
            try {
                return new String(request.getInputStream().readAllBytes());
            } catch (Exception e) {
                return null;
            }
        }

        private String truncate(String s, int max) {
            if (s == null) return null;
            return s.length() > max ? s.substring(0, max) + "...[truncated]" : s;
        }

        private String determineOutcome(int status) {
            if (status >= 200 && status < 300) return "SUCCESS";
            if (status >= 400 && status < 500) return "CLIENT_ERROR";
            if (status >= 500) return "SERVER_ERROR";
            return "OTHER";
        }

        private String categorizeEvent(String path, int status) {
            if (path.startsWith("/actuator/health")) return "HEALTH_CHECK";
            if (path.startsWith("/actuator/")) return "ACTUATOR";
            if (path.startsWith("/api/auth/") || path.startsWith("/api/login")) return "AUTH";
            if (path.startsWith("/api/approvals/")) return "APPROVAL";
            if (path.startsWith("/api/tasks/")) return "TASK";
            if (path.startsWith("/api/projects/")) return "PROJECT";
            if (path.startsWith("/api/")) return "API";
            return "OTHER";
        }

        private void logAuditEvent(AuditEvent event) {
            try {
                String json = objectMapper.writeValueAsString(event);
                // Log at INFO level for structured audit trail
                log.info("AUDIT: {}", json);
            } catch (Exception e) {
                log.error("Failed to serialize audit event", e);
            }
        }
    }

    /**
     * Response wrapper to capture response body and size.
     */
    public static class AuditResponseWrapper extends jakarta.servlet.http.HttpServletResponseWrapper {
        private final StringBuilder body = new StringBuilder();
        private int contentSize = 0;

        public AuditResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public jakarta.servlet.ServletOutputStream getOutputStream() throws IOException {
            return new jakarta.servlet.ServletOutputStream() {
                private final jakarta.servlet.ServletOutputStream delegate = getResponse().getOutputStream();

                @Override
                public void write(int b) throws IOException {
                    body.append((char) b);
                    contentSize++;
                    delegate.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    body.append(new String(b, off, len));
                    contentSize += len;
                    delegate.write(b, off, len);
                }

                @Override
                public boolean isReady() {
                    return delegate.isReady();
                }

                @Override
                public void setWriteListener(jakarta.servlet.WriteListener listener) {
                    delegate.setWriteListener(listener);
                }
            };
        }

        @Override
        public java.io.PrintWriter getWriter() throws IOException {
            return new java.io.PrintWriter(getOutputStream());
        }

        public String getBody() {
            return body.toString();
        }

        public int getContentSize() {
            return contentSize;
        }
    }
}

/**
 * Audit event data structure for structured logging.
 * Designed for Elasticsearch/Splunk ingestion.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
class AuditEvent {
    private String requestId;
    private String correlationId;
    private Instant timestamp;
    private String method;
    private String path;
    private String queryString;
    private String clientIp;
    private String userAgent;
    private String authType;
    private String requestBody;
    private int statusCode;
    private long durationMs;
    private long responseSize;
    private String outcome; // SUCCESS, CLIENT_ERROR, SERVER_ERROR, OTHER
    private String category; // HEALTH_CHECK, ACTUATOR, AUTH, APPROVAL, TASK, PROJECT, API, OTHER
    private String responseBody;

    // Getters and setters
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getQueryString() { return queryString; }
    public void setQueryString(String queryString) { this.queryString = queryString; }
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }
    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public long getResponseSize() { return responseSize; }
    public void setResponseSize(long responseSize) { this.responseSize = responseSize; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
}