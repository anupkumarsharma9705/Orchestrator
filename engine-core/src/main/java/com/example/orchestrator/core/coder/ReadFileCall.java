package com.example.orchestrator.core.coder;

/**
 * Read a file.
 */
public class ReadFileCall extends ToolCall {
    private String path;

    public ReadFileCall() {}
    public ReadFileCall(String path) { this.path = path; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    @Override
    public String getTool() { return "readFile"; }
}