package com.example.orchestrator.cli.commands;

import com.example.orchestrator.cli.CompanionCli;
import com.example.orchestrator.cli.config.CliConfig;
import com.example.orchestrator.cli.config.ConfigManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Manage CLI configuration.
 */
@Command(
    name = "config",
    description = "Manage CLI configuration",
    mixinStandardHelpOptions = true,
    subcommands = {
        ConfigCommand.Get.class,
        ConfigCommand.SetConfig.class,
        ConfigCommand.List.class,
        ConfigCommand.Show.class,
        ConfigCommand.Path.class
    }
)
public class ConfigCommand implements Callable<Integer> {

    @ParentCommand
    private CompanionCli parent;

    @Spec
    private picocli.CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        // Show help when no subcommand provided
        if (spec != null) {
            spec.commandLine().usage(System.out);
        } else {
            System.out.println("Use 'companion config --help' to see subcommands");
        }
        return 0;
    }

    private ConfigManager getConfigManager() {
        String configPath = parent != null ? parent.getConfigPath() : null;
        return configPath != null ? new ConfigManager(Paths.get(configPath)) : new ConfigManager();
    }

    /**
     * Get a configuration value.
     */
    @Command(name = "get", description = "Get a configuration value")
    public static class Get implements Callable<Integer> {
        public Get() {}

        @Parameters(index = "0", description = "Configuration key (serverUrl, defaultProject, outputFormat, timeoutSeconds, apiKey, logLevel, noColor)")
        private String key;

        @ParentCommand
        private ConfigCommand parentCmd;

        @Override
        public Integer call() throws Exception {
            if (key == null || key.isBlank()) {
                System.err.println("Error: Key is required");
                return 1;
            }

            ConfigManager manager = parentCmd.getConfigManager();
            CliConfig config = manager.load();

            String value = getConfigValue(config, key);
            if (value == null) {
                System.err.println("Error: Unknown config key '" + key + "'");
                System.err.println("Valid keys: serverUrl, defaultProject, outputFormat, timeoutSeconds, apiKey, logLevel, noColor");
                return 1;
            }

            System.out.println(value);
            return 0;
        }

        private String getConfigValue(CliConfig config, String key) {
            return switch (key) {
                case "serverUrl" -> config.serverUrl();
                case "defaultProject" -> config.defaultProject();
                case "outputFormat" -> config.outputFormat();
                case "timeoutSeconds" -> config.timeoutSeconds() != null ? config.timeoutSeconds().toString() : null;
                case "apiKey" -> config.apiKey();
                case "logLevel" -> config.logLevel();
                case "noColor" -> config.noColor() != null ? config.noColor().toString() : null;
                default -> null;
            };
        }
    }

    /**
     * Set a configuration value.
     */
    @Command(name = "set", description = "Set a configuration value")
    public static class SetConfig implements Callable<Integer> {
        public SetConfig() {}

        @Parameters(index = "0", description = "Configuration key (serverUrl, defaultProject, outputFormat, timeoutSeconds, apiKey, logLevel, noColor)")
        private String key;

        @Parameters(index = "1", description = "Configuration value")
        private String value;

        @ParentCommand
        private ConfigCommand parentCmd;

        @Override
        public Integer call() throws Exception {
            if (key == null || key.isBlank()) {
                System.err.println("Error: Key is required");
                return 1;
            }
            if (value == null) {
                System.err.println("Error: Value is required");
                return 1;
            }

            ConfigManager manager = parentCmd.getConfigManager();
            CliConfig current = manager.load();

            CliConfig updated = setConfigValue(current, key, value);
            if (updated == null) {
                System.err.println("Error: Unknown config key '" + key + "'");
                System.err.println("Valid keys: serverUrl, defaultProject, outputFormat, timeoutSeconds, apiKey, logLevel, noColor");
                return 1;
            }

            try {
                updated.validate();
            } catch (IllegalArgumentException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }

            manager.save(updated);
            System.out.println("Set " + key + " = " + value);
            return 0;
        }

        private CliConfig setConfigValue(CliConfig config, String key, String value) {
            return switch (key) {
                case "serverUrl" -> new CliConfig(value, config.defaultProject(), config.outputFormat(), config.timeoutSeconds(), config.apiKey(), config.logLevel(), config.noColor(), config.ssl());
                case "defaultProject" -> new CliConfig(config.serverUrl(), value, config.outputFormat(), config.timeoutSeconds(), config.apiKey(), config.logLevel(), config.noColor(), config.ssl());
                case "outputFormat" -> new CliConfig(config.serverUrl(), config.defaultProject(), value, config.timeoutSeconds(), config.apiKey(), config.logLevel(), config.noColor(), config.ssl());
                case "timeoutSeconds" -> {
                    try {
                        yield new CliConfig(config.serverUrl(), config.defaultProject(), config.outputFormat(), Integer.parseInt(value), config.apiKey(), config.logLevel(), config.noColor(), config.ssl());
                    } catch (NumberFormatException e) {
                        yield null;
                    }
                }
                case "apiKey" -> new CliConfig(config.serverUrl(), config.defaultProject(), config.outputFormat(), config.timeoutSeconds(), value, config.logLevel(), config.noColor(), config.ssl());
                case "logLevel" -> new CliConfig(config.serverUrl(), config.defaultProject(), config.outputFormat(), config.timeoutSeconds(), config.apiKey(), value.toUpperCase(), config.noColor(), config.ssl());
                case "noColor" -> {
                    Boolean boolValue = switch (value.toLowerCase()) {
                        case "true", "yes", "1", "on" -> true;
                        case "false", "no", "0", "off" -> false;
                        default -> null;
                    };
                    if (boolValue == null) yield null;
                    yield new CliConfig(config.serverUrl(), config.defaultProject(), config.outputFormat(), config.timeoutSeconds(), config.apiKey(), config.logLevel(), boolValue, config.ssl());
                }
                default -> null;
            };
        }
    }

    /**
     * List all configuration values.
     */
    @Command(name = "list", description = "List all configuration values")
    public static class List implements Callable<Integer> {
        public List() {}

        @Option(names = {"--output-format"}, description = "Output format: table, json, markdown")
        private String outputFormat = "table";

        @ParentCommand
        private ConfigCommand parentCmd;

        @Override
        public Integer call() throws Exception {
            ConfigManager manager = parentCmd.getConfigManager();
            CliConfig config = manager.load();

            Map<String, String> values = new LinkedHashMap<>();
            values.put("serverUrl", config.serverUrl());
            values.put("defaultProject", config.defaultProject().isEmpty() ? "(empty)" : config.defaultProject());
            values.put("outputFormat", config.outputFormat());
            values.put("timeoutSeconds", config.timeoutSeconds() != null ? config.timeoutSeconds().toString() : "(default)");
            values.put("apiKey", config.apiKey().isEmpty() ? "(empty)" : maskApiKey(config.apiKey()));
            values.put("logLevel", config.logLevel());
            values.put("noColor", config.noColor() != null ? config.noColor().toString() : "(default)");

            switch (outputFormat.toLowerCase()) {
                case "json" -> printJson(values);
                case "markdown" -> printMarkdown(values);
                case "plain" -> printPlain(values);
                default -> printTable(values);
            }
            return 0;
        }

        private void printTable(Map<String, String> values) {
            int maxKeyLen = values.keySet().stream().mapToInt(String::length).max().orElse(10);
            System.out.printf("%-" + (maxKeyLen + 2) + "s %s%n", "KEY", "VALUE");
            System.out.println("-".repeat(maxKeyLen + 40));
            values.forEach((k, v) -> System.out.printf("%-" + (maxKeyLen + 2) + "s %s%n", k, v));
        }

        private void printJson(Map<String, String> values) {
            StringBuilder json = new StringBuilder("{\n");
            java.util.Set<String> keys = values.keySet();
            int i = 0;
            for (String key : keys) {
                json.append("  \"").append(key).append("\": \"").append(escapeJson(values.get(key))).append("\"");
                if (i < keys.size() - 1) json.append(",");
                json.append("\n");
                i++;
            }
            json.append("}");
            System.out.println(json);
        }

        private void printMarkdown(Map<String, String> values) {
            System.out.println("| Key | Value |");
            System.out.println("|-----|-------|");
            values.forEach((k, v) -> System.out.println("| " + k + " | " + v + " |"));
        }

        private void printPlain(Map<String, String> values) {
            values.forEach((k, v) -> System.out.println(k + "=" + v));
        }

        private String escapeJson(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        }

        private String maskApiKey(String apiKey) {
            if (apiKey.length() <= 8) return "********";
            return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
        }
    }

    /**
     * Show all configuration values (alias for list).
     */
    @Command(name = "show", description = "Show all configuration values (alias for list)")
    public static class Show implements Callable<Integer> {
        public Show() {}

        @Option(names = {"--output-format"}, description = "Output format: table, json, markdown")
        private String outputFormat = "table";

        @ParentCommand
        private ConfigCommand parentCmd;

        @Override
        public Integer call() throws Exception {
            ConfigManager manager = parentCmd.getConfigManager();
            CliConfig config = manager.load();

            Map<String, String> values = new LinkedHashMap<>();
            values.put("serverUrl", config.serverUrl());
            values.put("defaultProject", config.defaultProject().isEmpty() ? "(empty)" : config.defaultProject());
            values.put("outputFormat", config.outputFormat());
            values.put("timeoutSeconds", config.timeoutSeconds() != null ? config.timeoutSeconds().toString() : "(default)");
            values.put("apiKey", config.apiKey().isEmpty() ? "(empty)" : maskApiKey(config.apiKey()));
            values.put("logLevel", config.logLevel());
            values.put("noColor", config.noColor() != null ? config.noColor().toString() : "(default)");

            switch (outputFormat.toLowerCase()) {
                case "json" -> printJson(values);
                case "markdown" -> printMarkdown(values);
                case "plain" -> printPlain(values);
                default -> printTable(values);
            }
            return 0;
        }

        private void printTable(Map<String, String> values) {
            int maxKeyLen = values.keySet().stream().mapToInt(String::length).max().orElse(10);
            System.out.printf("%-" + (maxKeyLen + 2) + "s %s%n", "KEY", "VALUE");
            System.out.println("-".repeat(maxKeyLen + 40));
            values.forEach((k, v) -> System.out.printf("%-" + (maxKeyLen + 2) + "s %s%n", k, v));
        }

        private void printJson(Map<String, String> values) {
            StringBuilder json = new StringBuilder("{\n");
            java.util.Set<String> keys = values.keySet();
            int i = 0;
            for (String key : keys) {
                json.append("  \"").append(key).append("\": \"").append(escapeJson(values.get(key))).append("\"");
                if (i < keys.size() - 1) json.append(",");
                json.append("\n");
                i++;
            }
            json.append("}");
            System.out.println(json);
        }

        private void printMarkdown(Map<String, String> values) {
            System.out.println("| Key | Value |");
            System.out.println("|-----|-------|");
            values.forEach((k, v) -> System.out.println("| " + k + " | " + v + " |"));
        }

        private void printPlain(Map<String, String> values) {
            values.forEach((k, v) -> System.out.println(k + "=" + v));
        }

        private String escapeJson(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        }

        private String maskApiKey(String apiKey) {
            if (apiKey.length() <= 8) return "********";
            return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
        }
    }

    /**
     * Show the config file path.
     */
    @Command(name = "path", description = "Show the config file path")
    public static class Path implements Callable<Integer> {
        public Path() {}

        @ParentCommand
        private ConfigCommand parentCmd;

        @Override
        public Integer call() throws Exception {
            ConfigManager manager = parentCmd.getConfigManager();
            System.out.println(manager.getConfigFile().toString());
            return 0;
        }
    }
}