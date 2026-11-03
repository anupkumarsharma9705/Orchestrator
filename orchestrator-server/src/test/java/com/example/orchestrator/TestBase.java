package com.example.orchestrator;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.context.annotation.Import;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.yml", inheritLocations = false)
@Import(SharedTestConfig.class)
public class TestBase {
    // Base class for integration tests - provides test profile configuration with SharedTestConfig
}