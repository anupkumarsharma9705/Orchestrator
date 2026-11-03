package com.example.orchestrator.model.router;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallParserTest {

    @Test
    void parsePlainObject() {
        String content = "{\"tool\": \"readFile\", \"arguments\": {\"path\": \"src/main/java/Foo.java\"}}";
        List<ToolCallParser.ToolCall> calls = ToolCallParser.parse(content);
        assertEquals(1, calls.size());
        assertEquals("readFile", calls.get(0).toolName());
        assertEquals("{\"path\":\"src/main/java/Foo.java\"}", calls.get(0).arguments().toString());
    }

    @Test
    void parseFencedJson() {
        String content = "```json\n{\"tool\": \"writeFile\", \"arguments\": {\"path\": \"test.txt\", \"content\": \"hello\"}}\n```";
        List<ToolCallParser.ToolCall> calls = ToolCallParser.parse(content);
        assertEquals(1, calls.size());
        assertEquals("writeFile", calls.get(0).toolName());
    }

    @Test
    void parseFencedWithoutTag() {
        String content = "```\n{\"tool\": \"searchFiles\", \"arguments\": {\"pattern\": \"*.java\"}}\n```";
        List<ToolCallParser.ToolCall> calls = ToolCallParser.parse(content);
        assertEquals(1, calls.size());
        assertEquals("searchFiles", calls.get(0).toolName());
    }

    @Test
    void parseArray() {
        String content = "[{\"tool\": \"readFile\", \"arguments\": {\"path\": \"a.java\"}}, {\"tool\": \"writeFile\", \"arguments\": {\"path\": \"b.java\"}}]";
        List<ToolCallParser.ToolCall> calls = ToolCallParser.parse(content);
        assertEquals(2, calls.size());
        assertEquals("readFile", calls.get(0).toolName());
        assertEquals("writeFile", calls.get(1).toolName());
    }

    @Test
    void parseToolCallsWrapper() {
        String content = "{\"tool_calls\": [{\"tool\": \"readFile\", \"arguments\": {\"path\": \"a.java\"}}, {\"tool\": \"writeFile\", \"arguments\": {\"path\": \"b.java\"}}]}";
        List<ToolCallParser.ToolCall> calls = ToolCallParser.parse(content);
        assertEquals(2, calls.size());
        assertEquals("readFile", calls.get(0).toolName());
        assertEquals("writeFile", calls.get(1).toolName());
    }

    @Test
    void parseArgumentsAsString() {
        String content = "{\"tool\": \"runMavenTest\", \"arguments\": \"{\\\"goals\\\": [\\\"test\\\"]}\"}";
        List<ToolCallParser.ToolCall> calls = ToolCallParser.parse(content);
        assertEquals(1, calls.size());
        assertEquals("runMavenTest", calls.get(0).toolName());
        // arguments should be parsed as object
        assertNotNull(calls.get(0).arguments());
    }

    @Test
    void parseArgumentsAsObject() {
        String content = "{\"tool\": \"runMavenTest\", \"arguments\": {\"goals\": [\"test\"]}}";
        List<ToolCallParser.ToolCall> calls = ToolCallParser.parse(content);
        assertEquals(1, calls.size());
        assertEquals("runMavenTest", calls.get(0).toolName());
    }

    @Test
    void parseWithAlternativeKeys() {
        String content = "{\"name\": \"readFile\", \"args\": {\"path\": \"test.java\"}}";
        List<ToolCallParser.ToolCall> calls = ToolCallParser.parse(content);
        assertEquals(1, calls.size());
        assertEquals("readFile", calls.get(0).toolName());
    }

    @Test
    void parseWithFunctionWrapper() {
        String content = "{\"function\": {\"name\": \"readFile\", \"arguments\": {\"path\": \"test.java\"}}}";
        List<ToolCallParser.ToolCall> calls = ToolCallParser.parse(content);
        assertEquals(1, calls.size());
        assertEquals("readFile", calls.get(0).toolName());
    }

    @Test
    void parseWithProseBeforeAndAfter() {
        String content = "Here is the tool call:\n```json\n{\"tool\": \"readFile\", \"arguments\": {\"path\": \"test.java\"}}\n```\nThat's it.";
        List<ToolCallParser.ToolCall> calls = ToolCallParser.parse(content);
        assertEquals(1, calls.size());
        assertEquals("readFile", calls.get(0).toolName());
    }

    @Test
    void parseGarbageInput() {
        String content = "This is just plain text with no JSON";
        List<ToolCallParser.ToolCall> calls = ToolCallParser.parse(content);
        assertTrue(calls.isEmpty());
    }

    @Test
    void parseNullOrEmpty() {
        assertTrue(ToolCallParser.parse(null).isEmpty());
        assertTrue(ToolCallParser.parse("").isEmpty());
        assertTrue(ToolCallParser.parse("   ").isEmpty());
    }

    @Test
    void parseMalformedJson() {
        String content = "{\"tool\": \"readFile\", \"arguments\": {not valid json}}";
        List<ToolCallParser.ToolCall> calls = ToolCallParser.parse(content);
        assertTrue(calls.isEmpty());
    }
}