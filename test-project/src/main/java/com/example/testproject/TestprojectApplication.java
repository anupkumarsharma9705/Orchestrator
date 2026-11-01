package com.example.testproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for test-project.
 */
@SpringBootApplication
@EnableAsync
public class TestprojectApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestprojectApplication.class, args);
    }
}