package com.example.orchestrator.config;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Outbound HTTP client configuration.
 *
 * <p>The client always uses normal TLS hostname verification and the JVM
 * default trust store. A deployment can add private trust anchors with
 * {@code orchestrator.http.tls.trust-store}; the configured store is used in
 * addition to the default store, so public provider APIs keep working.
 * There is no trust-all mode.</p>
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public OkHttpClient okHttpClient(
            @Value("${orchestrator.http.tls.trust-store:}") String trustStore,
            @Value("${orchestrator.http.tls.trust-store-password:}") String trustStorePassword,
            @Value("${orchestrator.http.tls.trust-store-type:PKCS12}") String trustStoreType) {
        try {
            X509TrustManager trustManager = buildTrustManager(
                    trustStore, trustStorePassword, trustStoreType);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, null);

            return new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Failed to configure outbound HTTP TLS", e);
        }
    }

    private static X509TrustManager buildTrustManager(
            String trustStore, String trustStorePassword, String trustStoreType)
            throws GeneralSecurityException, IOException {
        X509TrustManager defaults = defaultTrustManager();
        if (trustStore == null || trustStore.isBlank()) {
            return defaults;
        }

        String type = trustStoreType == null || trustStoreType.isBlank()
                ? "PKCS12" : trustStoreType;
        KeyStore keyStore = KeyStore.getInstance(type);
        char[] password = trustStorePassword == null || trustStorePassword.isEmpty()
                ? null : trustStorePassword.toCharArray();
        try (InputStream input = Files.newInputStream(Path.of(trustStore.trim()))) {
            keyStore.load(input, password);
        }

        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore);
        X509TrustManager custom = findX509TrustManager(factory);
        return new CompositeX509TrustManager(custom, defaults);
    }

    private static X509TrustManager defaultTrustManager() throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        return findX509TrustManager(factory);
    }

    private static X509TrustManager findX509TrustManager(TrustManagerFactory factory)
            throws GeneralSecurityException {
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager x509) {
                return x509;
            }
        }
        throw new GeneralSecurityException("No X509TrustManager available");
    }

    /**
     * Accepts a certificate chain when either the configured additional trust
     * store or the JVM default store trusts it. Both paths still perform full
     * chain and validity validation.
     */
    private static final class CompositeX509TrustManager implements X509TrustManager {

        private final X509TrustManager custom;
        private final X509TrustManager defaults;

        private CompositeX509TrustManager(X509TrustManager custom, X509TrustManager defaults) {
            this.custom = custom;
            this.defaults = defaults;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            try {
                custom.checkClientTrusted(chain, authType);
            } catch (CertificateException e) {
                defaults.checkClientTrusted(chain, authType);
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            try {
                custom.checkServerTrusted(chain, authType);
            } catch (CertificateException e) {
                defaults.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> issuers = new ArrayList<>();
            issuers.addAll(Arrays.asList(custom.getAcceptedIssuers()));
            issuers.addAll(Arrays.asList(defaults.getAcceptedIssuers()));
            return issuers.toArray(X509Certificate[]::new);
        }
    }
}
