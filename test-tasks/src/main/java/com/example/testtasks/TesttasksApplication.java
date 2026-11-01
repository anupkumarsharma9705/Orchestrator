package com.example.testtasks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for test-tasks.
 */
@SpringBootApplication
@EnableAsync
public class TesttasksApplication {

    public static void main(String[] args) {
        SpringApplication.run(TesttasksApplication.class, args);
    }
}