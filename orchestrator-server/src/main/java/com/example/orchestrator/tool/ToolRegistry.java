package com.example.orchestrator.tool;

import com.example.orchestrator.permission.PermissionManager;
import com.example.orchestrator.project.Project;
import com.example.orchestrator.tool.impl.*;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ToolRegistry {

    private final ListDirectoryTool listDirectoryTool;
    private final ReadFileTool readFileTool;
    private final SearchFilesTool searchFilesTool;
    private final GitStatusTool gitStatusTool;
    private final GitDiffTool gitDiffTool;
    private final RunMavenTestTool runMavenTestTool;
    private final WriteFileTool writeFileTool;
    private final EditFileTool editFileTool;
    private final RunMavenBuildTool runMavenBuildTool;
    private final SemgrepScanTool semgrepScanTool;
    private final DependencyCheckTool dependencyCheckTool;
    private final TrivyScanTool trivyScanTool;
    private final SecretScanTool secretScanTool;
    private final DockerBuildTool dockerBuildTool;
    private final DockerImageScanTool dockerImageScanTool;
    private final PermissionManager permissionManager;

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    public ToolRegistry(
        ListDirectoryTool listDirectoryTool,
        ReadFileTool readFileTool,
        SearchFilesTool searchFilesTool,
        GitStatusTool gitStatusTool,
        GitDiffTool gitDiffTool,
        RunMavenTestTool runMavenTestTool,
        WriteFileTool writeFileTool,
        EditFileTool editFileTool,
        RunMavenBuildTool runMavenBuildTool,
        SemgrepScanTool semgrepScanTool,
        DependencyCheckTool dependencyCheckTool,
        TrivyScanTool trivyScanTool,
        SecretScanTool secretScanTool,
        DockerBuildTool dockerBuildTool,
        DockerImageScanTool dockerImageScanTool,
        PermissionManager permissionManager
    ) {
        this.listDirectoryTool = listDirectoryTool;
        this.readFileTool = readFileTool;
        this.searchFilesTool = searchFilesTool;
        this.gitStatusTool = gitStatusTool;
        this.gitDiffTool = gitDiffTool;
        this.runMavenTestTool = runMavenTestTool;
        this.writeFileTool = writeFileTool;
        this.editFileTool = editFileTool;
        this.runMavenBuildTool = runMavenBuildTool;
        this.semgrepScanTool = semgrepScanTool;
        this.dependencyCheckTool = dependencyCheckTool;
        this.trivyScanTool = trivyScanTool;
        this.secretScanTool = secretScanTool;
        this.dockerBuildTool = dockerBuildTool;
        this.dockerImageScanTool = dockerImageScanTool;
        this.permissionManager = permissionManager;
    }

    @PostConstruct
    public void init() {
        register(listDirectoryTool);
        register(readFileTool);
        register(searchFilesTool);
        register(gitStatusTool);
        register(gitDiffTool);
        register(runMavenTestTool);
        register(writeFileTool);
        register(editFileTool);
        register(runMavenBuildTool);
        register(semgrepScanTool);
        register(dependencyCheckTool);
        register(trivyScanTool);
        register(secretScanTool);
        register(dockerBuildTool);
        register(dockerImageScanTool);
    }

    public void register(Tool tool) {
        tools.put(tool.getSpec().name(), tool);
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public List<Tool> getAll() {
        return List.copyOf(tools.values());
    }

    public List<Tool> getByRiskLevel(ToolSpec.RiskLevel riskLevel) {
        return tools.values().stream()
            .filter(t -> t.getSpec().riskLevel() == riskLevel)
            .toList();
    }

    public Map<String, ToolSpec> getAllSpecs() {
        return tools.values().stream()
            .collect(Collectors.toMap(t -> t.getSpec().name(), Tool::getSpec, (a, b) -> a));
    }

    public ToolResult execute(Project project, String toolName, String input, PermissionManager.PermissionContext context) {
        Tool tool = tools.get(toolName);
        if (tool == null) {
            return ToolResult.failure(toolName, "Tool not found: " + toolName, 0);
        }

        ToolSpec.RiskLevel riskLevel = tool.getSpec().riskLevel();
        if (!permissionManager.checkPermission(riskLevel, context)) {
            return ToolResult.failure(toolName, "Permission denied: " + riskLevel + " tools require appropriate authorization", 0);
        }

        return tool.execute(project, input);
    }
}