package com.example.orchestrator.cli.output;

import com.example.orchestrator.cli.util.Ansi;
import com.example.orchestrator.cli.util.TablePrinter;
import com.example.orchestrator.cli.util.TerminalSupport;

import java.util.List;
import java.util.Map;

/**
 * Formats output in various formats: table, json, markdown, plain.
 */
public class OutputFormatter {

    private final String format;

    public OutputFormatter(String format) {
        this.format = format != null ? format.toLowerCase() : "table";
    }

    public <T> String formatTable(List<String> headers, List<List<String>> rows) {
        return switch (format) {
            case "json" -> toJson(headers, rows);
            case "markdown" -> toMarkdown(headers, rows);
            case "plain" -> toPlain(headers, rows);
            default -> toTable(headers, rows);
        };
    }

    public String formatMap(Map<String, String> map) {
        return switch (format) {
            case "json" -> mapToJson(map);
            case "markdown" -> mapToMarkdown(map);
            case "plain" -> mapToPlain(map);
            default -> TablePrinter.printMap(map);
        };
    }

    public String format(Object obj) {
        if (obj == null) return "";
        if (obj instanceof Map) return formatMap((Map<String, String>) obj);
        if (obj instanceof List) return formatList((List<?>) obj);
        return obj.toString();
    }

    private String toTable(List<String> headers, List<List<String>> rows) {
        if (headers == null || headers.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        int[] widths = new int[headers.size()];

        for (int i = 0; i < headers.size(); i++) widths[i] = headers.get(i).length();
        for (List<String> row : rows) {
            for (int i = 0; i < Math.min(row.size(), widths.length); i++) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }

        sb.append(" ");
        for (int i = 0; i < widths.length; i++) {
            sb.append(" ").append(padRight(headers.get(i), widths[i])).append(" ").append(TerminalSupport.BOX_VERTICAL);
        }
        sb.append("\n");

        for (int w : widths) sb.append(" ").append(TerminalSupport.BOX_HORIZONTAL.repeat(w))
            .append(" ").append(TerminalSupport.BOX_CROSS);
        sb.append("\n");

        for (List<String> row : rows) {
            sb.append(" ");
            for (int i = 0; i < widths.length; i++) {
                sb.append(" ").append(padRight(i < row.size() ? row.get(i) : "", widths[i]))
                    .append(" ").append(TerminalSupport.BOX_VERTICAL);
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String toMarkdown(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(String.join(" | ", headers)).append(" |\n");
        sb.append("| ").append(String.join(" | ", headers.stream().map(h -> "---").collect(java.util.stream.Collectors.toList()))).append(" |\n");
        for (List<String> row : rows) {
            sb.append("| ").append(String.join(" | ", row)).append(" |\n");
        }
        return sb.toString();
    }

    private String toPlain(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i < headers.size()) sb.append(headers.get(i)).append(": ");
                sb.append(row.get(i));
                if (i < row.size() - 1) sb.append(", ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String toJson(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < rows.size(); i++) {
            sb.append("  {");
            for (int j = 0; j < headers.size(); j++) {
                sb.append("\"").append(headers.get(j)).append("\": \"")
                  .append(escapeJson(j < rows.get(i).size() ? rows.get(i).get(j) : "")).append("\"");
                if (j < headers.size() - 1) sb.append(", ");
            }
            sb.append("}");
            if (i < rows.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private String mapToJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, String> e : map.entrySet()) {
            sb.append("  \"").append(e.getKey()).append("\": \"")
              .append(escapeJson(e.getValue())).append("\"");
            if (i < map.size() - 1) sb.append(",");
            sb.append("\n");
            i++;
        }
        sb.append("}");
        return sb.toString();
    }

    private String mapToMarkdown(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("| Key | Value |\n|-----|-------|\n");
        for (Map.Entry<String, String> e : map.entrySet()) {
            sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
        }
        return sb.toString();
    }

    private String mapToPlain(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        }
        return sb.toString();
    }

    private String formatList(List<?> list) {
        if (list.isEmpty()) return "[]";
        if (format.equals("json")) {
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append("  \"").append(escapeJson(list.get(i).toString())).append("\"");
                if (i < list.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("]");
            return sb.toString();
        }
        return list.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", "));
    }

    private String padRight(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}