package com.example.orchestrator.cli.util;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ASCII table printer for CLI output.
 */
public class TablePrinter {

    public static void printTable(List<String> headers, List<List<String>> rows) {
        if (headers == null || headers.isEmpty()) return;

        // Calculate column widths
        int[] widths = new int[headers.size()];
        for (int i = 0; i < headers.size(); i++) {
            widths[i] = headers.get(i).length();
        }

        for (List<String> row : rows) {
            for (int i = 0; i < Math.min(row.size(), widths.length); i++) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }

        // Print header
        printRow(headers, widths, true);
        printSeparator(widths);

        // Print rows
        for (List<String> row : rows) {
            printRow(row, widths, false);
        }
    }

    public static String printMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return Ansi.dim("(empty)");
        }

        int keyWidth = map.keySet().stream()
            .mapToInt(String::length)
            .max()
            .orElse(10);

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            sb.append(String.format("%-" + (keyWidth + 2) + "s %s%n",
                Ansi.cyan(entry.getKey()) + ":", entry.getValue()));
        }
        return sb.toString();
    }

    public static String printKeyValue(String key, String value) {
        return Ansi.cyan(key + ":") + " " + value;
    }

    public static String printKeyValue(String key, String value, String color) {
        return Ansi.wrap(key + ":", color) + " " + value;
    }

    private static void printRow(List<String> cells, int[] widths, boolean isHeader) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            String cell = i < cells.size() ? cells.get(i) : "";
            sb.append(" ").append(padRight(cell, widths[i])).append(" │");
        }
        String row = sb.toString();
        if (isHeader) {
            System.out.println(Ansi.bold(row));
        } else {
            System.out.println(row);
        }
    }

    private static void printSeparator(int[] widths) {
        StringBuilder sb = new StringBuilder();
        for (int w : widths) {
            sb.append(" ").append("─".repeat(w)).append(" ┼");
        }
        System.out.println(Ansi.dim(sb.toString()));
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }
}