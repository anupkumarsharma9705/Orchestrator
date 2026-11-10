package com.example.library;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for library.
 */
@SpringBootApplication
@EnableAsync
public final class LibraryApplication {

    private LibraryApplication() {
        // Prevent accidental instantiation; Spring Boot starts the app from main.
    }

    public static void main(String[] args) {
        SpringApplication.run(LibraryApplication.class, args);
    }
}