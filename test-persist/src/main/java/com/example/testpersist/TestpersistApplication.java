package com.example.testpersist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for test-persist.
 */
@SpringBootApplication
@EnableAsync
public class TestpersistApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestpersistApplication.class, args);
    }
}