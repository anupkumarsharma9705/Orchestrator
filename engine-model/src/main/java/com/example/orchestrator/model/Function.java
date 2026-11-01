package com.example.orchestrator.model;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Objects;

/**
 * Function definition for tool calling.
 * 
 * @param name Function name
 * @param description Function description
 * @param parameters Function parameters as a map
 */
public record Function(
    @NotBlank String name,
    @NotBlank String description,
    Map<String, Object> parameters
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Function that)) return false;
        return Objects.equals(name, that.name) &&
               Objects.equals(description, that.description) &&
               Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, parameters);
    }
}