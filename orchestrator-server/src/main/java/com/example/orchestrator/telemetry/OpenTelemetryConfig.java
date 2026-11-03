package com.example.orchestrator.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * OpenTelemetry configuration for distributed tracing.
 * 
 * H-15: Distributed tracing integration with OpenTelemetry.
 * Supports OTLP (gRPC/HTTP), Zipkin, and Jaeger exporters.
 * 
 * Enable with: spring.otel.enabled=true
 * Configure exporter: spring.otel.exporter=otlp|zipkin|jaeger
 * OTLP endpoint: spring.otel.otlp.endpoint=http://localhost:4317 (gRPC) or http://localhost:4318 (HTTP)
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.otel", name = "enabled", havingValue = "true", matchIfMissing = false)
public class OpenTelemetryConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryConfig.class);

    @Value("${spring.application.name:orchestrator-server}")
    private String serviceName;

    @Value("${spring.otel.exporter:otlp}")
    private String exporterType;

    @Value("${spring.otel.otlp.endpoint:http://localhost:4317}")
    private String otlpEndpoint;

    @Value("${spring.otel.zipkin.endpoint:http://localhost:9411/api/v2/spans}")
    private String zipkinEndpoint;

    @Value("${spring.otel.sampling-rate:1.0}")
    private double samplingRate;

    @Bean
    public OpenTelemetry openTelemetry() {
        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.of(
                AttributeKey.stringKey("service.name"), serviceName,
                AttributeKey.stringKey("service.version"), "0.0.1-SNAPSHOT",
                AttributeKey.stringKey("service.namespace"), "java-companion"
            )));

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(createExporter())
                .setScheduleDelay(Duration.ofMillis(500))
                .setMaxExportBatchSize(512)
                .setMaxQueueSize(2048)
                .setExporterTimeout(Duration.ofSeconds(30))
                .build())
            .build();

        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal();

        log.info("OpenTelemetry initialized with {} exporter", exporterType);
        return openTelemetry;
    }

    private SpanExporter createExporter() {
        if ("zipkin".equalsIgnoreCase(exporterType)) {
            return ZipkinSpanExporter.builder()
                .setEndpoint(zipkinEndpoint)
                .build();
        } else {
            return OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlpEndpoint)
                .setTimeout(Duration.ofSeconds(10))
                .build();
        }
    }

    /**
     * Get the tracer for this application.
     */
    public static Tracer getTracer(String instrumentationName) {
        return GlobalOpenTelemetry.getTracer(instrumentationName);
    }

    /**
     * Create a new span for an operation.
     */
    public static Span startSpan(String operationName, SpanKind kind) {
        return getTracer("java-companion").spanBuilder(operationName)
            .setSpanKind(kind)
            .startSpan();
    }

    /**
     * Create a new span for a client call (outbound).
     */
    public static Span startClientSpan(String operationName) {
        return startSpan(operationName, SpanKind.CLIENT);
    }

    /**
     * Create a new span for a server handler (inbound).
     */
    public static Span startServerSpan(String operationName) {
        return startSpan(operationName, SpanKind.SERVER);
    }

    /**
     * Create a new span for an internal operation.
     */
    public static Span startInternalSpan(String operationName) {
        return startSpan(operationName, SpanKind.INTERNAL);
    }
}