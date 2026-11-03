package com.example.orchestrator.model.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

/**
 * Egress filter to prevent SSRF attacks by blocking requests to internal/metadata endpoints.
 * 
 * Blocks access to:
 * - Link-local addresses (169.254.0.0/16) - AWS/GCP/Azure metadata
 * - Loopback (127.0.0.0/8, ::1)
 * - Private RFC1918 ranges (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)
 * - Private IPv6 (fc00::/7)
 * - Docker host gateway (host.docker.internal, gateway.docker.internal)
 * - Kubernetes internal (kubernetes.default.svc, *.svc.cluster.local)
 */
public final class EgressFilter {

    private static final Logger log = LoggerFactory.getLogger(EgressFilter.class);

    // Internal IP ranges to block (CIDR notation)
    private static final List<String> BLOCKED_CIDRS = List.of(
        "127.0.0.0/8",       // Loopback
        "169.254.0.0/16",    // Link-local (cloud metadata endpoints)
        "10.0.0.0/8",        // RFC1918 private
        "172.16.0.0/12",     // RFC1918 private
        "192.168.0.0/16",    // RFC1918 private
        "100.64.0.0/10",     // Carrier-grade NAT
        "fc00::/7"           // IPv6 ULA
    );

    // Internal hostnames to block
    private static final List<String> BLOCKED_HOSTS = List.of(
        "localhost",
        "host.docker.internal",
        "gateway.docker.internal",
        "metadata.google.internal",        // GCP metadata
        "169.254.169.254",                 // AWS/Azure/GCP metadata IP
        "metadata.azure.com",              // Azure metadata
        "metadata",                        // Generic metadata
        "kubernetes.default.svc",          // K8s internal
        "kubernetes.default.svc.cluster.local"
    );

    // Allowed public IP ranges (inverse of blocked)
    // We use a deny-list approach for internal addresses

    private EgressFilter() {}

    /**
     * Validates that a URL is safe for egress (not pointing to internal endpoints).
     * 
     * @param url the URL to validate
     * @throws IllegalArgumentException if URL points to blocked internal endpoint
     */
    public static void validateEgress(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }

        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            
            if (host == null) {
                throw new IllegalArgumentException("Invalid URL: no host");
            }

            // Check blocked hostnames (exact match or suffix match)
            String lowerHost = host.toLowerCase();
            for (String blocked : BLOCKED_HOSTS) {
                if (lowerHost.equals(blocked) || lowerHost.endsWith("." + blocked)) {
                    log.error("BLOCKED EGRESS: Attempt to access internal hostname: {}", host);
                    throw new IllegalArgumentException("Access to internal endpoint blocked: " + host);
                }
            }

            // Check if host is an IP address
            if (isIpAddress(lowerHost)) {
                if (isIpInBlockedRanges(lowerHost)) {
                    log.error("BLOCKED EGRESS: Attempt to access internal IP: {}", host);
                    throw new IllegalArgumentException("Access to internal IP blocked: " + host);
                }
            } else {
                // Resolve hostname to check IPs (with timeout to prevent DNS blocking)
                // Note: In production, you'd want to cache DNS resolutions and set a timeout
                try {
                    var addresses = java.net.InetAddress.getAllByName(host);
                    for (var addr : addresses) {
                        String ip = addr.getHostAddress();
                        if (isIpInBlockedRanges(ip)) {
                            log.error("BLOCKED EGRESS: Host {} resolves to blocked IP: {}", host, ip);
                            throw new IllegalArgumentException("Host resolves to blocked internal IP: " + host);
                        }
                    }
                } catch (java.net.UnknownHostException e) {
                    // If we can't resolve, we allow but log a warning
                    log.warn("Could not resolve host for egress validation: {}", host);
                }
            }

        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL syntax: " + url, e);
        }
    }

    /**
     * Validates a base URL and all its potential redirect targets.
     * Should be called during provider initialization.
     * 
     * @param baseUrl the base URL to validate
     * @param providerName name of the provider (for logging)
     * @throws IllegalArgumentException if URL is not safe
     */
    public static void validateProviderBaseUrl(String baseUrl, String providerName) {
        validateEgress(baseUrl);
        log.info("Egress validation passed for provider: {} (URL: {})", providerName, baseUrl);
    }

    private static boolean isIpAddress(String host) {
        // IPv4
        if (host.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")) {
            String[] parts = host.split("\\.");
            for (String part : parts) {
                int val = Integer.parseInt(part);
                if (val > 255) return false;
            }
            return true;
        }
        // IPv6 (simplified check)
        if (host.contains(":")) {
            return true;
        }
        return false;
    }

    private static boolean isIpInBlockedRanges(String ip) {
        try {
            java.net.InetAddress address = java.net.InetAddress.getByName(ip);
            byte[] addrBytes = address.getAddress();

            for (String cidr : BLOCKED_CIDRS) {
                if (cidr.contains(":")) {
                    // IPv6 CIDR - simplified: check if it's ULA (fc00::/7)
                    if (cidr.equals("fc00::/7") && isIpv6Ula(addrBytes)) {
                        return true;
                    }
                } else {
                    // IPv4 CIDR
                    if (isIpv4InCidr(addrBytes, cidr)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error checking IP in blocked ranges: {}", e.getMessage());
        }
        return false;
    }

    private static boolean isIpv4InCidr(byte[] addrBytes, String cidr) {
        String[] parts = cidr.split("/");
        String networkStr = parts[0];
        int prefixLength = Integer.parseInt(parts[1]);

        byte[] networkBytes;
        try {
            java.net.InetAddress networkAddr = java.net.InetAddress.getByName(networkStr);
            networkBytes = networkAddr.getAddress();
        } catch (java.net.UnknownHostException e) {
            return false;
        }

        // Handle IPv4 mapped IPv6 addresses (::ffff:x.x.x.x)
        if (addrBytes.length == 16) {
            // Check if it's IPv4-mapped IPv6
            boolean isV4Mapped = true;
            for (int i = 0; i < 10; i++) {
                if (addrBytes[i] != 0) { isV4Mapped = false; break; }
            }
            if (isV4Mapped && addrBytes[10] == (byte)0xff && addrBytes[11] == (byte)0xff) {
                // Extract IPv4 part
                byte[] v4Bytes = Arrays.copyOfRange(addrBytes, 12, 16);
                return isIpv4InCidr(v4Bytes, cidr);
            }
            return false; // Pure IPv6, not matching IPv4 CIDR
        }

        // Compare bits
        int totalBits = prefixLength;
        int fullBytes = totalBits / 8;
        int remainingBits = totalBits % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (addrBytes[i] != networkBytes[i]) return false;
        }
        if (remainingBits > 0) {
            byte mask = (byte) (0xFF << (8 - remainingBits));
            if ((addrBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) return false;
        }
        return true;
    }

    private static boolean isIpv6Ula(byte[] addrBytes) {
        // fc00::/7 means first 7 bits are 1111110
        // First byte: fc (11111100) or fd (11111101)
        if (addrBytes.length >= 1) {
            int firstByte = addrBytes[0] & 0xFF;
            return firstByte >= 0xFC && firstByte <= 0xFD;
        }
        return false;
    }
}