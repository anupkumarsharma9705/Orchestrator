package com.example.orchestrator.eval.runner;

import com.example.orchestrator.eval.model.GoldenSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Loads golden specs from YAML/JSON files.
 */
@Component
public class SpecLoader {

    private static final Logger log = LoggerFactory.getLogger(SpecLoader.class);

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Value("${orchestrator.eval.specs-dir:eval/specs}")
    private String specsDir;

    /**
     * Loads all golden specs from the specs directory.
     * Supports .yaml, .yml, and .json files.
     */
    public List<GoldenSpec> loadAll() throws IOException {
        Path dir = Paths.get(specsDir);
        if (!Files.exists(dir)) {
            log.warn("Specs directory does not exist: {}", specsDir);
            return List.of();
        }

        return Files.list(dir)
            .filter(p -> p.toString().endsWith(".yaml") 
                      || p.toString().endsWith(".yml") 
                      || p.toString().endsWith(".json"))
            .map(this::loadSpec)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    /**
     * Loads a single golden spec from file.
     */
    public Optional<GoldenSpec> loadSpec(Path path) {
        try {
            String content = Files.readString(path);
            if (path.toString().endsWith(".json")) {
                return Optional.of(jsonMapper.readValue(content, GoldenSpec.class));
            } else {
                return Optional.of(yamlMapper.readValue(content, GoldenSpec.class));
            }
        } catch (Exception e) {
            log.error("Failed to load spec from {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Loads a specific spec by ID.
     */
    public Optional<GoldenSpec> loadSpecById(String specId) throws IOException {
        Path path = Paths.get(specsDir, specId + ".yaml");
        if (!Files.exists(path)) {
            path = Paths.get(specsDir, specId + ".yml");
        }
        if (!Files.exists(path)) {
            path = Paths.get(specsDir, specId + ".json");
        }
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        return loadSpec(path);
    }

    /**
     * Saves a golden spec to file (YAML).
     */
    public void saveSpec(GoldenSpec spec) throws IOException {
        Path dir = Paths.get(specsDir);
        Files.createDirectories(dir);
        Path file = dir.resolve(spec.id() + ".yaml");
        yamlMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), spec);
        log.info("Saved spec {} to {}", spec.id(), file);
    }
}