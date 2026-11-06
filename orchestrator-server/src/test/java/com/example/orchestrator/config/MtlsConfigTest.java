package com.example.orchestrator.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MtlsConfigTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ignoresClientCertificateHeaderByDefault() throws Exception {
        MtlsConfig config = new MtlsConfig(true, "X-Client-Cert-DN", false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tasks");
        request.addHeader("X-Client-Cert-DN", "CN=admin,O=Example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter(config).doFilter(request, response, chain(chainCalled));

        assertEquals(401, response.getStatus());
        assertFalse(chainCalled.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void trustsClientCertificateHeaderOnlyWhenExplicitlyEnabled() throws Exception {
        MtlsConfig config = new MtlsConfig(true, "X-Client-Cert-DN", true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tasks");
        request.addHeader("X-Client-Cert-DN", "CN=client,O=Example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter(config).doFilter(request, response, chain(chainCalled));

        assertTrue(chainCalled.get());
        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("CN=client,O=Example", authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().stream()
            .anyMatch(authority -> "CN=client".equals(authority.getAuthority())));
    }

    @Test
    void rejectsControlCharactersInTrustedHeader() throws Exception {
        MtlsConfig config = new MtlsConfig(true, "X-Client-Cert-DN", true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tasks");
        request.addHeader("X-Client-Cert-DN", "CN=client\nInjected=value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter(config).doFilter(request, response, chain(chainCalled));

        assertEquals(401, response.getStatus());
        assertFalse(chainCalled.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void disabledMtlsPassesRequestThrough() throws Exception {
        MtlsConfig config = new MtlsConfig(false, "X-Client-Cert-DN", false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tasks");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter(config).doFilter(request, response, chain(chainCalled));

        assertTrue(chainCalled.get());
        assertEquals(200, response.getStatus());
    }

    private static Filter filter(MtlsConfig config) {
        return config.clientCertificateFilter().getFilter();
    }

    private static FilterChain chain(AtomicBoolean called) {
        return new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) {
                called.set(true);
            }
        };
    }
}
