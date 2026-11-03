package com.example.orchestrator.api;

import com.example.orchestrator.approval.ApprovalDecision;
import com.example.orchestrator.approval.ApprovalService;
import com.example.orchestrator.artifact.ArtifactStore;
import com.example.orchestrator.execution.ExecutionEngine;
import com.example.orchestrator.execution.ExecutionState;
import com.example.orchestrator.execution.ExecutionStatus;
import com.example.orchestrator.project.Project;
import com.example.orchestrator.project.ProjectManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@Tag(name = "Task Management", description = "Operations for managing orchestration tasks")
public class TaskController {

    private final ProjectManager projectManager;
    private final ExecutionEngine executionEngine;
    private final ApprovalService approvalService;
    private final ArtifactStore artifactStore;

    public TaskController(ProjectManager projectManager, ExecutionEngine executionEngine, ApprovalService approvalService, ArtifactStore artifactStore) {
        this.projectManager = projectManager;
        this.executionEngine = executionEngine;
        this.approvalService = approvalService;
        this.artifactStore = artifactStore;
    }

    @PostMapping("/projects")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Register a new project", description = "Creates a new project with the given configuration")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Project registered successfully",
            content = @Content(schema = @Schema(implementation = ProjectResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ProjectResponse> registerProject(
            @Parameter(description = "Project registration request", required = true)
            @RequestBody ProjectRequest request) {
        Project project = projectManager.register(
            request.id(),
            request.name(),
            java.nio.file.Path.of(request.rootPath()),
            request.buildTool() != null ? request.buildTool() : "maven",
            request.testCommand() != null ? request.testCommand() : "test",
            request.environment() != null ? request.environment() : Map.of()
        );
        
        return ResponseEntity.ok(toResponse(project));
    }

    @GetMapping("/projects")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "List all projects", description = "Returns a list of all registered projects")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of projects",
            content = @Content(schema = @Schema(implementation = ProjectResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<List<ProjectResponse>> listProjects() {
        List<ProjectResponse> projects = projectManager.listAll().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/projects/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable String id) {
        return projectManager.get(id)
            .map(this::toResponse)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/tasks")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Submit a new task", description = "Submits a new orchestration task for execution")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Task submitted successfully",
            content = @Content(schema = @Schema(implementation = TaskResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<TaskResponse> submitTask(
            @Parameter(description = "Task creation request", required = true)
            @RequestBody CreateTaskRequest request) {
        String taskId = executionEngine.submitTask(request.projectId(), request.request(), request.requester());
        ExecutionState state = executionEngine.getState(taskId);
        
        return ResponseEntity.ok(toResponse(state));
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<TaskResponse>> listTasks() {
        List<TaskResponse> tasks = executionEngine.getAllStates().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/tasks/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TaskResponse> getTask(@PathVariable String id) {
        ExecutionState state = executionEngine.getState(id);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toResponse(state));
    }

    @PostMapping("/tasks/{id}/approve")
    @PreAuthorize("hasRole('APPROVER')")
    public ResponseEntity<Void> approveTask(@PathVariable String id, @RequestBody ApprovalRequest request) {
        ApprovalDecision decision;
        if ("APPROVED".equalsIgnoreCase(request.decision())) {
            decision = ApprovalDecision.approved(id, request.approver(), request.comment());
        } else if ("REJECTED".equalsIgnoreCase(request.decision())) {
            decision = ApprovalDecision.rejected(id, request.approver(), request.comment());
        } else {
            return ResponseEntity.badRequest().build();
        }
        
        approvalService.submitApproval(decision);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tasks/{id}/reject")
    @PreAuthorize("hasRole('APPROVER')")
    public ResponseEntity<Void> rejectTask(@PathVariable String id, @RequestBody ApprovalRequest request) {
        ApprovalDecision decision = ApprovalDecision.rejected(id, request.approver(), request.comment());
        approvalService.submitApproval(decision);
        return ResponseEntity.ok().build();
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
            project.id(),
            project.name(),
            project.rootPath().toString(),
            project.memoryPath().toString(),
            project.buildTool(),
            project.testCommand(),
            project.registeredAt(),
            project.lastAccessedAt()
        );
    }

    private TaskResponse toResponse(ExecutionState state) {
        return new TaskResponse(
            state.taskId(),
            state.projectId(),
            state.request(),
            state.requester(),
            state.status().name(),
            state.createdAt(),
            state.plan() != null ? state.plan().summary() : null,
            state.error()
        );
    }

    @GetMapping("/tasks/{id}/artifacts")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ArtifactResponse>> listArtifacts(@PathVariable String id) {
        List<ArtifactStore.ArtifactRecord> artifacts = artifactStore.getArtifactsForTask(id);
        return ResponseEntity.ok(artifacts.stream()
            .map(a -> new ArtifactResponse(
                a.artifactId(),
                a.name(),
                a.fileName(),
                a.contentType(),
                a.sizeBytes(),
                a.path(),
                a.createdAt(),
                a.checksum()
            ))
            .collect(Collectors.toList()));
    }

    @GetMapping("/tasks/{id}/artifacts/{artifactName}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Resource> downloadArtifact(@PathVariable String id, @PathVariable String artifactName) {
        List<ArtifactStore.ArtifactRecord> artifacts = artifactStore.getArtifactsForTask(id);
        ArtifactStore.ArtifactRecord artifact = artifacts.stream()
            .filter(a -> a.fileName().equals(artifactName))
            .findFirst()
            .orElse(null);
        
        if (artifact == null) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            byte[] content = artifactStore.readArtifactBytes(artifact.artifactId());
            
            ByteArrayResource resource = new ByteArrayResource(content);
            
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.name() + "\"")
                .header("X-Artifact-Checksum", artifact.checksum())
                .contentLength(artifact.sizeBytes())
                .body(resource);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private record ArtifactResponse(
        String artifactId,
        String name,
        String fileName,
        String contentType,
        long sizeBytes,
        String path,
        Instant createdAt,
        String checksum
    ) {}
}