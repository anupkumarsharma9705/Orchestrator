package com.example.orchestrator.tool;

import com.example.orchestrator.project.Project;

public interface Tool {
    ToolSpec getSpec();
    ToolResult execute(Project project, String input);
}