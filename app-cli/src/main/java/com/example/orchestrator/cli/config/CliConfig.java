package com.example.orchestrator.cli.config;

import java.util.Set;

/**
 * CLI configuration record.
 * Persisted as YAML at ~/.companion/config.yaml
 */
public record CliConfig(
    /** Orchestrator server REST API URL */
    String serverUrl,

    /** Default project name (used when not specified in commands) */
    String defaultProject,

    /** Output format: table, json, markdown */
    String outputFormat,

    /** Default timeout for long-running operations (seconds) */
    Integer timeoutSeconds,

    /** API key for authentication (optional) */
    String apiKey,

    /** Log level: DEBUG, INFO, WARN, ERROR */
    String logLevel,

    /** Disable colored output */
    Boolean noColor,

    /** SSL/TLS configuration */
    SslConfig ssl
) {

    /**
     * Default configuration values.
     */
    public static CliConfig defaults() {
        return new CliConfig(
            "https://localhost:8443",
            "",
            "table",
            600,
            "",
            "INFO",
            false,
            SslConfig.defaults()
        );
    }

    /**
     * Merge this config with another, using non-null/non-empty values from other.
     */
    public CliConfig merge(CliConfig other) {
        if (other == null) return this;
        return new CliConfig(
            nonEmpty(other.serverUrl) ? other.serverUrl : this.serverUrl,
            nonEmpty(other.defaultProject) ? other.defaultProject : this.defaultProject,
            nonEmpty(other.outputFormat) ? other.outputFormat : this.outputFormat,
            other.timeoutSeconds != null ? other.timeoutSeconds : this.timeoutSeconds,
            nonEmpty(other.apiKey) ? other.apiKey : this.apiKey,
            nonEmpty(other.logLevel) ? other.logLevel : this.logLevel,
            other.noColor != null ? other.noColor : this.noColor,
            other.ssl != null ? other.ssl : this.ssl
        );
    }

    private static boolean nonEmpty(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Validate the configuration.
     * @throws IllegalArgumentException if validation fails
     */
    public void validate() {
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalArgumentException("serverUrl cannot be empty");
        }
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            throw new IllegalArgumentException("serverUrl must start with http:// or https://");
        }
        if (outputFormat != null && !Set.of("table", "json", "markdown", "plain").contains(outputFormat)) {
            throw new IllegalArgumentException("outputFormat must be one of: table, json, markdown, plain");
        }
        if (timeoutSeconds != null && timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
        if (logLevel != null && !Set.of("DEBUG", "INFO", "WARN", "ERROR").contains(logLevel.toUpperCase())) {
            throw new IllegalArgumentException("logLevel must be one of: DEBUG, INFO, WARN, ERROR");
        }
    }

    /**
     * SSL/TLS configuration for secure communication with the orchestrator server.
     * Supports both server verification (trust store) and client authentication (mTLS).
     */
    public record SslConfig(
        /** Enable SSL/TLS for server communication */
        Boolean enabled,

        /** Path to trust store (JKS or PKCS12) - for server certificate verification */
        String trustStore,

        /** Trust store password */
        String trustStorePassword,

        /** Trust store type (JKS, PKCS12) */
        String trustStoreType,

        /** Verify hostname against certificate (disable for self-signed dev certs) */
        Boolean verifyHostname,

        // mTLS Client Authentication
        /** Enable mutual TLS (client certificate authentication) */
        Boolean mtlsEnabled,

        /** Path to client key store (JKS or PKCS12) containing client certificate and private key */
        String keyStore,

        /** Key store password */
        String keyStorePassword,

        /** Key store type (JKS, PKCS12) */
        String keyStoreType,

        /** Client certificate alias in key store */
        String keyAlias
    ) {
        public static SslConfig defaults() {
            return new SslConfig(
                true,
                "",     // Configured by scripts/dev-up or explicitly by the user
                "",     // Must be supplied via config file or env; never shipped
                "PKCS12",
                true,   // Always verify the server certificate hostname
                false,  // mTLS disabled by default
                "",     // No client key store by default
                "",     // No key store password
                "PKCS12",
                ""      // No key alias
            );
        }

        public static SslConfig mtlsDefaults() {
            return new SslConfig(
                true,
                "",
                "",
                "PKCS12",
                true,
                true,   // mTLS enabled
                "",     // Configured by the dev bootstrap or config command
                "",
                "PKCS12",
                "client"
            );
        }
    }
}