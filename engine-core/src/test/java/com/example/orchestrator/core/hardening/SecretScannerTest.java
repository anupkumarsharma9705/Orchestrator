package com.example.orchestrator.core.hardening;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecretScannerTest {

    private final SecretScanner scanner = new SecretScanner();

    @ParameterizedTest
    @ValueSource(strings = {
        "postgresql://app:s3cret@db.internal:5432/app",
        "postgres://app:s3cret@db.internal:5432/app",
        "jdbc:postgresql://app:s3cret@db.internal:5432/app",
        "mysql://app:s3cret@db.internal:3306/app",
        "jdbc:mysql://app:s3cret@db.internal:3306/app",
        "mariadb://app:s3cret@db.internal:3306/app",
        "sqlserver://sa:s3cret@db.internal:1433/app",
        "jdbc:sqlserver://sa:s3cret@db.internal:1433;databaseName=app",
        "oracle://app:s3cret@db.internal:1521/app",
        "mongodb://app:s3cret@db.internal:27017/app",
        "mongodb+srv://app:s3cret@cluster.internal/app",
        "redis://:s3cret@cache.internal:6379/0",
        "rediss://app:s3cret@cache.internal:6379/0",
        "sqlite://app:s3cret@db.internal/app"
    })
    void detectsCredentialsInDatabaseUrlVariants(String input) {
        assertThat(scanner.scan(input).hasSecrets()).isTrue();
    }

    @Test
    void detectsPasswordInJdbcQueryParameters() {
        String input = "jdbc:postgresql://db.internal:5432/app?user=app&password=s3cret";

        assertThat(scanner.scan(input).hasSecrets()).isTrue();
    }

    @Test
    void doesNotFlagCredentialFreeJdbcUrl() {
        String input = "jdbc:postgresql://db.internal:5432/app?sslmode=require";

        assertThat(scanner.scan(input).hasSecrets()).isFalse();
    }
}
