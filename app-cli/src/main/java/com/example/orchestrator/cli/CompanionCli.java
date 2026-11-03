package com.example.orchestrator.cli;

import com.example.orchestrator.cli.commands.ConfigCommand;
import com.example.orchestrator.cli.commands.DoctorCommand;
import com.example.orchestrator.cli.commands.GenerateCommand;
import com.example.orchestrator.cli.commands.PinCommand;
import com.example.orchestrator.cli.commands.PinGenerateCommand;
import com.example.orchestrator.cli.commands.PinVerifyCommand;
import com.example.orchestrator.cli.commands.PinListCommand;
import com.example.orchestrator.cli.commands.PinAddCommand;
import com.example.orchestrator.cli.commands.PinRemoveCommand;
import com.example.orchestrator.cli.commands.ProjectCommand;
import com.example.orchestrator.cli.commands.SelfUpdateCommand;
import com.example.orchestrator.cli.commands.TaskCommand;
import com.example.orchestrator.cli.commands.VerifyCommand;
import com.example.orchestrator.cli.util.Ansi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

/**
 * Main entry point for the Companion CLI.
 * A CLI tool that generates a production-ready Spring Boot REST API project
 * from a natural language description, with built-in security scanning,
 * testing, and auto-fixing.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.example.orchestrator.cli", "com.example.orchestrator.templates"})
@Command(
    name = "companion",
    version = "0.0.1-SNAPSHOT",
    mixinStandardHelpOptions = true,
    description = {
        "Generate Spring Boot REST API projects from natural language descriptions,",
        "with secure defaults, security scanning, testing, and auto-fixing.",
        "",
        "Usage examples:",
        "  companion generate \"REST CRUD for Book entity\" --name library",
        "  companion generate \"Simple TODO API\" --name todo --offline",
        "  companion verify ./my-spring-project",
        "  companion doctor",
        "  companion task get TASK-ABC123",
        "  companion config set serverUrl https://localhost:8443"
    },
    subcommands = {
        GenerateCommand.class,
        VerifyCommand.class,
        DoctorCommand.class,
        ProjectCommand.class,
        TaskCommand.class,
        ConfigCommand.class,
        SelfUpdateCommand.class,
        PinCommand.class
    }
)
public class CompanionCli implements Callable<Integer> {

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    private boolean verbose;

    @Option(names = {"--no-color"}, description = "Disable colored output")
    private boolean noColor;

    @Option(names = {"--config"}, description = "Path to config file (default: ~/.companion/config.yaml)")
    private String configPath;

    private ConfigurableApplicationContext springContext;

    public static void main(String[] args) {
        CommandLine commandLine = new CommandLine(new CompanionCli())
            .setCaseInsensitiveEnumValuesAllowed(true);

        // Configuration and connectivity errors are user-facing; do not dump a
        // stack trace unless the developer explicitly asks for it.
        commandLine.setExecutionExceptionHandler((ex, cmd, parseResult) -> {
            String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            System.err.println(Ansi.error(" " + message));
            if ("1".equals(System.getenv("COMPANION_DEBUG"))) {
                ex.printStackTrace(System.err);
            }
            return 1;
        });

        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        // If no subcommand provided, show help
        System.out.println("Use 'companion --help' to see available commands.");
        System.out.println("Try: companion generate \"REST CRUD for Book\" --name library");
        return 0;
    }

    public void setSpringContext(ConfigurableApplicationContext context) {
        this.springContext = context;
    }

    public ConfigurableApplicationContext getSpringContext() {
        return springContext;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public boolean isNoColor() {
        return noColor;
    }

    public String getConfigPath() {
        return configPath;
    }
}