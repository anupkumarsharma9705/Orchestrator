package com.example.testfinal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for test-final.
 */
@SpringBootApplication
@EnableAsync
public class TestfinalApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestfinalApplication.class, args);
    }
}