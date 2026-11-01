package com.example.orchestrator.core.coder;

/**
 * Write a new file.
 */
public class WriteFileCall extends ToolCall {
    private String path;
    private String content;

    public WriteFileCall() {}
    public WriteFileCall(String path, String content) {
        this.path = path;
        this.content = content;
    }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    @Override
    public String getTool() { return "writeFile"; }
}