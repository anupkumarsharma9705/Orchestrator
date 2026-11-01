package com.example.testfinalall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for test-final-all.
 */
@SpringBootApplication
@EnableAsync
public class TestfinalallApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestfinalallApplication.class, args);
    }
}