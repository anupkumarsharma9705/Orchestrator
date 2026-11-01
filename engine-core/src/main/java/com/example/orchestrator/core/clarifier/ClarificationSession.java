package com.example.orchestrator.core.clarifier;

import com.example.orchestrator.model.Clarification;
import com.example.orchestrator.model.RequirementsDoc;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a clarification session with questions, answers, and progress.
 * 
 * @param projectName Name of the project
 * @param userRequest Original user request
 * @param questions List of clarification questions
 * @param round Current round number (1-based)
 * @param answers Map of questionId -> userAnswer
 * @param requirementsDoc Final requirements document (when complete)
 */
public record ClarificationSession(
    String projectName,
    String userRequest,
    List<Clarification> questions,
    int round,
    Map<String, String> answers,
    RequirementsDoc requirementsDoc
) {
    public ClarificationSession {
        questions = questions != null ? List.copyOf(questions) : List.of();
        answers = answers != null ? Map.copyOf(answers) : Map.of();
    }

    /**
     * Creates a new session with updated answers.
     */
    public ClarificationSession withAnswers(Map<String, String> newAnswers) {
        Map<String, String> merged = new HashMap<>(answers);
        merged.putAll(newAnswers);
        
        List<Clarification> updatedQuestions = questions.stream()
            .map(q -> {
                String userAnswer = merged.get(q.id());
                if (userAnswer != null && !userAnswer.isBlank()) {
                    return new Clarification(
                        q.id(), q.question(), q.defaultAnswer(), userAnswer,
                        q.type(), q.options());
                }
                return q;
            })
            .toList();

        return new ClarificationSession(
            projectName, userRequest, updatedQuestions, round + 1, merged, requirementsDoc);
    }

    /**
     * Creates a session with previous answers preserved (for follow-up rounds).
     */
    public ClarificationSession withPreviousAnswers(Map<String, String> previousAnswers) {
        Map<String, String> merged = new HashMap<>(previousAnswers);
        merged.putAll(answers);
        
        List<Clarification> updatedQuestions = questions.stream()
            .map(q -> {
                String userAnswer = merged.get(q.id());
                if (userAnswer != null && !userAnswer.isBlank()) {
                    return new Clarification(
                        q.id(), q.question(), q.defaultAnswer(), userAnswer,
                        q.type(), q.options());
                }
                return q;
            })
            .toList();

        return new ClarificationSession(
            projectName, userRequest, updatedQuestions, round, merged, requirementsDoc);
    }

    /**
     * Creates a session with the final RequirementsDoc.
     */
    public ClarificationSession withRequirementsDoc(RequirementsDoc requirementsDoc) {
        return new ClarificationSession(
            projectName, userRequest, questions, round, answers, requirementsDoc);
    }

    /**
     * Checks if all questions have been answered.
     */
    @JsonIgnore
    public boolean isComplete() {
        return questions.stream().allMatch(Clarification::isAnswered);
    }

    /**
     * Gets all answers (user answers + defaults for unanswered).
     */
    @JsonIgnore
    public Map<String, String> getAllAnswers() {
        Map<String, String> all = new HashMap<>();
        for (Clarification q : questions) {
            all.put(q.id(), q.getEffectiveAnswer());
        }
        return all;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClarificationSession that)) return false;
        return Objects.equals(projectName, that.projectName) &&
               Objects.equals(userRequest, that.userRequest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectName, userRequest);
    }
}