package com.example.orchestrator.security;

import com.example.orchestrator.audit.AuditConfig;
import com.example.orchestrator.config.MtlsConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the servlet URL patterns used by cross-cutting security filters.
 *
 * <p>The servlet specification only accepts exact, prefix ({@code /path/*}) and
 * extension mappings. A pattern such as {@code /api/**} is invalid and is
 * silently ignored by the container, which disables audit logging and client
 * certificate propagation.<p>
 */
class FilterRegistrationTest {

    @Test
    void auditFilterUsesValidServletUrlPatterns() {
        var registration = new AuditConfig().auditFilter(false);

        assertThat(registration.getUrlPatterns())
            .containsExactlyInAnyOrder("/api/*", "/actuator/*");
        assertThat(registration.getUrlPatterns())
            .noneMatch(pattern -> pattern.contains("**"));
    }

    @Test
    void mtlsFilterUsesValidServletUrlPatterns() {
        var registration = new MtlsConfig(true, "X-Client-Cert-DN", false)
            .clientCertificateFilter();

        assertThat(registration.getUrlPatterns())
            .containsExactly("/api/*");
        assertThat(registration.getUrlPatterns())
            .noneMatch(pattern -> pattern.contains("**"));
    }
}
