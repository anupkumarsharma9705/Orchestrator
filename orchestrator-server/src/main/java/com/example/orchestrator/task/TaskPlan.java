package com.example.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TaskPlan(
    @JsonProperty("tasks") List<Task> tasks,
    @JsonProperty("summary") String summary,
    @JsonProperty("estimatedRounds") int estimatedRounds
) {}