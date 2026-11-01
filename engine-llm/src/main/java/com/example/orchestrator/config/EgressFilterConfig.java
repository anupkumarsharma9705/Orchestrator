package com.example.orchestrator.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Egress filtering configuration for LLM provider calls (LV-07).
 * Restricts outbound connections to allowed LLM provider domains only.
 * Implements ASVS v5.0.0-1.3.6 - Egress Filtering.
 * 
 * Features:
 * - Domain allowlist for approved provider domains
 * - Connection timeout enforcement
 * - Connection pooling with limits
 * - TLS verification enforcement
 * - Host validation at connection time
 */
@Configuration
public class EgressFilterConfig {

    // Allowed LLM provider domains (configurable via application.yml)
    private static final Set<String> DEFAULT_ALLOWED_DOMAINS = Set.of(
        "api.openai.com",
        "openrouter.ai",
        "integrate.api.nvidia.com",
        "api.anthropic.com",
        "generativelanguage.googleapis.com",
        "api.cohere.ai",
        "api.together.xyz",
        "api.mistral.ai",
        "api.perplexity.ai",
        "api.deepseek.com",
        "api.groq.com",
        "localhost"
    );

    @Value("${orchestrator.egress.allowed-domains:#{null}}")
    private List<String> configuredAllowedDomains;

    @Value("${orchestrator.egress.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${orchestrator.egress.read-timeout-ms:60000}")
    private int readTimeoutMs;

    @Value("${orchestrator.egress.max-connections:50}")
    private int maxConnections;

    @Value("${orchestrator.egress.max-pending-acquires:100}")
    private int maxPendingAcquires;

    @Value("${orchestrator.egress.tls-enabled:true}")
    private boolean tlsEnabled;

    @Value("${orchestrator.egress.tls-verify-hostname:true}")
    private boolean tlsVerifyHostname;

    /**
     * Creates a WebClient builder with egress filtering applied.
     * Use this builder for all outbound LLM provider calls.
     */
    @Bean
    public WebClient.Builder filteredWebClientBuilder() {
        Set<String> allowedDomains = (configuredAllowedDomains != null && !configuredAllowedDomains.isEmpty())
            ? Set.copyOf(configuredAllowedDomains)
            : DEFAULT_ALLOWED_DOMAINS;

        HttpClient httpClient = createFilteredHttpClient(allowedDomains);

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)); // 10MB
    }

    /**
     * Creates an HttpClient with egress filtering.
     * 
     * @param allowedDomains set of allowed destination domains
     * @return configured HttpClient
     */
    private HttpClient createFilteredHttpClient(Set<String> allowedDomains) {
        ConnectionProvider provider = ConnectionProvider.builder("egress-filtered")
            .maxConnections(maxConnections)
            .pendingAcquireMaxCount(maxPendingAcquires)
            .pendingAcquireTimeout(Duration.ofSeconds(30))
            .maxIdleTime(Duration.ofSeconds(30))
            .maxLifeTime(Duration.ofMinutes(5))
            .evictInBackground(Duration.ofSeconds(120))
            .build();

        HttpClient client = HttpClient.create(provider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
            .responseTimeout(Duration.ofMillis(readTimeoutMs))
            .doOnConnected(conn -> {
                // Validate destination host against allowlist at connection time
                conn.addHandlerLast(new EgressFilterHandler(allowedDomains));
            });

        // Configure TLS
        if (tlsEnabled) {
            try {
                SslContext sslContext = createSslContext();
                client = client.secure(ssl -> ssl.sslContext(sslContext));
            } catch (SSLException e) {
                throw new IllegalStateException("Failed to create SSL context for egress filtering", e);
            }
        }

        return client;
    }

    /**
     * Creates SSL context with proper certificate validation.
     */
    private SslContext createSslContext() throws SSLException {
        SslContextBuilder builder = SslContextBuilder.forClient();

        if (!tlsVerifyHostname) {
            // For development with self-signed certs - NOT for production
            builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
        }

        return builder.build();
    }

    /**
     * Netty handler to enforce egress filtering at connection level.
     * Validates destination host against allowlist.
     */
    private static class EgressFilterHandler extends io.netty.channel.ChannelDuplexHandler {
        private final Set<String> allowedDomains;

        public EgressFilterHandler(Set<String> allowedDomains) {
            this.allowedDomains = allowedDomains;
        }

        @Override
        public void connect(io.netty.channel.ChannelHandlerContext ctx,
                           java.net.SocketAddress remoteAddress,
                           java.net.SocketAddress localAddress,
                           io.netty.channel.ChannelPromise promise) {
            String host = getHost(remoteAddress);
            if (!isAllowed(host, allowedDomains)) {
                promise.setFailure(new SecurityException(
                    "Egress connection denied: " + host + " is not in allowlist"));
                return;
            }
            try {
                super.connect(ctx, remoteAddress, localAddress, promise);
            } catch (Exception e) {
                promise.setFailure(e);
            }
        }

        private boolean isAllowed(String host, Set<String> allowed) {
            if (host == null) return false;
            // Check exact match or subdomain match
            for (String domain : allowed) {
                if (host.equals(domain) || host.endsWith("." + domain)) {
                    return true;
                }
            }
            // Allow localhost for development
            if ("localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host)) {
                return true;
            }
            return false;
        }

        private String getHost(java.net.SocketAddress address) {
            if (address instanceof InetSocketAddress) {
                return ((InetSocketAddress) address).getHostString();
            }
            return null;
        }
    }
}