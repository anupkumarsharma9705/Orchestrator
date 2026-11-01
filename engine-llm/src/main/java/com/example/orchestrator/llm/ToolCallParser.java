package com.example.orchestrator.llm;

import com.example.orchestrator.model.ChatRequest;
import com.example.orchestrator.model.ChatResponse;
import com.example.orchestrator.model.Function;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses tool calls from LLM responses.
 * Supports both native function calling and text-based tool calls.
 */
@Component
public class ToolCallParser {

    private static final Logger log = LoggerFactory.getLogger(ToolCallParser.class);

    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "\\{\\s*\"tool\\s*\":\\s*\"([^\"]+)\"\\s*,\\s*\"arguments\\s*\":\\s*(\\{.*?\\})\\s*\\}",
            Pattern.DOTALL
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parses tool calls from the assistant's message content.
     * 
     * @param response The chat response containing the message
     * @return List of parsed tool calls (empty if none found)
     */
    public List<ChatRequest.ToolCall> parseToolCalls(ChatResponse response) {
        if (response.choices() == null || response.choices().isEmpty()) {
            return List.of();
        }

        var choice = response.choices().get(0);
        var message = choice.message();

        // First check for native tool calls
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            return message.toolCalls();
        }

        // Fall back to parsing from message content
        String content = message.content();
        if (content == null) {
            return List.of();
        }

        var toolCalls = new java.util.ArrayList<ChatRequest.ToolCall>();
        Matcher matcher = TOOL_CALL_PATTERN.matcher(content);

        while (matcher.find()) {
            String toolName = matcher.group(1);
            String argumentsJson = matcher.group(2);

            try {
                JsonNode arguments = objectMapper.readTree(argumentsJson);
                Map<String, Object> parameters = objectMapper.convertValue(arguments, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                var toolCall = new ChatRequest.ToolCall(
                        "call_" + System.nanoTime(),
                        "function",
                        new Function(toolName, "", parameters)
                );
                toolCalls.add(toolCall);
            } catch (Exception e) {
                log.warn("Failed to parse tool call arguments: {}", argumentsJson, e);
            }
        }

        return toolCalls;
    }

    /**
     * Extracts and parses a JSON object from text.
     * Useful for parsing structured responses from LLMs.
     */
    public <T> T extractJson(String text, Class<T> clazz) {
        try {
            // Try to find JSON object in text
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String json = text.substring(start, end + 1);
                return objectMapper.readValue(json, clazz);
            }
        } catch (Exception e) {
            log.warn("Failed to extract JSON from text", e);
        }
        return null;
    }
}