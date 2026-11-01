package com.example.orchestrator.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class FingerprintUtilTest {

    @Test
    void compute_returnsConsistentHash() {
        String fp1 = FingerprintUtil.compute("semgrep", "java.sql-injection", 
            "src/main/java/BookRepository.java", "String query = \"SELECT * FROM books WHERE id = \" + id;");
        String fp2 = FingerprintUtil.compute("semgrep", "java.sql-injection", 
            "src/main/java/BookRepository.java", "String query = \"SELECT * FROM books WHERE id = \" + id;");

        assertThat(fp1).isEqualTo(fp2);
        assertThat(fp1).hasSize(64); // SHA-256 = 64 hex chars
    }

    @Test
    void compute_differentInputsProduceDifferentHashes() {
        String fp1 = FingerprintUtil.compute("semgrep", "java.sql-injection", 
            "src/main/java/BookRepository.java", "String query = \"SELECT * FROM books WHERE id = \" + id;");
        String fp2 = FingerprintUtil.compute("semgrep", "java.sql-injection", 
            "src/main/java/BookRepository.java", "String query = \"SELECT * FROM books WHERE id = \" + name;");

        assertThat(fp1).isNotEqualTo(fp2);
    }

    @Test
    void compute_survivesLineNumberChanges() {
        String codeLine1 = "String query = \"SELECT * FROM books WHERE id = \" + id;";
        String codeLine2 = "    String query = \"SELECT * FROM books WHERE id = \" + id;";
        
        String fp1 = FingerprintUtil.compute("semgrep", "java.sql-injection", 
            "src/main/java/BookRepository.java", codeLine1);
        String fp2 = FingerprintUtil.compute("semgrep", "java.sql-injection", 
            "src/main/java/BookRepository.java", codeLine2);

        assertThat(fp1).isEqualTo(fp2);
    }

    @Test
    void compute_differentToolsProduceDifferentHashes() {
        String fp1 = FingerprintUtil.compute("semgrep", "java.sql-injection", 
            "src/main/java/BookRepository.java", "String query = \"SELECT * FROM books WHERE id = \" + id;");
        String fp2 = FingerprintUtil.compute("spotbugs", "java.sql-injection", 
            "src/main/java/BookRepository.java", "String query = \"SELECT * FROM books WHERE id = \" + id;");

        assertThat(fp1).isNotEqualTo(fp2);
    }

    @Test
    void verify_returnsTrueForMatchingFingerprint() {
        String fp = FingerprintUtil.compute("semgrep", "java.sql-injection", 
            "src/main/java/BookRepository.java", "String query = \"SELECT * FROM books WHERE id = \" + id;");

        assertThat(FingerprintUtil.verify(fp, "semgrep", "java.sql-injection", 
            "src/main/java/BookRepository.java", "String query = \"SELECT * FROM books WHERE id = \" + id;"))
            .isTrue();
    }

    @Test
    void verify_returnsFalseForMismatchedFingerprint() {
        String fp = FingerprintUtil.compute("semgrep", "java.sql-injection", 
            "src/main/java/BookRepository.java", "String query = \"SELECT * FROM books WHERE id = \" + id;");

        assertThat(FingerprintUtil.verify(fp, "semgrep", "java.sql-injection", 
            "src/main/java/BookRepository.java", "String query = \"SELECT * FROM books WHERE id = \" + name;"))
            .isFalse();
    }

    @Test
    void computeWithLine_consistentWithLineNumber() {
        String fp1 = FingerprintUtil.computeWithLine("semgrep", "java.sql-injection",
            "src/main/java/BookRepository.java", 42, "String query = \"SELECT * FROM books WHERE id = \" + id;");
        String fp2 = FingerprintUtil.computeWithLine("semgrep", "java.sql-injection",
            "src/main/java/BookRepository.java", 42, "String query = \"SELECT * FROM books WHERE id = \" + id;");

        assertThat(fp1).isEqualTo(fp2);
    }

    @Test
    void computeWithLine_differentLineNumbersProduceDifferentHashes() {
        String fp1 = FingerprintUtil.computeWithLine("semgrep", "java.sql-injection",
            "src/main/java/BookRepository.java", 42, "String query = \"SELECT * FROM books WHERE id = \" + id;");
        String fp2 = FingerprintUtil.computeWithLine("semgrep", "java.sql-injection",
            "src/main/java/BookRepository.java", 43, "String query = \"SELECT * FROM books WHERE id = \" + id;");

        assertThat(fp1).isNotEqualTo(fp2);
    }

    @Test
    void compute_nullCodeLineHandledGracefully() {
        String fp = FingerprintUtil.compute("semgrep", "java.sql-injection",
            "src/main/java/BookRepository.java", null);

        assertThat(fp).isNotNull().hasSize(64);
    }

    @Test
    void compute_emptyCodeLineHandledGracefully() {
        String fp = FingerprintUtil.compute("semgrep", "java.sql-injection",
            "src/main/java/BookRepository.java", "");

        assertThat(fp).isNotNull().hasSize(64);
    }

    @Test
    void compute_specialCharactersInCodeLine() {
        String code = "String s = \"<script>alert('xss')</script>\";";
        String fp = FingerprintUtil.compute("semgrep", "java.xss", 
            "src/main/java/Controller.java", code);

        assertThat(fp).isNotNull().hasSize(64);
    }

    @Test
    void compute_unicodeInCodeLine() {
        String code = "String msg = \"Héllo 世界 🌍\";";
        String fp = FingerprintUtil.compute("semgrep", "java.encoding", 
            "src/main/java/Message.java", code);

        assertThat(fp).isNotNull().hasSize(64);
    }
}