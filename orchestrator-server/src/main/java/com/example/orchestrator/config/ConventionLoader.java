package com.example.orchestrator.config;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ConventionLoader {

    private final Yaml yaml = new Yaml();

    public ProjectConventions load(Path conventionsFile) throws IOException {
        try (InputStream is = Files.newInputStream(conventionsFile)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> config = yaml.load(is);
            return parseConventions(config);
        }
    }

    public ProjectConventions loadFromClasspath() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("conventions.yaml")) {
            if (is == null) {
                return defaultConventions();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> config = yaml.load(is);
            return parseConventions(config);
        }
    }

    private ProjectConventions parseConventions(Map<String, Object> config) {
        if (config == null) {
            return defaultConventions();
        }

        String packageStructure = (String) config.getOrDefault("package_structure", "com.{domain}.{module}.{layer}");
        String domain = (String) config.getOrDefault("domain", "app");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> modules = (Map<String, Object>) config.getOrDefault("modules", Map.of());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> defaults = (Map<String, Object>) config.getOrDefault("defaults", Map.of());

        return new ProjectConventions(
            packageStructure,
            domain,
            modules,
            defaults
        );
    }

    private ProjectConventions defaultConventions() {
        return new ProjectConventions(
            "com.{domain}.{module}.{layer}",
            "app",
            Map.of(),
            Map.of()
        );
    }

    public record ProjectConventions(
        String packageStructure,
        String domain,
        Map<String, Object> modules,
        Map<String, Object> defaults
    ) {
        public ModuleConventions getModule(String moduleName) {
            @SuppressWarnings("unchecked")
            Map<String, Object> moduleConfig = (Map<String, Object>) modules.get(moduleName);
            if (moduleConfig == null) {
                return new ModuleConventions(List.of(), Map.of(), Map.of(), Map.of());
            }
            
            @SuppressWarnings("unchecked")
            List<String> layers = (List<String>) moduleConfig.getOrDefault("layers", List.of());
            
            @SuppressWarnings("unchecked")
            Map<String, String> naming = (Map<String, String>) moduleConfig.getOrDefault("naming", Map.of());
            
            @SuppressWarnings("unchecked")
            Map<String, String> patterns = (Map<String, String>) moduleConfig.getOrDefault("patterns", Map.of());

            return new ModuleConventions(layers, naming, patterns, Map.of());
        }

        public String getDefault(String key) {
            return (String) defaults.getOrDefault(key, "");
        }
    }

    public record ModuleConventions(
        List<String> layers,
        Map<String, String> naming,
        Map<String, String> patterns,
        Map<String, Object> extra
    ) {}
}