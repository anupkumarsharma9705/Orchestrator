package com.example.orchestrator.cli.security;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Certificate Pinning Manager for CLI.
 * 
 * Implements certificate pinning to prevent MITM attacks by verifying
 * the server's certificate matches expected fingerprints.
 * 
 * Supports:
 * - SHA-256 pinning (recommended)
 * - Multiple pins per host (for rotation)
 * - Pin file configuration
 * - HPKP-style backup pins
 */
public class CertificatePinningManager {

    private static final Logger log = LoggerFactory.getLogger(CertificatePinningManager.class);

    // Pin format: "sha256/<base64-encoded-sha256>"
    private static final String PIN_PREFIX = "sha256/";

    private final Map<String, Set<String>> pinnedCerts = new HashMap<>();
    private boolean enforcePinning;

    public CertificatePinningManager() {
        this(false);
    }

    public CertificatePinningManager(boolean enforcePinning) {
        this.enforcePinning = enforcePinning;
    }

    /**
     * Load pins from a configuration file.
     * File format: one pin per line as "hostname sha256/<base64>"
     */
    public void loadPinsFromFile(Path pinFile) throws IOException {
        if (!Files.exists(pinFile)) {
            log.warn("Pin file not found: {}", pinFile);
            return;
        }

        String content = Files.readString(pinFile, StandardCharsets.UTF_8);
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            String[] parts = line.split("\\s+", 2);
            if (parts.length != 2) {
                log.warn("Invalid pin line: {}", line);
                continue;
            }
            
            String hostname = parts[0].trim();
            String pin = parts[1].trim();
            
            if (!pin.startsWith(PIN_PREFIX)) {
                log.warn("Invalid pin format (must start with sha256/): {}", pin);
                continue;
            }
            
            addPin(hostname, pin);
            log.info("Loaded pin for {}: {}", hostname, pin);
        }
    }

    /**
     * Add a pin for a hostname.
     */
    public void addPin(String hostname, String pin) {
        if (!pin.startsWith(PIN_PREFIX)) {
            throw new IllegalArgumentException("Pin must start with " + PIN_PREFIX);
        }
        pinnedCerts.computeIfAbsent(hostname, k -> new HashSet<>()).add(pin);
        log.debug("Added pin for {}: {}", hostname, pin);
    }

    /**
     * Remove a pin for a hostname.
     */
    public void removePin(String hostname, String pin) {
        Set<String> pins = pinnedCerts.get(hostname);
        if (pins != null) {
            pins.remove(pin);
            if (pins.isEmpty()) {
                pinnedCerts.remove(hostname);
            }
        }
    }

    /**
     * Verify a certificate chain against pinned certificates.
     * Called during SSL handshake via custom TrustManager or Netty handler.
     * 
     * @param hostname The hostname being connected to
     * @param certificates The certificate chain presented by the server
     * @throws CertificatePinningException if pinning fails
     */
    public void verifyPins(String hostname, Certificate[] certificates) throws CertificatePinningException {
        if (!enforcePinning) {
            log.debug("Pinning not enforced, skipping verification for {}", hostname);
            return;
        }

        Set<String> expectedPins = pinnedCerts.get(hostname);
        if (expectedPins == null || expectedPins.isEmpty()) {
            // No pins configured for this host - skip verification
            log.debug("No pins configured for {}, skipping verification", hostname);
            return;
        }

        boolean pinMatched = false;
        StringBuilder matchedPinInfo = new StringBuilder();

        for (Certificate cert : certificates) {
            if (!(cert instanceof X509Certificate x509Cert)) {
                continue;
            }

            String pin = computePin(x509Cert);
            if (expectedPins.contains(pin)) {
                pinMatched = true;
                matchedPinInfo.append("Matched pin: ").append(pin).append(" (").append(getSubjectDN(x509Cert)).append(")");
                break;
            }
        }

        if (!pinMatched) {
            String errorMsg = String.format(
                "Certificate pinning failed for %s. Expected one of: %s. Got: %s",
                hostname,
                expectedPins,
                getPresentedPins(certificates)
            );
            log.error("Certificate pinning failure: {}", errorMsg);
            throw new CertificatePinningException(errorMsg);
        }

        log.debug("Certificate pinning verified for {}: {}", hostname, matchedPinInfo);
    }

    /**
     * Verify pins during SSL handshake using a custom hostname verifier.
     */
    public void verifyPins(String hostname, java.security.cert.X509Certificate[] certs) throws CertificatePinningException {
        if (!enforcePinning) {
            return;
        }
        
        Set<String> expectedPins = pinnedCerts.get(hostname);
        if (expectedPins == null || expectedPins.isEmpty()) {
            return; // No pins configured
        }

        boolean pinMatched = false;
        for (java.security.cert.X509Certificate cert : certs) {
            String pin = computePin(cert);
            if (expectedPins.contains(pin)) {
                pinMatched = true;
                break;
            }
        }

        if (!pinMatched) {
            throw new CertificatePinningException(
                String.format("Certificate pinning failed for %s. Expected one of: %s", 
                    hostname, expectedPins));
        }
    }

    /**
     * Compute SHA-256 pin for a certificate.
     * Format: sha256/<base64-encoded-sha256>
     */
    public static String computePin(X509Certificate cert) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(cert.getEncoded());
            String base64 = Base64.getEncoder().encodeToString(hash);
            return PIN_PREFIX + base64;
        } catch (CertificateEncodingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to compute certificate pin", e);
        }
    }

    /**
     * Compute pins for a certificate chain.
     */
    public static Set<String> computePins(Certificate[] certificates) {
        Set<String> pins = new HashSet<>();
        for (Certificate cert : certificates) {
            if (cert instanceof X509Certificate x509) {
                pins.add(computePin(x509));
            }
        }
        return pins;
    }

    private String getSubjectDN(X509Certificate cert) {
        return cert.getSubjectX500Principal().getName();
    }

    private String getPresentedPins(Certificate[] certificates) {
        StringBuilder sb = new StringBuilder();
        for (Certificate cert : certificates) {
            if (cert instanceof X509Certificate x509) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(computePin(x509));
            }
        }
        return sb.toString();
    }

    /**
     * Generate pin for a server by connecting to it.
     * Useful for initial pin generation (TOFU - Trust On First Use).
     */
    public static String generatePinForHost(String hostname, int port) throws IOException, CertificateException, NoSuchAlgorithmException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(hostname, port), 10000);
            // For TLS, we'd need to use SSLSocket
            // This is a simplified version - in practice use SSLSocketFactory
            throw new UnsupportedOperationException("Use SSLSocket for TLS connections");
        }
    }

    /**
     * Generate pin for a host using TLS.
     */
    public static Set<String> generatePinsForHost(String hostname, int port) throws Exception {
        Set<String> pins = new HashSet<>();
        
        javax.net.ssl.SSLSocketFactory factory = (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
        try (javax.net.ssl.SSLSocket socket = (javax.net.ssl.SSLSocket) factory.createSocket(hostname, port)) {
            socket.startHandshake();
            Certificate[] certs = socket.getSession().getPeerCertificates();
            
            for (Certificate cert : certs) {
                if (cert instanceof X509Certificate x509) {
                    pins.add(computePin(x509));
                }
            }
        }
        
        return pins;
    }

    /**
     * Check if pinning is enforced.
     */
    public boolean isEnforcePinning() {
        return enforcePinning;
    }

    /**
     * Set pinning enforcement mode.
     */
    public void setEnforcePinning(boolean enforce) {
        this.enforcePinning = enforce;
    }

    /**
     * Get all configured pins for a hostname.
     */
    public Set<String> getPins(String hostname) {
        return pinnedCerts.getOrDefault(hostname, Set.of());
    }

    /**
     * Get all pinned hosts.
     */
    public Set<String> getPinnedHosts() {
        return pinnedCerts.keySet();
    }

    /**
     * Exception thrown when certificate pinning fails.
     */
    public static class CertificatePinningException extends Exception {
        public CertificatePinningException(String message) {
            super(message);
        }
    }
}