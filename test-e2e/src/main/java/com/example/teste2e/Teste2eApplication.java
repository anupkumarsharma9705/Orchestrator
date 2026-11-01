package com.example.teste2e;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for test-e2e.
 */
@SpringBootApplication
@EnableAsync
public class Teste2eApplication {

    public static void main(String[] args) {
        SpringApplication.run(Teste2eApplication.class, args);
    }
}