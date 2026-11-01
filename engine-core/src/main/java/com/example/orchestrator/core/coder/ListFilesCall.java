package com.example.orchestrator.core.coder;

/**
 * List files in a directory.
 */
public class ListFilesCall extends ToolCall {
    private String path;

    public ListFilesCall() {}
    public ListFilesCall(String path) { this.path = path; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    @Override
    public String getTool() { return "listFiles"; }
}