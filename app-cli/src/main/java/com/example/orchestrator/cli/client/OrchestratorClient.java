package com.example.orchestrator.cli.client;

import com.example.orchestrator.cli.config.CliConfig;
import com.example.orchestrator.cli.config.ConfigManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import javax.net.ssl.SSLException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.TrustManagerFactory;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.resolver.dns.DnsNameResolverException;

/**
 * Type-safe HTTP client to orchestrator REST API.
 * Uses Spring WebFlux WebClient with retry, timeout, and error mapping.
 * 
 * Security: Configures request/response size limits (H-07)
 * - Max inbound message size: 10MB
 * - Max inbound header size: 64KB
 * - Max initial line length: 8KB
 * 
 * Error Handling (HV-08): Maps raw connection exceptions to user-friendly messages
 */
public class OrchestratorClient {

    private static final int MAX_INBOUND_MESSAGE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_INBOUND_HEADER_SIZE = 64 * 1024; // 64KB
    private static final int MAX_INITIAL_LINE_LENGTH = 8 * 1024; // 8KB
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final CliConfig config;

    public OrchestratorClient(CliConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();

        String baseUrl = config.serverUrl();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        // Create SSL context if SSL is enabled
        SslContext sslContext = createSslContext(config);

        // Create HTTP client with SSL and SIZE LIMITS (H-07)
        HttpClient httpClient = HttpClient.create()
            .secure(ssl -> {
                if (config.ssl() != null && config.ssl().enabled()) {
                    ssl.sslContext(sslContext);
                }
            })
            // Size limits to prevent OOM from large responses
            .httpResponseDecoder(spec -> {
                spec.maxHeaderSize(MAX_INBOUND_HEADER_SIZE);
                spec.maxInitialLineLength(MAX_INITIAL_LINE_LENGTH);
                spec.maxChunkSize(MAX_INBOUND_MESSAGE_SIZE);
                return spec;
            })
            // Connection timeout
            .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            // Keep alive
            .keepAlive(true);

        this.webClient = WebClient.builder()
            .baseUrl(baseUrl + "api/")
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("Authorization", "Bearer " + config.apiKey())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            // WebClient-level size limit (additional defense)
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_INBOUND_MESSAGE_SIZE))
            .build();
    }

    private SslContext createSslContext(CliConfig config) {
        if (config.ssl() == null || !config.ssl().enabled()) {
            return null;
        }

        try {
            String trustStorePath = config.ssl().trustStore();
            String password = config.ssl().trustStorePassword();
            String type = config.ssl().trustStoreType();

            // Load trust store from classpath or file system
            InputStream trustStoreStream;
            if (trustStorePath.startsWith("classpath:")) {
                String classpathPath = trustStorePath.substring("classpath:".length());
                trustStoreStream = getClass().getClassLoader().getResourceAsStream(classpathPath);
                if (trustStoreStream == null) {
                    throw new IllegalStateException("Trust store not found on classpath: " + classpathPath);
                }
            } else {
                trustStoreStream = new java.io.FileInputStream(trustStorePath);
            }

            KeyStore trustStore = KeyStore.getInstance(type);
            try (InputStream is = trustStoreStream) {
                trustStore.load(is, config.ssl().trustStorePassword().toCharArray());
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            SslContextBuilder sslContextBuilder = SslContextBuilder.forClient()
                .trustManager(tmf);

            // Disable hostname verification for self-signed dev certs
            if (Boolean.FALSE.equals(config.ssl().verifyHostname())) {
                sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            }

            return sslContextBuilder.build();

        } catch (Exception e) {
            throw new IllegalStateException("Failed to create SSL context: " + e.getMessage(), e);
        }
    }

    /**
     * Register a new project.
     */
    public Mono<ProjectResponse> registerProject(ProjectRequest request) {
        return post("/projects", request, ProjectResponse.class)
            .onErrorMap(this::mapError);
    }

    /**
     * Submit a new task.
     */
    public Mono<TaskResponse> submitTask(CreateTaskRequest request) {
        return post("/tasks", request, TaskResponse.class)
            .onErrorMap(this::mapError);
    }

    /**
     * Get task by ID.
     */
    public Mono<TaskResponse> getTask(String taskId) {
        return get("/tasks/" + taskId, TaskResponse.class)
            .onErrorMap(this::mapError);
    }

    /**
     * List tasks with optional filters.
     */
    public Mono<TaskListResponse> listTasks(String projectId, String status) {
        return webClient.get()
            .uri(uriBuilder -> {
                var builder = uriBuilder.path("/tasks");
                if (projectId != null && !projectId.isBlank()) {
                    builder.queryParam("projectId", projectId);
                }
                if (status != null && !status.isBlank()) {
                    builder.queryParam("status", status);
                }
                return builder.build();
            })
            .retrieve()
            .bodyToMono(TaskListResponse.class)
            .onErrorMap(this::mapError);
    }

    /**
     * Approve a task.
     */
    public Mono<ApprovalResponse> approveTask(String taskId, String approver, String comment) {
        Map<String, String> body = Map.of(
            "approver", approver,
            "comment", comment != null ? comment : ""
        );
        return post("/approvals/" + taskId + "/approve", body, ApprovalResponse.class)
            .onErrorMap(this::mapError);
    }

    /**
     * Reject a task.
     */
    public Mono<ApprovalResponse> rejectTask(String taskId, String approver, String reason) {
        Map<String, String> body = Map.of(
            "approver", approver,
            "reason", reason
        );
        return post("/approvals/" + taskId + "/reject", body, ApprovalResponse.class)
            .onErrorMap(this::mapError);
    }

    /**
     * Get task artifacts.
     */
    public Mono<ArtifactsResponse> getArtifacts(String taskId) {
        return get("/tasks/" + taskId + "/artifacts", ArtifactsResponse.class)
            .onErrorMap(this::mapError);
    }

    /**
     * Download a specific artifact file.
     */
    public Mono<byte[]> downloadArtifact(String taskId, String artifactName) {
        return webClient.get()
            .uri("/tasks/" + taskId + "/artifacts/" + artifactName)
            .retrieve()
            .bodyToMono(byte[].class)
            .retryWhen(retrySpec())
            .timeout(Duration.ofSeconds(config.timeoutSeconds()))
            .onErrorMap(this::mapError);
    }

    /**
     * Get task logs.
     */
    public Mono<LogsResponse> getLogs(String taskId, boolean follow) {
        return get("/tasks/" + taskId + "/logs?follow=" + follow, LogsResponse.class)
            .onErrorMap(this::mapError);
    }

    /**
     * Health check endpoint.
     * Uses root actuator endpoint (not under /api/).
     */
    public Mono<HealthResponse> healthCheck() {
        // Use absolute URI to bypass the /api/ base path
        String baseUrl = config.serverUrl();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        String healthUrl = baseUrl + "actuator/health";

        WebClient.Builder builder = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("Authorization", "Bearer " + config.apiKey());

        // Add SSL if enabled
        SslContext sslContext = createSslContext(config);
        if (sslContext != null) {
            builder.clientConnector(new ReactorClientHttpConnector(
                HttpClient.create().secure(ssl -> ssl.sslContext(sslContext))
            ));
        }

        return builder
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("Authorization", "Bearer " + config.apiKey())
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_INBOUND_MESSAGE_SIZE))
            .build()
            .get()
            .uri(healthUrl)
            .retrieve()
            .bodyToMono(HealthResponse.class)
            .timeout(Duration.ofSeconds(5))
            .onErrorMap(this::mapError);
    }

    /**
     * Submit clarification answers.
     */
    public Mono<TaskResponse> submitClarificationAnswers(String taskId, Map<String, String> answers) {
        return post("/tasks/" + taskId + "/clarification", answers, TaskResponse.class)
            .onErrorMap(this::mapError);
    }

    private <T, R> Mono<R> post(String path, T body, Class<R> responseType) {
        return webClient.post()
            .uri(path)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(responseType)
            .retryWhen(retrySpec())
            .timeout(Duration.ofSeconds(config.timeoutSeconds()));
    }

    private <R> Mono<R> get(String path, Class<R> responseType) {
        return webClient.get()
            .uri(path)
            .retrieve()
            .bodyToMono(responseType)
            .retryWhen(retrySpec())
            .timeout(Duration.ofSeconds(config.timeoutSeconds()));
    }

    private Retry retrySpec() {
        return Retry.backoff(3, Duration.ofSeconds(2))
            .maxBackoff(Duration.ofSeconds(10))
            .jitter(0.1)
            .filter(throwable -> throwable instanceof WebClientResponseException
                && ((WebClientResponseException) throwable).getStatusCode().is5xxServerError());
    }

    /**
     * Maps raw WebClient/Netty exceptions to user-friendly exceptions (HV-08).
     * Prevents raw socket errors like "getsockopt" from reaching users.
     */
    private Throwable mapError(Throwable throwable) {
        // Unwrap Reactor's wrapped exceptions
        Throwable cause = unwrap(throwable);
        
        if (cause instanceof WebClientResponseException ex) {
            return mapHttpError(ex);
        }
        
        if (cause instanceof ConnectException) {
            return new ConnectionFailedException(
                "Cannot connect to orchestrator server at " + config.serverUrl() +
                ". Please verify the server is running and accessible.",
                cause
            );
        }
        
        if (cause instanceof ConnectTimeoutException || cause instanceof SocketTimeoutException) {
            return new ConnectionTimeoutException(
                "Connection to orchestrator server timed out (" + config.serverUrl() + "). " +
                "The server may be overloaded or network issues exist.",
                cause
            );
        }
        
        if (cause instanceof UnknownHostException) {
            return new ConnectionFailedException(
                "Cannot resolve hostname for " + config.serverUrl() +
                ". Please check your network configuration and DNS settings.",
                cause
            );
        }
        
        if (cause instanceof java.util.concurrent.TimeoutException) {
            return new TimeoutException("Request timed out after " + config.timeoutSeconds() + " seconds");
        }
        
        if (cause instanceof SSLException) {
            return new SslHandshakeException(
                "SSL/TLS handshake failed with " + config.serverUrl() +
                ". Verify certificate configuration and trust store.",
                cause
            );
        }
        
        if (cause instanceof io.netty.resolver.dns.DnsNameResolverException) {
            return new ConnectionFailedException(
                "DNS resolution failed for " + config.serverUrl() +
                ". Check your network connectivity and DNS settings.",
                cause
            );
        }
        
        // Generic fallback - don't expose raw stack traces
        return new ApiException("Connection error: " + cause.getClass().getSimpleName() + 
            " - " + sanitizeMessage(cause.getMessage()));
    }
    
    /**
     * Recursively unwraps Reactor's exception wrappers to get the root cause.
     */
    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            // Check for common wrapper exceptions
            if (current instanceof WebClientResponseException ||
                current instanceof ConnectException ||
                current instanceof ConnectTimeoutException ||
                current instanceof SocketTimeoutException ||
                current instanceof UnknownHostException ||
                current instanceof SSLException ||
                current instanceof io.netty.resolver.dns.DnsNameResolverException ||
                current instanceof java.util.concurrent.TimeoutException) {
                return current;
            }
            // Check for Reactor's exceptions that wrap causes
            if (current.getCause() != null && current.getCause() != current) {
                current = current.getCause();
            } else {
                break;
            }
        }
        return throwable;
    }
    
    private Throwable mapHttpError(WebClientResponseException ex) {
        return switch (ex.getStatusCode().value()) {
            case 400 -> new BadRequestException(sanitizeMessage(ex.getResponseBodyAsString()));
            case 401 -> new UnauthorizedException("Authentication failed. Check your API key configuration.");
            case 403 -> new ForbiddenException("Access denied. Insufficient permissions for this operation.");
            case 404 -> new NotFoundException("Resource not found: " + sanitizeMessage(ex.getResponseBodyAsString()));
            case 409 -> new ConflictException(sanitizeMessage(ex.getResponseBodyAsString()));
            case 500 -> new ServerErrorException("Server error: " + sanitizeMessage(ex.getResponseBodyAsString()));
            case 502 -> new ServerErrorException("Bad gateway - orchestrator server may be starting up");
            case 503 -> new ServerErrorException("Service unavailable - orchestrator server is overloaded or down");
            case 504 -> new ServerErrorException("Gateway timeout - server took too long to respond");
            default -> new ApiException("HTTP " + ex.getStatusCode().value() + ": " + sanitizeMessage(ex.getResponseBodyAsString()));
        };
    }
    
    /**
     * Sanitizes error messages to prevent information leakage.
     * Removes stack traces, internal paths, and sensitive data.
     */
    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown error";
        }
        // Truncate very long messages
        if (message.length() > 500) {
            message = message.substring(0, 500) + "... [truncated]";
        }
        // Remove file paths
        message = message.replaceAll("(?:[a-zA-Z]:)?(?:[/\\\\][^/\\\\:*?\"<>|]+)+", "[path]");
        // Remove stack trace lines
        message = message.replaceAll("\\s+at\\s+[\\w.$]+\\([^)]*\\)", "");
        return message.trim();
    }

    // ===== DTO Classes =====

    public record ProjectRequest(String name, String rootPath, String description) {}
    public record ProjectResponse(String projectId, String name, String rootPath, String createdAt) {}

    public record CreateTaskRequest(String projectId, String request, String requester) {}
    public record TaskResponse(String taskId, String projectId, String request, String requester,
                                String status, String createdAt, String error, String planJson) {}
    public record TaskListResponse(java.util.List<TaskResponse> tasks) {}

    public record ApprovalResponse(boolean approved, String taskId, String message) {}

    public record ArtifactsResponse(String taskId, java.util.List<Artifact> artifacts) {}
    public record Artifact(String name, String path, String type, long size, String createdAt, String checksum) {}

    public record LogsResponse(String taskId, String stdout, String stderr, boolean finished) {}

    public record HealthResponse(String status, Map<String, Object> details, Map<String, Object> components) {
        public boolean isUp() {
            return "UP".equalsIgnoreCase(status) || "UP".equalsIgnoreCase(components != null ? (String) components.get("status") : null);
        }
    }

    // ===== Exception Classes =====

    public static class ApiException extends RuntimeException {
        public ApiException(String message) { super(message); }
        public ApiException(String message, Throwable cause) { super(message, cause); }
    }
    
    public static class BadRequestException extends ApiException {
        public BadRequestException(String message) { super(message); }
    }
    
    public static class UnauthorizedException extends ApiException {
        public UnauthorizedException(String message) { super(message); }
    }
    
    public static class ForbiddenException extends ApiException {
        public ForbiddenException(String message) { super(message); }
    }
    
    public static class NotFoundException extends ApiException {
        public NotFoundException(String message) { super(message); }
    }
    
    public static class ConflictException extends ApiException {
        public ConflictException(String message) { super(message); }
    }
    
    public static class ServerErrorException extends ApiException {
        public ServerErrorException(String message) { super(message); }
    }
    
    public static class TimeoutException extends ApiException {
        public TimeoutException(String message) { super(message); }
    }
    
    public static class ConnectionFailedException extends ApiException {
        public ConnectionFailedException(String message, Throwable cause) { super(message, cause); }
    }
    
    public static class ConnectionTimeoutException extends ApiException {
        public ConnectionTimeoutException(String message, Throwable cause) { super(message, cause); }
    }
    
    public static class SslHandshakeException extends ApiException {
        public SslHandshakeException(String message, Throwable cause) { super(message, cause); }
    }
}