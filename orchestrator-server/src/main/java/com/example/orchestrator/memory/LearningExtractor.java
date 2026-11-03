package com.example.orchestrator.memory;

import com.example.orchestrator.execution.ExecutionState;
import com.example.orchestrator.execution.ExecutionStatus;
import com.example.orchestrator.persistence.entity.ProjectMemoryEntity;
import com.example.orchestrator.persistence.repo.ProjectMemoryRepository;
import com.example.orchestrator.project.Project;
import com.example.orchestrator.requirement.RequirementSpec;
import com.example.orchestrator.task.Task;
import com.example.orchestrator.task.TaskPlan;
import com.example.orchestrator.tool.ToolResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LearningExtractor {

    private final ProjectMemoryRepository memoryRepository;

    // Patterns to detect
    private static final Pattern FILE_PATTERN = Pattern.compile("(?:create|modify|edit|update|write|read)\\s+(\\S+\\.(?:java|xml|yml|yaml|properties|json|md|gradle))");
    private static final Pattern CONTROLLER_PATTERN = Pattern.compile("@RestController|@Controller|@RequestMapping|@GetMapping|@PostMapping");
    private static final Pattern SERVICE_PATTERN = Pattern.compile("@Service|@Transactional");
    private static final Pattern REPOSITORY_PATTERN = Pattern.compile("@Repository|extends\\s+JpaRepository");
    private static final Pattern DTO_PATTERN = Pattern.compile("class\\s+(\\w+Dto|\\w+Request|\\w+Response)\\s*\\{");
    private static final Pattern ENTITY_PATTERN = Pattern.compile("@Entity|@Table|@Column|@Id|@GeneratedValue");
    private static final Pattern VALIDATION_PATTERN = Pattern.compile("@Valid|@NotNull|@NotBlank|@Size|@Pattern");
    private static final Pattern MAPSTRUCT_PATTERN = Pattern.compile("@Mapper|@Mapping");
    private static final Pattern TEST_PATTERN = Pattern.compile("@Test|@DataJpaTest|@SpringBootTest|@MockMvcTest");
    private static final Pattern TRANSACTIONAL_PATTERN = Pattern.compile("@Transactional");

    public LearningExtractor(ProjectMemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    public void extractAndStoreLearnings(ExecutionState state, Project project, TaskPlan plan, RequirementSpec requirements) {
        // Extract from successful completion
        if (state.status() == ExecutionStatus.COMPLETED) {
            extractSuccessfulPatterns(state, project, plan, requirements);
        }
        // Extract from failures
        else if (state.status() == ExecutionStatus.FAILED) {
            extractFailurePatterns(state, project);
        }
    }

    private void extractSuccessfulPatterns(ExecutionState state, Project project, TaskPlan plan, RequirementSpec requirements) {
        List<String> toolOutputs = new ArrayList<>();
        
        if (state.toolResults() != null) {
            for (var result : state.toolResults()) {
                if (result.success() && result.output() != null) {
                    toolOutputs.add(result.output());
                }
            }
        }

        // Extract conventions from file operations
        String combinedOutput = String.join("\n", toolOutputs);
        
        // Detect patterns
        detectAndStoreConvention(project, combinedOutput, "Controller pattern", CONTROLLER_PATTERN, "Controller files should use @RestController with @RequestMapping");
        detectAndStoreConvention(project, combinedOutput, "Service pattern", SERVICE_PATTERN, "Service classes should use @Service and @Transactional");
        detectAndStoreConvention(project, combinedOutput, "Repository pattern", REPOSITORY_PATTERN, "Repository interfaces should extend JpaRepository");
        detectAndStoreConvention(project, combinedOutput, "DTO pattern", DTO_PATTERN, "DTO classes should follow naming convention *Dto/*Request/*Response");
        detectAndStoreConvention(project, combinedOutput, "Entity pattern", ENTITY_PATTERN, "JPA entities should use @Entity, @Table, @Id");
        detectAndStoreConvention(project, combinedOutput, "Validation pattern", VALIDATION_PATTERN, "Use Jakarta Bean Validation annotations (@Valid, @NotNull, etc.)");
        detectAndStoreConvention(project, combinedOutput, "MapStruct pattern", MAPSTRUCT_PATTERN, "Use MapStruct for DTO mapping");
        detectAndStoreConvention(project, combinedOutput, "Transactional pattern", TRANSACTIONAL_PATTERN, "Use @Transactional on service write methods");
        detectAndStoreConvention(project, combinedOutput, "Test pattern", TEST_PATTERN, "Use @DataJpaTest for repository tests, @SpringBootTest for integration tests");

        // Extract file structure conventions
        extractFileStructureConventions(project, combinedOutput);

        // Store task-specific learning
        if (!plan.summary().isBlank()) {
            storeLearning(project, ProjectMemoryEntity.MemoryType.SUCCESS,
                "Task completed: " + plan.summary(),
                "Plan: " + plan.summary() + "\nTasks: " + plan.tasks().size(),
                0.8);
        }
    }

    private void extractFailurePatterns(ExecutionState state, Project project) {
        String error = state.error() != null ? state.error() : "Unknown error";
        
        // Classify error type
        String errorType = classifyError(error);
        
        storeLearning(project, ProjectMemoryEntity.MemoryType.FAILURE,
            "Task failed: " + errorType,
            "Error: " + error + "\nTask: " + state.request(),
            0.7);
    }

    private String classifyError(String error) {
        if (error.contains("compile") || error.contains("cannot find symbol")) {
            return "COMPILATION_ERROR";
        } else if (error.contains("test") || error.contains("Test")) {
            return "TEST_FAILURE";
        } else if (error.contains("validation") || error.contains("constraint")) {
            return "VALIDATION_ERROR";
        } else if (error.contains("database") || error.contains("SQL") || error.contains("transaction")) {
            return "DATABASE_ERROR";
        } else if (error.contains("security") || error.contains("permission")) {
            return "SECURITY_ERROR";
        }
        return "UNKNOWN_ERROR";
    }

    private void detectAndStoreConvention(Project project, String content, String name, Pattern pattern, String description) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            storeLearning(project, ProjectMemoryEntity.MemoryType.CONVENTION,
                name + " detected",
                description + "\nContext: " + matcher.group(),
                0.75);
        }
    }

    private void extractFileStructureConventions(Project project, String content) {
        Map<String, Integer> pathCounts = new HashMap<>();
        
        // Find all file paths mentioned
        Matcher matcher = FILE_PATTERN.matcher(content);
        while (matcher.find()) {
            String path = matcher.group(1);
            String layer = extractLayer(path);
            pathCounts.merge(layer, 1, Integer::sum);
        }

        // Store layer conventions
        for (Map.Entry<String, Integer> entry : pathCounts.entrySet()) {
            if (entry.getValue() >= 2) { // Only store if seen multiple times
                storeLearning(project, ProjectMemoryEntity.MemoryType.CONVENTION,
                    "File structure: " + entry.getKey() + " layer",
                    "Files in " + entry.getKey() + " layer follow project convention. Count: " + entry.getValue(),
                    0.7);
            }
        }
    }

    private String extractLayer(String path) {
        if (path.contains("controller")) return "controller";
        if (path.contains("service")) return "service";
        if (path.contains("repository")) return "repository";
        if (path.contains("entity") || path.contains("model")) return "entity";
        if (path.contains("dto") || path.contains("request") || path.contains("response")) return "dto";
        if (path.contains("config")) return "config";
        if (path.contains("exception")) return "exception";
        if (path.contains("test")) return "test";
        return "other";
    }

    private void storeLearning(Project project, ProjectMemoryEntity.MemoryType type, 
                               String pattern, String context, double confidence) {
        // Check if similar pattern already exists
        List<ProjectMemoryEntity> existing = memoryRepository.findByProjectIdAndType(project.id(), type);
        for (ProjectMemoryEntity existingMem : existing) {
            if (existingMem.getPattern().contains(pattern) || pattern.contains(existingMem.getPattern())) {
                // Boost confidence and update
                existingMem.incrementUsage();
                if (existingMem.getConfidence() < confidence) {
                    existingMem.setConfidence(confidence);
                }
                memoryRepository.save(existingMem);
                return;
            }
        }

        // Create new learning
        ProjectMemoryEntity memory = new ProjectMemoryEntity(
            project.id(), type, pattern, context, confidence
        );
        memoryRepository.save(memory);
    }
}