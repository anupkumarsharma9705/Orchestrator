package com.example.orchestrator.cli.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalSupportTest {

    @Test
    void utf8SupportsUnicodeGlyphs() {
        assertThat(TerminalSupport.detectUnicode(StandardCharsets.UTF_8, null)).isTrue();
    }

    @Test
    void legacyCodePageFallsBackToAscii() {
        assertThat(TerminalSupport.detectUnicode(Charset.forName("windows-1252"), null)).isFalse();
    }

    @Test
    void asciiCharsetFallsBackToAscii() {
        assertThat(TerminalSupport.detectUnicode(StandardCharsets.US_ASCII, null)).isFalse();
    }

    @Test
    void companionAsciiOverridesUtf8() {
        assertThat(TerminalSupport.detectUnicode(StandardCharsets.UTF_8, "1")).isFalse();
        assertThat(TerminalSupport.detectUnicode(StandardCharsets.UTF_8, "true")).isFalse();
    }

    @Test
    void headingIncludesTitle() {
        assertThat(TerminalSupport.heading("RESULTS")).contains("RESULTS");
    }
}
