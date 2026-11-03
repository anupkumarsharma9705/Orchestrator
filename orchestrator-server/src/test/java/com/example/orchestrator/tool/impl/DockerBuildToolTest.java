package com.example.orchestrator.tool.impl;

import com.example.orchestrator.SharedTestConfig;
import com.example.orchestrator.project.Project;
import com.example.orchestrator.tool.ToolRegistry;
import com.example.orchestrator.tool.ToolResult;
import com.example.orchestrator.tool.ToolSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Import(SharedTestConfig.class)
class DockerBuildToolTest {

    @Autowired
    private ToolRegistry toolRegistry;

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateDockerBuildTool() {
        ObjectMapper mapper = new ObjectMapper();
        DockerBuildTool tool = new DockerBuildTool(mapper);

        ToolSpec spec = tool.getSpec();
        assertEquals("dockerBuild", spec.name());
        assertEquals(ToolSpec.RiskLevel.CONTROLLED, spec.riskLevel());
    }
}