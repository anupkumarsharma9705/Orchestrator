package com.example.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds orchestrator.pipeline.* — everything that was previously hardcoded
 * as Java constants in OrchestratorApplication (target project path,
 * Semgrep executable path, the file(s) being edited, and the task itself).
 *
 * Pointing this orchestrator at a different project or a different task is
 * now an application.yml edit, not a recompile.
 */
@ConfigurationProperties(prefix = "orchestrator.pipeline")
public class PipelineProperties {

    /** Absolute path to the root of the project being worked on. */
    private String targetRoot;

    /** Path to the file being edited, relative to targetRoot. */
    private String sourceFile;

    /** Path to the generated test file, relative to targetRoot. */
    private String testFile;

    /** Absolute path to the Semgrep executable. */
    private String semgrepPath;

    /** Hard cap on review/fix rounds before handing off to a human. */
    private int maxRounds = 5;

    /** The task description sent to Coder each round. */
    private String taskDescription;

    /** Java package of the class being edited (e.g. com.fintech.fintech_gateway.controller). */
    private String packageName;

    /** Simple class name of the class being edited (e.g. PingController), no ".java". */
    private String className;

    /**
     * Comma-separated fully-qualified class names of unrelated Filter beans
     * that must be excluded from any @WebMvcTest slice for this project,
     * because they require dependencies (real services, JPA, etc.) that
     * aren't available in a web-layer-only test context. Fixed, known facts
     * about this project — not something the Tester model should guess at.
     */
    private String webMvcTestExcludeFilterClasses;

    public String getWebMvcTestExcludeFilterClasses() {
        return webMvcTestExcludeFilterClasses;
    }

    public void setWebMvcTestExcludeFilterClasses(String webMvcTestExcludeFilterClasses) {
        this.webMvcTestExcludeFilterClasses = webMvcTestExcludeFilterClasses;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getTargetRoot() {
        return targetRoot;
    }

    public void setTargetRoot(String targetRoot) {
        this.targetRoot = targetRoot;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public String getTestFile() {
        return testFile;
    }

    public void setTestFile(String testFile) {
        this.testFile = testFile;
    }

    public String getSemgrepPath() {
        return semgrepPath;
    }

    public void setSemgrepPath(String semgrepPath) {
        this.semgrepPath = semgrepPath;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    public String getTaskDescription() {
        return taskDescription;
    }

    public void setTaskDescription(String taskDescription) {
        this.taskDescription = taskDescription;
    }
}