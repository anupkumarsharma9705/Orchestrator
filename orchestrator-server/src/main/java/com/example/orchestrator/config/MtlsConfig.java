package com.example.orchestrator.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

/**
 * mTLS Configuration and Certificate Filter.
 *
 * <p>Validates client certificates and extracts identity for authorization.
 * Works with Spring Security to provide certificate-based authentication.</p>
 *
 * <p>Some deployments terminate TLS at a reverse proxy and forward the client
 * certificate DN in a header. A header is only trusted when
 * {@code mtls.trust-proxy-header=true}; otherwise any caller could inject an
 * arbitrary identity. The flag must remain disabled unless every request
 * reaches this server through a proxy that strips and rewrites the header.</p>
 */
@Configuration
public class MtlsConfig {

    private static final Logger log = LoggerFactory.getLogger(MtlsConfig.class);

    private static final int MAX_DN_LENGTH = 1024;

    private final boolean mtlsEnabled;
    private final String clientCertHeader;
    private final boolean trustProxyHeader;

    public MtlsConfig(
            @Value("${mtls.enabled:true}") boolean mtlsEnabled,
            @Value("${mtls.client-cert-header:X-Client-Cert-DN}") String clientCertHeader,
            @Value("${mtls.trust-proxy-header:false}") boolean trustProxyHeader) {
        this.mtlsEnabled = mtlsEnabled;
        this.clientCertHeader = clientCertHeader;
        this.trustProxyHeader = trustProxyHeader;
    }

    @Bean
    public FilterRegistrationBean<ClientCertificateFilter> clientCertificateFilter() {
        FilterRegistrationBean<ClientCertificateFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ClientCertificateFilter());
        // Servlet spec path mappings must use a single trailing '*'.
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5); // After security filter
        return registration;
    }

    /**
     * Filter to extract and validate client certificate.
     * Sets authentication in SecurityContext based on client certificate.
     */
    public class ClientCertificateFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {

            if (!mtlsEnabled) {
                filterChain.doFilter(request, response);
                return;
            }

            // Try to get certificate from request attribute (set by Tomcat/Undertow)
            X509Certificate[] certs = (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");

            if (certs == null || certs.length == 0) {
                if (!trustProxyHeader) {
                    // Never trust a client-controlled identity header. Log only
                    // that the header was present, not its value, to avoid
                    // reflecting attacker-controlled data into logs.
                    if (request.getHeader(clientCertHeader) != null) {
                        log.warn("Ignoring {} because mtls.trust-proxy-header is disabled",
                            clientCertHeader);
                    } else {
                        log.warn("No client certificate presented for mTLS");
                    }
                    unauthorized(response, "Client certificate required");
                    return;
                }

                // Header is trusted only when the deployment explicitly enables
                // proxy-header trust. The proxy must overwrite this header for
                // every request; otherwise it remains spoofable.
                String certHeader = request.getHeader(clientCertHeader);
                if (certHeader == null || certHeader.isBlank()) {
                    log.warn("No client certificate or proxy header presented for mTLS");
                    unauthorized(response, "Client certificate required");
                    return;
                }
                String subjectDn = certHeader.trim();
                if (!isPlausibleDistinguishedName(subjectDn)) {
                    log.warn("Rejected malformed client certificate DN header");
                    unauthorized(response, "Client certificate identity is malformed");
                    return;
                }
                log.debug("Client certificate DN from trusted proxy header");
                setAuthenticationFromDN(subjectDn);
            } else {
                X509Certificate clientCert = certs[0];
                String subjectDN = clientCert.getSubjectX500Principal().getName();
                log.debug("Client certificate DN: {}", subjectDN);

                // Validate certificate (check expiration, etc.)
                try {
                    clientCert.checkValidity();
                } catch (Exception e) {
                    log.warn("Client certificate validation failed: {}", e.getMessage());
                    unauthorized(response, "Client certificate validation failed");
                    return;
                }

                setAuthenticationFromDN(subjectDN);

                // Store certificate in request for downstream use
                request.setAttribute("clientCertificate", clientCert);
            }

            filterChain.doFilter(request, response);
        }

        private void unauthorized(HttpServletResponse response, String message) throws IOException {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + message + "\"}");
        }

        private boolean isPlausibleDistinguishedName(String value) {
            return value.length() <= MAX_DN_LENGTH
                && value.indexOf('\n') < 0
                && value.indexOf('\r') < 0
                && value.contains("=");
        }

        private void setAuthenticationFromDN(String subjectDN) {
            // Extract CN from DN
            String cn = extractCN(subjectDN);

            // Create authentication with certificate DN as principal
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                subjectDN, // principal = full DN
                null,      // no credentials needed
                List.of(new SimpleGrantedAuthority("ROLE_CLIENT_CERT"), new SimpleGrantedAuthority("CN=" + cn))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("Set mTLS authentication for: {}", subjectDN);
        }

        private String extractCN(String subjectDN) {
            // Extract CN from X.500 DN
            String[] parts = subjectDN.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.regionMatches(true, 0, "CN=", 0, 3)) {
                    return trimmed.substring(3);
                }
            }
            return "unknown";
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String path = request.getRequestURI();
            // Skip mTLS for public endpoints
            return path.equals("/api/version")
                || path.startsWith("/actuator/health")
                || path.startsWith("/actuator/info");
        }
    }

    /**
     * Utility method to get client certificate from request.
     * Usage in controllers:
     *   X509Certificate cert = MtlsConfig.getClientCertificate(request);
     */
    public static Optional<X509Certificate> getClientCertificate(HttpServletRequest request) {
        X509Certificate[] certs = (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");
        if (certs != null && certs.length > 0) {
            return Optional.of(certs[0]);
        }
        return Optional.empty();
    }
}
