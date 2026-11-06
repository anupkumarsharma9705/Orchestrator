package com.example.orchestrator.tool.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MavenGoalValidatorTest {

    @Test
    void allowsLifecyclePhasesAndReportingGoals() {
        assertNull(MavenGoalValidator.validate(List.of("clean", "test")));
        assertNull(MavenGoalValidator.validate(List.of("verify")));
        assertNull(MavenGoalValidator.validate(List.of("dependency:tree")));
        assertNull(MavenGoalValidator.validate(List.of("jacoco:report")));
    }

    @Test
    void rejectsPluginGoalsThatCanExecuteCode() {
        assertNotNull(MavenGoalValidator.validate(List.of("exec:exec")));
        assertNotNull(MavenGoalValidator.validate(List.of("org.codehaus.mojo:exec-maven-plugin:exec")));
    }

    @Test
    void rejectsMavenOptionsAndUnknownGoals() {
        assertNotNull(MavenGoalValidator.validate(List.of("-Dmaven.repo.local=/tmp/repo")));
        assertNotNull(MavenGoalValidator.validate(List.of("deploy")));
        assertNotNull(MavenGoalValidator.validate(List.of("compile -DskipTests")));
        assertNotNull(MavenGoalValidator.validate(List.of("")));
    }
}
