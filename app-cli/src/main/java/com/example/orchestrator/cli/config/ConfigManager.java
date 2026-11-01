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
                return CliConfig.defaults();
            }
            return mapToConfig(raw);
        } catch (IOException e) {
            System.err.println("Warning: Failed to load config from " + configFile + ": " + e.getMessage());
            return CliConfig.defaults();
        }
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
        
        return new CliConfig.SslConfig(enabled, trustStore, trustStorePassword, trustStoreType, verifyHostname);
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

            String yamlContent = yaml.dump(data);
            Files.writeString(configFile, yamlContent);
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