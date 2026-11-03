package com.example.orchestrator.memory;

import com.example.orchestrator.project.Project;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProjectMemoryService {

    static final List<String> MEMORY_FILES = Arrays.asList(
        "CURRENT_STATE.md",
        "TODO.md",
        "ARCHITECTURE.md",
        "DECISIONS.md",
        "KNOWN_ISSUES.md",
        "DEVELOPMENT_LOG.md",
        "REQUIREMENTS.md",
        "API_CONTRACTS.md",
        "DATABASE.md",
        "CONVENTIONS.md",
        "SECURITY.md",
        "TESTING.md",
        "CHANGELOG.md",
        "PROJECT.md"
    );

    static final List<String> PRIORITY_FILES = Arrays.asList(
        "CURRENT_STATE.md",
        "TODO.md",
        "ARCHITECTURE.md",
        "DECISIONS.md",
        "KNOWN_ISSUES.md",
        "DEVELOPMENT_LOG.md"
    );

    public MemoryBundle loadMemory(Project project) throws IOException {
        Path memoryPath = project.memoryPath();
        Map<String, String> memories = MEMORY_FILES.stream()
            .collect(Collectors.toMap(
                name -> name,
                name -> {
                    try {
                        return readMemoryFile(memoryPath.resolve(name));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                (a, b) -> a
            ));

        Map<String, String> priorityMemories = PRIORITY_FILES.stream()
            .filter(memories::containsKey)
            .collect(Collectors.toMap(
                name -> name,
                memories::get
            ));

        return new MemoryBundle(project.id(), memories, priorityMemories);
    }

    public void initializeMemory(Project project) throws IOException {
        Path memoryPath = project.memoryPath();
        Files.createDirectories(memoryPath);

        for (String fileName : MEMORY_FILES) {
            Path filePath = memoryPath.resolve(fileName);
            if (!Files.exists(filePath)) {
                String template = getTemplate(fileName, project);
                Files.writeString(filePath, template);
            }
        }
    }

    public void writeMemoryFile(Project project, String fileName, String content) throws IOException {
        if (!MEMORY_FILES.contains(fileName)) {
            throw new IllegalArgumentException("Unknown memory file: " + fileName);
        }
        Path filePath = project.memoryPath().resolve(fileName);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }

    public String readMemoryFile(Path path) throws IOException {
        if (Files.exists(path)) {
            return Files.readString(path);
        }
        return "";
    }

    private String getTemplate(String fileName, Project project) {
        String projectName = project.name();
        String projectId = project.id();
        String rootPath = project.rootPath().toString();

        return switch (fileName) {
            case "PROJECT.md" -> """
                # Project: %s (%s)

                **Root Path**: %s
                **Memory Path**: %s/.ai

                ## Overview
                Brief description of the project.

                ## Technology Stack
                - Language:
                - Framework:
                - Build Tool:
                - Database:
                - Key Dependencies:

                ## Team
                - Owner:
                - Contributors:
                """.formatted(projectName, projectId, rootPath, rootPath);

            case "CURRENT_STATE.md" -> """
                # Current State

                ## Active Work
                - 

                ## Recent Changes
                - 

                ## Blockers
                - 

                ## Next Steps
                - 
                """;

            case "TODO.md" -> """
                # TODO

                ## High Priority
                - [ ] 

                ## Medium Priority
                - [ ] 

                ## Low Priority
                - [ ] 

                ## Done
                - [x] 
                """;

            case "ARCHITECTURE.md" -> """
                # Architecture

                ## Overview
                High-level architecture description.

                ## Components
                - 

                ## Data Flow
                - 

                ## External Dependencies
                - 

                ## Deployment Architecture
                - 
                """;

            case "DECISIONS.md" -> """
                # Architecture Decisions

                ## ADR Template
                ### [ADR-XXX] Title
                **Status**: Proposed | Accepted | Superseded
                **Date**: YYYY-MM-DD

                **Context**:
                
                **Decision**:
                
                **Consequences**:
                """;

            case "KNOWN_ISSUES.md" -> """
                # Known Issues

                | ID | Description | Severity | Status | Workaround |
                |----|-------------|----------|--------|------------|
                |    |             |          |        |            |
                """;

            case "DEVELOPMENT_LOG.md" -> """
                # Development Log

                ## YYYY-MM-DD
                - 

                ## YYYY-MM-DD
                - 
                """;

            case "REQUIREMENTS.md" -> """
                # Requirements

                ## Functional Requirements
                - 

                ## Non-Functional Requirements
                - 

                ## Acceptance Criteria
                - 
                """;

            case "API_CONTRACTS.md" -> """
                # API Contracts

                ## Endpoints
                | Method | Path | Description | Request | Response |
                |--------|------|-------------|---------|----------|
                |        |      |             |         |          |

                ## Data Models
                - 
                """;

            case "DATABASE.md" -> """
                # Database Schema

                ## Tables
                | Table | Description |
                |-------|-------------|
                |       |             |

                ## Migrations
                - 
                """;

            case "CONVENTIONS.md" -> """
                # Conventions

                ## Code Style
                - 

                ## Naming Conventions
                - 

                ## Git Workflow
                - 

                ## Testing Standards
                - 
                """;

            case "SECURITY.md" -> """
                # Security

                ## Threat Model
                - 

                ## Authentication
                - 

                ## Authorization
                - 

                ## Data Protection
                - 

                ## Compliance
                - 
                """;

            case "TESTING.md" -> """
                # Testing Strategy

                ## Unit Tests
                - 

                ## Integration Tests
                - 

                ## Contract Tests
                - 

                ## Performance Tests
                - 
                """;

            case "CHANGELOG.md" -> """
                # Changelog

                ## [Unreleased]
                ### Added
                - 

                ### Changed
                - 

                ### Fixed
                - 

                ## [1.0.0] - YYYY-MM-DD
                ### Added
                - 
                """;

            default -> "";
        };
    }
}