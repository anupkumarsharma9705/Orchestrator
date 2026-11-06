package com.example.orchestrator.tool.impl;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates Maven goals supplied by tool callers.
 *
 * <p>Goals are passed to Maven without a shell, so shell metacharacters are not
 * interpreted. The risk is that Maven goals can select arbitrary plugins that
 * execute code (for example {@code exec:exec}), so only the standard lifecycle
 * phases and a small set of read-only reporting goals are accepted.</p>
 */
final class MavenGoalValidator {

    private static final Pattern SAFE_GOAL = Pattern.compile("^[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*)?$");

    private static final Set<String> ALLOWED_GOALS = Set.of(
        "clean",
        "validate",
        "initialize",
        "generate-sources",
        "process-sources",
        "generate-resources",
        "process-resources",
        "compile",
        "process-classes",
        "generate-test-sources",
        "process-test-sources",
        "generate-test-resources",
        "process-test-resources",
        "test-compile",
        "process-test-classes",
        "test",
        "prepare-package",
        "package",
        "pre-integration-test",
        "integration-test",
        "post-integration-test",
        "verify",
        "install",
        "dependency:tree",
        "dependency:list",
        "dependency:analyze",
        "jacoco:report",
        "checkstyle:check",
        "spotbugs:check"
    );

    private MavenGoalValidator() {
    }

    /**
     * @return {@code null} when every goal is allowed, otherwise a message
     *         describing the rejected goal.
     */
    static String validate(List<String> goals) {
        for (String goal : goals) {
            if (goal == null || !SAFE_GOAL.matcher(goal).matches() || !ALLOWED_GOALS.contains(goal)) {
                return "Maven goal not allowed: " + goal
                    + ". Allowed goals are the standard lifecycle phases and a small set of reporting goals.";
            }
        }
        return null;
    }
}
