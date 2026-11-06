package com.example.orchestrator.config;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpClientConfigTest {

    @Test
    void defaultClientUsesJvmTrustAndHostnameVerification() {
        OkHttpClient client = new HttpClientConfig().okHttpClient("", "", "PKCS12");

        assertThat(client.sslSocketFactory()).isNotNull();
        assertThat(client.hostnameVerifier()).isNotNull();
    }

    @Test
    void additionalTrustStoreIsLoaded(@TempDir Path tempDir) throws Exception {
        Path trustStore = tempDir.resolve("additional-truststore.p12");
        char[] password = "changeit".toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        try (OutputStream output = Files.newOutputStream(trustStore)) {
            keyStore.store(output, password);
        }

        OkHttpClient client = new HttpClientConfig()
                .okHttpClient(trustStore.toString(), new String(password), "PKCS12");

        assertThat(client.sslSocketFactory()).isNotNull();
        assertThat(client.hostnameVerifier()).isNotNull();
    }

    @Test
    void missingTrustStoreFailsClosed() {
        assertThatThrownBy(() -> new HttpClientConfig()
                .okHttpClient("/does/not/exist/truststore.p12", "changeit", "PKCS12"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outbound HTTP TLS");
    }
}
