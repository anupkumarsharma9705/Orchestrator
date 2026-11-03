package {{package}};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for {{projectName}}.
 */
@SpringBootApplication
@EnableAsync
public final class {{className}} {

    private {{className}}() {
        // Prevent accidental instantiation; Spring Boot starts the app from main.
    }

    public static void main(String[] args) {
        SpringApplication.run({{className}}.class, args);
    }
}