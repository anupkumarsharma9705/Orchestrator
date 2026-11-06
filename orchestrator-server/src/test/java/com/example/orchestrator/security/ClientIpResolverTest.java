package com.example.orchestrator.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    @Test
    void ignoresForwardingHeadersWhenTrustIsDisabled() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.10");
        request.addHeader("X-Real-IP", "203.0.113.9");

        assertEquals("10.0.0.10", new ClientIpResolver(false).resolve(request));
    }

    @Test
    void usesRightMostForwardedForHopWhenTrustIsEnabled() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "attacker-controlled, 198.51.100.7, 10.0.0.10");

        // The right-most hop is the value appended by the trusted proxy.
        assertEquals("10.0.0.10", new ClientIpResolver(true).resolve(request));
    }

    @Test
    void skipsEmptyRightMostHops() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "198.51.100.7,  , ");

        assertEquals("198.51.100.7", new ClientIpResolver(true).resolve(request));
    }

    @Test
    void fallsBackToRealIpThenRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Real-IP", "198.51.100.8");
        assertEquals("198.51.100.8", new ClientIpResolver(true).resolve(request));

        request.removeHeader("X-Real-IP");
        assertEquals("10.0.0.10", new ClientIpResolver(true).resolve(request));
    }

    @Test
    void capsClientIdentifierLength() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("x".repeat(200));

        assertEquals(ClientIpResolver.MAX_CLIENT_IP_LENGTH,
            new ClientIpResolver(false).resolve(request).length());
    }
}
