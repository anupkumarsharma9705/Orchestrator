package com.example.orchestrator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Enable strict parsing (H-04, H-07: OOM prevention)
        mapper.configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);
        mapper.configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, true);
        // Size limits - Jackson 2.15+ uses StreamReadConstraints (H-07)
        mapper.getFactory().setStreamReadConstraints(
            StreamReadConstraints.builder()
                .maxDocumentLength(1_000_000) // 1MB max document
                .maxNestingDepth(100)
                .maxNumberLength(100)
                .maxStringLength(100_000) // 100KB max string
                .build()
        );
        return mapper;
    }
}