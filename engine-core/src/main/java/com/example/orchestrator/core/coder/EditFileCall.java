package com.example.orchestrator.core.coder;

/**
 * Edit an existing file (find/replace).
 */
public class EditFileCall extends ToolCall {
    private String path;
    private String oldString;
    private String newString;

    public EditFileCall() {}
    public EditFileCall(String path, String oldString, String newString) {
        this.path = path;
        this.oldString = oldString;
        this.newString = newString;
    }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getOldString() { return oldString; }
    public void setOldString(String oldString) { this.oldString = oldString; }
    public String getNewString() { return newString; }
    public void setNewString(String newString) { this.newString = newString; }

    @Override
    public String getTool() { return "editFile"; }
}