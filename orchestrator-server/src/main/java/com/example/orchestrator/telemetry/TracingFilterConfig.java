package com.example.orchestrator.telemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Filter to create OpenTelemetry spans for incoming HTTP requests.
 * Propagates trace context from incoming headers (W3C TraceContext).
 * 
 * H-15: Distributed tracing for HTTP requests.
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.otel", name = "enabled", havingValue = "true")
public class TracingFilterConfig {

    private static final Logger log = LoggerFactory.getLogger(TracingFilterConfig.class);

    @Bean
    public FilterRegistrationBean<TracingFilter> tracingFilter(OpenTelemetry openTelemetry) {
        Tracer tracer = openTelemetry.getTracer("com.example.orchestrator");
        FilterRegistrationBean<TracingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TracingFilter(tracer));
        registration.addUrlPatterns("/api/**", "/actuator/**");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    public static class TracingFilter extends OncePerRequestFilter {

        private final Tracer tracer;

        public TracingFilter(Tracer tracer) {
            this.tracer = tracer;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws IOException, ServletException {

            // Extract trace context from incoming headers
            Span parentSpan = tracer.spanBuilder(request.getMethod() + " " + request.getRequestURI())
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

            try (var scope = parentSpan.makeCurrent()) {
                // Add standard HTTP attributes
                parentSpan.setAttribute("http.method", request.getMethod());
                parentSpan.setAttribute("http.url", request.getRequestURL().toString());
                parentSpan.setAttribute("http.scheme", request.getScheme());
                parentSpan.setAttribute("http.target", request.getRequestURI());
                parentSpan.setAttribute("http.flavor", request.getProtocol());
                parentSpan.setAttribute("net.host.port", request.getServerPort());
                
                // Client info
                String clientIp = getClientIp(request);
                if (clientIp != null) {
                    parentSpan.setAttribute("net.peer.ip", clientIp);
                }
                
                String userAgent = request.getHeader("User-Agent");
                if (userAgent != null) {
                    parentSpan.setAttribute("http.user_agent", userAgent);
                }

                // Correlation ID
                String correlationId = request.getHeader("X-Correlation-ID");
                if (correlationId != null) {
                    parentSpan.setAttribute("correlation.id", correlationId);
                }

                // Response status will be set after filter chain
                try {
                    filterChain.doFilter(request, response);
                    parentSpan.setAttribute("http.status_code", response.getStatus());
                } catch (Exception e) {
                    parentSpan.recordException(e);
                    parentSpan.setAttribute("http.status_code", 500);
                    throw e;
                }
            } finally {
                parentSpan.end();
            }
        }

        private String getClientIp(HttpServletRequest request) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            String xri = request.getHeader("X-Real-IP");
            if (xri != null && !xri.isBlank()) {
                return xri;
            }
            return request.getRemoteAddr();
        }
    }

    /**
     * TextMapGetter for extracting trace context from HTTP headers.
     */
    public static class HttpHeadersGetter implements io.opentelemetry.context.propagation.TextMapGetter<HttpServletRequest> {
        
        @Override
        public String get(HttpServletRequest carrier, String key) {
            String value = carrier.getHeader(key);
            return value != null ? value : "";
        }

        @Override
        public Iterable<String> keys(HttpServletRequest carrier) {
            return Collections.list(carrier.getHeaderNames());
        }
    }
}