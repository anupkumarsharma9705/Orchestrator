package com.example.orchestrator.cli.util;

/**
 * ANSI escape codes for terminal colors and styles.
 * Automatically disables on non-TTY or when --no-color is set.
 */
public class Ansi {

    private static boolean enabled = true;
    private static Boolean ttyChecked = null;

    // Reset
    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";
    public static final String UNDERLINE = "\u001B[4m";
    public static final String BLINK = "\u001B[5m";
    public static final String REVERSE = "\u001B[7m";
    public static final String HIDDEN = "\u001B[8m";

    // Foreground colors
    public static final String BLACK = "\u001B[30m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";
    public static final String BRIGHT_BLACK = "\u001B[90m";
    public static final String BRIGHT_RED = "\u001B[91m";
    public static final String BRIGHT_GREEN = "\u001B[92m";
    public static final String BRIGHT_YELLOW = "\u001B[93m";
    public static final String BRIGHT_BLUE = "\u001B[94m";
    public static final String BRIGHT_MAGENTA = "\u001B[95m";
    public static final String BRIGHT_CYAN = "\u001B[96m";
    public static final String BRIGHT_WHITE = "\u001B[97m";

    // Background colors
    public static final String BG_BLACK = "\u001B[40m";
    public static final String BG_RED = "\u001B[41m";
    public static final String BG_GREEN = "\u001B[42m";
    public static final String BG_YELLOW = "\u001B[43m";
    public static final String BG_BLUE = "\u001B[44m";
    public static final String BG_MAGENTA = "\u001B[45m";
    public static final String BG_CYAN = "\u001B[46m";
    public static final String BG_WHITE = "\u001B[47m";

    // Cursor control
    public static final String CURSOR_UP = "\u001B[A";
    public static final String CURSOR_DOWN = "\u001B[B";
    public static final String CURSOR_RIGHT = "\u001B[C";
    public static final String CURSOR_LEFT = "\u001B[D";
    public static final String CURSOR_SAVE = "\u001B[s";
    public static final String CURSOR_RESTORE = "\u001B[u";
    public static final String CLEAR_LINE = "\u001B[2K";
    public static final String CLEAR_SCREEN = "\u001B[2J";
    public static final String CLEAR_TO_END = "\u001B[0J";
    public static final String CLEAR_TO_START = "\u001B[1J";
    public static final String HOME = "\u001B[H";

    // Common combinations
    public static final String SUCCESS = GREEN + TerminalSupport.CHECK + RESET;
    public static final String ERROR = RED + TerminalSupport.CROSS + RESET;
    public static final String WARNING = YELLOW + TerminalSupport.WARNING + RESET;
    public static final String INFO = BLUE + TerminalSupport.INFO + RESET;
    public static final String ARROW = CYAN + TerminalSupport.ARROW + RESET;
    public static final String BULLET = DIM + TerminalSupport.BULLET + RESET;

    /**
     * Enable or disable ANSI output globally.
     */
    public static void setEnabled(boolean enabled) {
        Ansi.enabled = enabled;
    }

    /**
     * Check if ANSI output is enabled and we're on a TTY.
     */
    public static boolean isEnabled() {
        if (!enabled) return false;
        if (ttyChecked != null) return ttyChecked;

        // Check if we have a console
        ttyChecked = System.console() != null;
        return ttyChecked;
    }

    /**
     * Force disable ANSI (for --no-color flag).
     */
    public static void disable() {
        enabled = false;
        ttyChecked = false;
    }

    /**
     * Wrap text with ANSI code if enabled, otherwise return plain text.
     */
    public static String wrap(String text, String... codes) {
        if (!isEnabled()) return text;
        StringBuilder sb = new StringBuilder();
        for (String code : codes) sb.append(code);
        sb.append(text).append(RESET);
        return sb.toString();
    }

    // Convenience methods
    public static String red(String text) { return wrap(text, RED); }
    public static String green(String text) { return wrap(text, GREEN); }
    public static String yellow(String text) { return wrap(text, YELLOW); }
    public static String blue(String text) { return wrap(text, BLUE); }
    public static String cyan(String text) { return wrap(text, CYAN); }
    public static String magenta(String text) { return wrap(text, MAGENTA); }
    public static String dim(String text) { return wrap(text, DIM); }
    public static String bold(String text) { return wrap(text, BOLD); }
    public static String underline(String text) { return wrap(text, UNDERLINE); }

    // Color by semantic meaning
    public static String success(String text) { return wrap(text, GREEN, BOLD); }
    public static String error(String text) { return wrap(text, RED, BOLD); }
    public static String warning(String text) { return wrap(text, YELLOW, BOLD); }
    public static String info(String text) { return wrap(text, BLUE, BOLD); }
    public static String muted(String text) { return wrap(text, DIM); }
    public static String highlight(String text) { return wrap(text, CYAN, BOLD); }
}