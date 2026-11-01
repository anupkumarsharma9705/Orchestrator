package com.example.orchestrator.cli.interactive;

import com.example.orchestrator.cli.util.Ansi;
import com.example.orchestrator.cli.util.TablePrinter;

import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Interactive prompter for Clarifier Q&A session.
 * Presents questions with defaults, validates input, supports help.
 */
public class ClarifierPrompter {

    private final Scanner scanner;
    private final boolean nonInteractive;

    public ClarifierPrompter() {
        this(false);
    }

    public ClarifierPrompter(boolean nonInteractive) {
        this.scanner = new Scanner(System.in);
        this.nonInteractive = nonInteractive;
    }

    /**
     * Present a question and get user input with default.
     *
     * @param question       Question text
     * @param defaultValue   Default value (shown in brackets)
     * @param options        Valid options (null for free text)
     * @param helpText       Help text shown when user enters '?'
     * @return User's answer (or default if empty)
     */
    public String prompt(String question, String defaultValue, List<String> options, String helpText) {
        if (nonInteractive) {
            return defaultValue != null ? defaultValue : "";
        }

        while (true) {
            String prompt = buildPrompt(question, defaultValue, options);
            System.out.print(prompt);

            String input = scanner.nextLine().trim();

            if (input.isEmpty() && defaultValue != null) {
                return defaultValue;
            }

            if ("?".equals(input) && helpText != null) {
                System.out.println(Ansi.dim("  Help: " + helpText));
                continue;
            }

            if (options != null && !options.isEmpty()) {
                // Try to match by number or exact text
                String matched = matchOption(input, options);
                if (matched != null) {
                    return matched;
                }
                System.out.println(Ansi.red("  Invalid option. Enter a number (1-" + options.size() + ") or the option text."));
                continue;
            }

            return input;
        }
    }

    /**
     * Prompt for yes/no confirmation.
     */
    public boolean confirm(String question, boolean defaultYes) {
        if (nonInteractive) return defaultYes;

        String defaultStr = defaultYes ? "Y/n" : "y/N";
        while (true) {
            System.out.print(Ansi.cyan(question) + " [" + defaultStr + "] ");
            String input = scanner.nextLine().trim().toLowerCase();

            if (input.isEmpty()) return defaultYes;
            if (input.equals("y") || input.equals("yes")) return true;
            if (input.equals("n") || input.equals("no")) return false;

            System.out.println(Ansi.red("  Please enter 'y' or 'n'"));
        }
    }

    /**
     * Prompt for multi-select (choose multiple options).
     */
    public List<String> multiSelect(String question, List<String> options, List<String> defaults) {
        if (nonInteractive) return defaults;

        System.out.println(Ansi.cyan(question));
        printNumberedOptions(options, defaults);

        System.out.print(Ansi.cyan("Select (comma-separated numbers, 'all', 'none') [default]: "));
        String input = scanner.nextLine().trim();

        if (input.isEmpty()) return defaults;

        if ("all".equalsIgnoreCase(input)) return options;
        if ("none".equalsIgnoreCase(input)) return List.of();

        return parseSelection(input, options);
    }

    /**
     * Show a section header.
     */
    public void section(String title) {
        System.out.println();
        System.out.println(Ansi.bold(Ansi.CYAN + "═══ " + title + " ═══"));
        System.out.println();
    }

    /**
     * Show info message.
     */
    public void info(String message) {
        System.out.println(Ansi.info(" " + message));
    }

    /**
     * Show warning message.
     */
    public void warn(String message) {
        System.out.println(Ansi.warning(" " + message));
    }

    /**
     * Show error message.
     */
    public void error(String message) {
        System.out.println(Ansi.error(" " + message));
    }

    /**
     * Show a summary table.
     */
    public void summary(String title, Map<String, String> items) {
        System.out.println();
        System.out.println(Ansi.bold(title));
        TablePrinter.printMap(items);
        System.out.println();
    }

    // ===== Private helpers =====

    private String buildPrompt(String question, String defaultValue, List<String> options) {
        StringBuilder sb = new StringBuilder();
        sb.append(Ansi.cyan("? ")).append(question);

        if (options != null && !options.isEmpty()) {
            sb.append("\n");
            printNumberedOptions(options, List.of());
        }

        if (defaultValue != null && !defaultValue.isBlank()) {
            sb.append(Ansi.dim(" [" + defaultValue + "]"));
        }

        sb.append(" ");
        sb.append(Ansi.dim("(Enter '?' for help)"));
        sb.append("\n");
        sb.append(Ansi.ARROW).append(" ");

        return sb.toString();
    }

    private void printNumberedOptions(List<String> options, List<String> defaults) {
        AtomicInteger i = new AtomicInteger(1);
        options.forEach(opt -> {
            String marker = defaults.contains(opt) ? Ansi.green(" ✓") : "";
            System.out.println("  " + Ansi.dim(i.getAndIncrement() + ")") + " " + opt + marker);
        });
    }

    private String matchOption(String input, List<String> options) {
        // Try number
        try {
            int idx = Integer.parseInt(input.trim());
            if (idx >= 1 && idx <= options.size()) {
                return options.get(idx - 1);
            }
        } catch (NumberFormatException ignored) {}

        // Try exact match (case-insensitive)
        for (String opt : options) {
            if (opt.equalsIgnoreCase(input)) return opt;
        }

        // Try prefix match
        for (String opt : options) {
            if (opt.toLowerCase().startsWith(input.toLowerCase())) return opt;
        }

        return null;
    }

    private List<String> parseSelection(String input, List<String> options) {
        return java.util.Arrays.stream(input.split(","))
            .map(String::trim)
            .flatMap(part -> {
                String matched = matchOption(part, options);
                return matched != null ? java.util.stream.Stream.of(matched) : java.util.stream.Stream.empty();
            })
            .distinct()
            .collect(Collectors.toList());
    }
}