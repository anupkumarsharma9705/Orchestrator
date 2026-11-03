package com.example.orchestrator.cli.config;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Manages CLI configuration persistence.
 * Loads/saves YAML at ~/.companion/config.yaml
 */
@Component
public class ConfigManager {

    private static final String CONFIG_DIR_NAME = ".companion";
    private static final String CONFIG_FILE_NAME = "config.yaml";

    private final Path configDir;
    private final Path configFile;
    private final Yaml yaml;

    public ConfigManager() {
        this(Paths.get(System.getProperty("user.home"), CONFIG_DIR_NAME, CONFIG_FILE_NAME));
    }

    public ConfigManager(Path configFile) {
        this.configFile = configFile.toAbsolutePath().normalize();
        this.configDir = this.configFile.getParent();

        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Representer representer = new Representer(dumperOptions);
        representer.getPropertyUtils().setSkipMissingProperties(true);
        this.yaml = new Yaml(representer);
    }

    /**
     * Load configuration from file, falling back to defaults.
     */
    public CliConfig load() {
        if (!Files.exists(configFile)) {
            return CliConfig.defaults();
        }

        try {
            String content = Files.readString(configFile);
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = yaml.load(content);
            if (raw == null || raw.isEmpty()) {
                return applyEnvironment(CliConfig.defaults());
            }
            return applyEnvironment(mapToConfig(raw));
        } catch (IOException e) {
            System.err.println("Warning: Failed to load config from " + configFile + ": " + e.getMessage());
            return applyEnvironment(CliConfig.defaults());
        }
    }

    /**
     * Environment variables override file values. Passwords should preferably be
     * supplied this way so they never have to be written to disk.
     */
    private CliConfig applyEnvironment(CliConfig config) {
        String serverUrl = envOrNull("COMPANION_SERVER_URL");
        String apiKey = envOrNull("COMPANION_API_KEY");
        String trustStore = envOrNull("COMPANION_SSL_TRUSTSTORE");
        String trustStorePassword = envOrNull("COMPANION_SSL_TRUSTSTORE_PASSWORD");
        String keyStore = envOrNull("COMPANION_SSL_KEYSTORE");
        String keyStorePassword = envOrNull("COMPANION_SSL_KEYSTORE_PASSWORD");
        String mtls = envOrNull("COMPANION_SSL_MTLS_ENABLED");

        if (serverUrl == null && apiKey == null && trustStore == null
                && trustStorePassword == null && keyStore == null
                && keyStorePassword == null && mtls == null) {
            return config;
        }

        CliConfig.SslConfig ssl = config.ssl() != null ? config.ssl() : CliConfig.SslConfig.defaults();
        CliConfig.SslConfig updatedSsl = new CliConfig.SslConfig(
            ssl.enabled(),
            trustStore != null ? trustStore : ssl.trustStore(),
            trustStorePassword != null ? trustStorePassword : ssl.trustStorePassword(),
            ssl.trustStoreType(),
            ssl.verifyHostname(),
            mtls != null ? Boolean.parseBoolean(mtls) : ssl.mtlsEnabled(),
            keyStore != null ? keyStore : ssl.keyStore(),
            keyStorePassword != null ? keyStorePassword : ssl.keyStorePassword(),
            ssl.keyStoreType(),
            ssl.keyAlias()
        );

        return new CliConfig(
            serverUrl != null ? serverUrl : config.serverUrl(),
            config.defaultProject(),
            config.outputFormat(),
            config.timeoutSeconds(),
            apiKey != null ? apiKey : config.apiKey(),
            config.logLevel(),
            config.noColor(),
            updatedSsl
        );
    }

    private static String envOrNull(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }

    private CliConfig mapToConfig(Map<String, Object> raw) {
        String serverUrl = getString(raw, "serverUrl", CliConfig.defaults().serverUrl());
        String defaultProject = getString(raw, "defaultProject", CliConfig.defaults().defaultProject());
        String outputFormat = getString(raw, "outputFormat", CliConfig.defaults().outputFormat());
        Integer timeoutSeconds = getInteger(raw, "timeoutSeconds", CliConfig.defaults().timeoutSeconds());
        String apiKey = getString(raw, "apiKey", CliConfig.defaults().apiKey());
        String logLevel = getString(raw, "logLevel", CliConfig.defaults().logLevel());
        Boolean noColor = getBoolean(raw, "noColor", CliConfig.defaults().noColor());
        
        // Parse SSL config if present
        CliConfig.SslConfig ssl = parseSslConfig(raw);

        return new CliConfig(serverUrl, defaultProject, outputFormat, timeoutSeconds, apiKey, logLevel, noColor, ssl);
    }

    private CliConfig.SslConfig parseSslConfig(Map<String, Object> raw) {
        Object sslRaw = raw.get("ssl");
        if (sslRaw == null || !(sslRaw instanceof Map)) {
            return CliConfig.SslConfig.defaults();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> sslMap = (Map<String, Object>) sslRaw;
        
        Boolean enabled = getBoolean(sslMap, "enabled", CliConfig.SslConfig.defaults().enabled());
        String trustStore = getString(sslMap, "trustStore", CliConfig.SslConfig.defaults().trustStore());
        String trustStorePassword = getString(sslMap, "trustStorePassword", CliConfig.SslConfig.defaults().trustStorePassword());
        String trustStoreType = getString(sslMap, "trustStoreType", CliConfig.SslConfig.defaults().trustStoreType());
        Boolean verifyHostname = getBoolean(sslMap, "verifyHostname", CliConfig.SslConfig.defaults().verifyHostname());
        
        // mTLS fields
        Boolean mtlsEnabled = getBoolean(sslMap, "mtlsEnabled", CliConfig.SslConfig.defaults().mtlsEnabled());
        String keyStore = getString(sslMap, "keyStore", CliConfig.SslConfig.defaults().keyStore());
        String keyStorePassword = getString(sslMap, "keyStorePassword", CliConfig.SslConfig.defaults().keyStorePassword());
        String keyStoreType = getString(sslMap, "keyStoreType", CliConfig.SslConfig.defaults().keyStoreType());
        String keyAlias = getString(sslMap, "keyAlias", CliConfig.SslConfig.defaults().keyAlias());
        
        return new CliConfig.SslConfig(
            enabled, trustStore, trustStorePassword, trustStoreType, verifyHostname,
            mtlsEnabled, keyStore, keyStorePassword, keyStoreType, keyAlias
        );
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return (value != null && value instanceof String) ? (String) value : defaultValue;
    }

    private Integer getInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Boolean getBoolean(Map<String, Object> map, String key, Boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) {
            String s = ((String) value).toLowerCase();
            if (Set.of("true", "yes", "1", "on").contains(s)) return true;
            if (Set.of("false", "no", "0", "off").contains(s)) return false;
        }
        return defaultValue;
    }

    /**
     * Save configuration to file.
     */
    public void save(CliConfig config) {
        try {
            Files.createDirectories(configDir);

            // Use LinkedHashMap to preserve field order in YAML
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("serverUrl", config.serverUrl());
            data.put("defaultProject", config.defaultProject());
            data.put("outputFormat", config.outputFormat());
            data.put("timeoutSeconds", config.timeoutSeconds());
            data.put("apiKey", config.apiKey());
            data.put("logLevel", config.logLevel());
            data.put("noColor", config.noColor());

            if (config.ssl() != null) {
                CliConfig.SslConfig ssl = config.ssl();
                Map<String, Object> sslData = new LinkedHashMap<>();
                sslData.put("enabled", ssl.enabled());
                sslData.put("trustStore", ssl.trustStore());
                sslData.put("trustStorePassword", ssl.trustStorePassword());
                sslData.put("trustStoreType", ssl.trustStoreType());
                sslData.put("verifyHostname", ssl.verifyHostname());
                sslData.put("mtlsEnabled", ssl.mtlsEnabled());
                sslData.put("keyStore", ssl.keyStore());
                sslData.put("keyStorePassword", ssl.keyStorePassword());
                sslData.put("keyStoreType", ssl.keyStoreType());
                sslData.put("keyAlias", ssl.keyAlias());
                data.put("ssl", sslData);
            }

            String yamlContent = yaml.dump(data);
            Files.writeString(configFile, yamlContent);

            // The config may contain keystore passwords; restrict it to the owner.
            try {
                Files.setPosixFilePermissions(configFile,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException | IOException ignored) {
                // Non-POSIX filesystem (for example Windows): ACL inheritance applies.
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save config to " + configFile, e);
        }
    }

    /**
     * Get the config file path.
     */
    public Path getConfigFile() {
        return configFile;
    }

    /**
     * Check if config file exists.
     */
    public boolean exists() {
        return Files.exists(configFile);
    }

    /**
     * Delete the config file.
     */
    public void delete() {
        try {
            Files.deleteIfExists(configFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete config file", e);
        }
    }
}