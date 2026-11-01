package com.example.testh14;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for test-h14.
 */
@SpringBootApplication
@EnableAsync
public class Testh14Application {

    public static void main(String[] args) {
        SpringApplication.run(Testh14Application.class, args);
    }
}