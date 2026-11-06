package com.example.orchestrator.cli.util;

import java.nio.charset.Charset;
import java.util.Locale;

/**
 * Detects whether the active terminal can render the Unicode glyphs used by
 * the CLI. Legacy Windows console code pages cannot encode box-drawing
 * characters or status symbols, so callers receive ASCII fallbacks instead of
 * question marks.
 */
public final class TerminalSupport {

    private static final String[] UNICODE_SAMPLES = {
        "\u2713", "\u2717", "\u26a0", "\u2139", "\u25b8", "\u2022",
        "\u2502", "\u2500", "\u253c", "\u2550", "\u27f3", "\u25cb", "\u280b"
    };

    private static final String[] BRAILLE_FRAMES = {
        "\u280b", "\u2819", "\u2839", "\u2838", "\u283c",
        "\u2834", "\u2826", "\u2827", "\u2807", "\u280f"
    };

    private static final String[] ASCII_FRAMES = {
        "|", "/", "-", "\\"
    };

    private static final boolean UNICODE = detectUnicode(
        System.out.charset(),
        System.getenv("COMPANION_ASCII"));

    public static final String CHECK = choose("\u2713", "+");
    public static final String CROSS = choose("\u2717", "x");
    public static final String WARNING = choose("\u26a0", "!");
    public static final String INFO = choose("\u2139", "i");
    public static final String ARROW = choose("\u25b8", ">");
    public static final String BULLET = choose("\u2022", "*");
    public static final String BOX_VERTICAL = choose("\u2502", "|");
    public static final String BOX_HORIZONTAL = choose("\u2500", "-");
    public static final String BOX_CROSS = choose("\u253c", "+");
    public static final String HEAVY_HORIZONTAL = choose("\u2550", "=");
    public static final String RUNNING = choose("\u27f3", "*");
    public static final String PENDING = choose("\u25cb", "o");

    private TerminalSupport() {
    }

    public static boolean isUnicodeSupported() {
        return UNICODE;
    }

    /**
     * Build a section heading using the configured heavy horizontal rule.
     */
    public static String heading(String title) {
        String rule = HEAVY_HORIZONTAL.repeat(3);
        return rule + " " + title + " " + rule;
    }

    /**
     * Return spinner frames appropriate for the active terminal. Braille is
     * used only when Unicode is supported and the caller asked for it.
     */
    public static String[] spinnerFrames(boolean preferBraille) {
        return preferBraille && UNICODE ? BRAILLE_FRAMES.clone() : ASCII_FRAMES.clone();
    }

    static boolean detectUnicode(Charset stdoutCharset, String asciiOverride) {
        if (asciiOverride != null && !asciiOverride.isBlank()) {
            String normalized = asciiOverride.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("1") || normalized.equals("true") || normalized.equals("yes")) {
                return false;
            }
        }
        if (stdoutCharset == null) {
            return false;
        }
        for (String sample : UNICODE_SAMPLES) {
            if (!stdoutCharset.newEncoder().canEncode(sample)) {
                return false;
            }
        }
        return true;
    }

    private static String choose(String unicode, String ascii) {
        return UNICODE ? unicode : ascii;
    }
}
