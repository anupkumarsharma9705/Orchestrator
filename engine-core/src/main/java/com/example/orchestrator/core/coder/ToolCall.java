package com.example.orchestrator.core.coder;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base class for tool calls from the LLM.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "tool")
@JsonSubTypes({
    @JsonSubTypes.Type(value = WriteFileCall.class, name = "writeFile"),
    @JsonSubTypes.Type(value = EditFileCall.class, name = "editFile"),
    @JsonSubTypes.Type(value = ReadFileCall.class, name = "readFile"),
    @JsonSubTypes.Type(value = ListFilesCall.class, name = "listFiles")
})
public abstract class ToolCall {
    public abstract String getTool();
}