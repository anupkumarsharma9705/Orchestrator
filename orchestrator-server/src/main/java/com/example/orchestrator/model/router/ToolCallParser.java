package com.example.orchestrator.model.router;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.StreamReadConstraints;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ToolCallParser {

    // H-04, H-07: Secure ObjectMapper with size limits to prevent OOM
    private static final ObjectMapper MAPPER = createSecureMapper();
    private static final Pattern FENCE_PATTERN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
    private static final Pattern SIMPLE_TOOL_PATTERN = Pattern.compile("^\\s*\\{\\s*\"?(?:tool|name)\"?\\s*:\"?([a-zA-Z][a-zA-Z0-9]*)\"?");

    private static ObjectMapper createSecureMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);
        mapper.configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, true);
        mapper.getFactory().setStreamReadConstraints(
            StreamReadConstraints.builder()
                .maxDocumentLength(1_000_000) // 1MB max document
                .maxNestingDepth(100)
                .maxNumberLength(100)
                .maxStringLength(100_000) // 100KB max string
                .build()
        );
        return mapper;
    }

    private ToolCallParser() {}

    public static class ToolCall {
        private final String toolName;
        private final Object arguments;

        public ToolCall(String toolName, Object arguments) {
            this.toolName = toolName;
            this.arguments = arguments;
        }

        public String toolName() {
            return toolName;
        }

        public Object arguments() {
            return arguments;
        }
    }

    public static List<ToolCall> parse(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        String jsonText = extractJson(content);
        if (jsonText == null || jsonText.isBlank()) {
            return List.of();
        }

        try {
            JsonNode node = MAPPER.readTree(jsonText);
            return parseNode(node);
        } catch (Exception e) {
            Matcher m = SIMPLE_TOOL_PATTERN.matcher(content.trim());
            if (m.find()) {
                return List.of(new ToolCall(m.group(1), "{}"));
            }
            return List.of();
        }
    }

    private static String extractJson(String content) {
        Matcher fenceMatcher = FENCE_PATTERN.matcher(content);
        if (fenceMatcher.find()) {
            return fenceMatcher.group(1).trim();
        }

        String trimmed = content.trim();
        int firstBrace = trimmed.indexOf('{');
        int firstBracket = trimmed.indexOf('[');

        int start = -1;
        if (firstBrace >= 0 && firstBracket >= 0) {
            start = Math.min(firstBrace, firstBracket);
        } else if (firstBrace >= 0) {
            start = firstBrace;
        } else if (firstBracket >= 0) {
            start = firstBracket;
        }

        if (start >= 0) {
            char open = trimmed.charAt(start);
            char close = (open == '{') ? '}' : ']';
            int depth = 0;
            boolean inString = false;
            boolean escape = false;

            for (int i = start; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (escape) {
                    escape = false;
                    continue;
                }
                if (c == '\\') {
                    escape = true;
                    continue;
                }
                if (c == '"') {
                    inString = !inString;
                    continue;
                }
                if (!inString) {
                    if (c == open) depth++;
                    else if (c == close) depth--;
                    if (depth == 0) {
                        return trimmed.substring(start, i + 1);
                    }
                }
            }
        }

        if (content.trim().startsWith("{") || content.trim().startsWith("[")) {
            return content.trim();
        }

        return null;
    }

    private static List<ToolCall> parseNode(JsonNode node) {
        List<ToolCall> calls = new ArrayList<>();

        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            if (obj.has("tool_calls")) {
                JsonNode toolCalls = obj.get("tool_calls");
                if (toolCalls.isArray()) {
                    for (JsonNode tc : toolCalls) {
                        String name = extractToolName(tc);
                        Object args = extractArguments(tc);
                        if (name != null) {
                            calls.add(new ToolCall(name, args));
                        }
                    }
                    return calls;
                }
            }
            String name = extractToolName(node);
            Object args = extractArguments(node);
            if (name != null) {
                calls.add(new ToolCall(name, args));
            }
            return calls;
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                String name = extractToolName(item);
                Object args = extractArguments(item);
                if (name != null) {
                    calls.add(new ToolCall(name, args));
                }
            }
            return calls;
        }

        return calls;
    }

    private static String extractToolName(JsonNode node) {
        if (node.has("tool")) {
            return node.get("tool").asText();
        }
        if (node.has("name")) {
            return node.get("name").asText();
        }
        if (node.has("function") && node.get("function").has("name")) {
            return node.get("function").get("name").asText();
        }
        return null;
    }

    private static Object extractArguments(JsonNode node) {
        JsonNode argsNode = null;
        if (node.has("arguments")) {
            argsNode = node.get("arguments");
        } else if (node.has("args")) {
            argsNode = node.get("args");
        } else if (node.has("parameters")) {
            argsNode = node.get("parameters");
        } else if (node.has("function") && node.get("function").has("arguments")) {
            argsNode = node.get("function").get("arguments");
        }

        if (argsNode == null || argsNode.isNull()) {
            return "{}";
        }

        if (argsNode.isTextual()) {
            try {
                return MAPPER.readTree(argsNode.asText());
            } catch (Exception e) {
                return argsNode.asText();
            }
        } else if (argsNode.isObject() || argsNode.isArray()) {
            return argsNode;
        }
        return argsNode.asText();
    }
}