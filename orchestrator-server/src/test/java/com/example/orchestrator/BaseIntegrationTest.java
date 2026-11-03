package com.example.orchestrator;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.yml", inheritLocations = false)
@ContextConfiguration(classes = SharedTestConfig.class)
public class BaseIntegrationTest {
    // Base class for integration tests - provides test profile configuration
}