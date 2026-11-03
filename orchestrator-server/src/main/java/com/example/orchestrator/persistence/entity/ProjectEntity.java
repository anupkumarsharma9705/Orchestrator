package com.example.orchestrator.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "projects")
public class ProjectEntity {

    @Id
    @Column(name = "id", nullable = false, length = 128)
    private String id;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "root_path", nullable = false, length = 1024)
    private String rootPath;

    @Column(name = "memory_path", nullable = false, length = 1024)
    private String memoryPath;

    @Column(name = "build_tool", length = 64)
    private String buildTool;

    @Column(name = "test_command", length = 256)
    private String testCommand;

    @Column(name = "environment_json", columnDefinition = "TEXT")
    private String environmentJson;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "last_accessed_at", nullable = false)
    private Instant lastAccessedAt;

    public ProjectEntity() {
    }

    public ProjectEntity(String id, String name, String rootPath, String memoryPath,
                         String buildTool, String testCommand, String environmentJson,
                         Instant registeredAt, Instant lastAccessedAt) {
        this.id = id;
        this.name = name;
        this.rootPath = rootPath;
        this.memoryPath = memoryPath;
        this.buildTool = buildTool;
        this.testCommand = testCommand;
        this.environmentJson = environmentJson;
        this.registeredAt = registeredAt;
        this.lastAccessedAt = lastAccessedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public String getMemoryPath() {
        return memoryPath;
    }

    public void setMemoryPath(String memoryPath) {
        this.memoryPath = memoryPath;
    }

    public String getBuildTool() {
        return buildTool;
    }

    public void setBuildTool(String buildTool) {
        this.buildTool = buildTool;
    }

    public String getTestCommand() {
        return testCommand;
    }

    public void setTestCommand(String testCommand) {
        this.testCommand = testCommand;
    }

    public String getEnvironmentJson() {
        return environmentJson;
    }

    public void setEnvironmentJson(String environmentJson) {
        this.environmentJson = environmentJson;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }
}