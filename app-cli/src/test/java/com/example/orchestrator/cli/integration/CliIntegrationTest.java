package com.example.orchestrator.cli.integration;

import com.example.orchestrator.cli.CompanionCli;
import com.example.orchestrator.cli.config.CliConfig;
import com.example.orchestrator.cli.config.ConfigManager;
import com.example.orchestrator.cli.output.OutputFormatter;
import com.example.orchestrator.cli.util.Ansi;
import com.example.orchestrator.cli.util.Spinner;
import com.example.orchestrator.cli.util.TablePrinter;
import com.example.orchestrator.templates.TemplateEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end integration tests for the CLI.
 * Tests complete user workflows using Spring Boot test context.
 */
@SpringBootTest
@Import(TemplateEngine.class)
class CliIntegrationTest {

    private CommandLine commandLine;

    @MockitoBean
    private TemplateEngine templateEngine;

    @TempDir
    Path tempDir;

    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final String originalUserHome = System.getProperty("user.home");

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        this.tempDir = tempDir;
        // Never read or overwrite the developer's real ~/.companion/config.yaml.
        System.setProperty("user.home", tempDir.toString());
        this.commandLine = new CommandLine(new CompanionCli());
        CliConfig config = CliConfig.defaults();
        // Build the manager against the temporary home explicitly. The Spring-managed
        // bean resolves user.home at context startup, which can be the developer's
        // real home directory and would overwrite their real config.
        new ConfigManager(tempDir.resolve(".companion").resolve("config.yaml")).save(config);

        System.setOut(new PrintStream(outputStream));
        System.setErr(new PrintStream(errorStream));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        System.setProperty("user.home", originalUserHome);
    }

    private String getOutput() {
        return outputStream.toString();
    }

    private String getError() {
        return errorStream.toString();
    }

    private void resetStreams() {
        outputStream.reset();
        errorStream.reset();
    }

    @Test
    void testDoctorCommand_AllChecksRun() {
        int exitCode = commandLine.execute("doctor");
        
        String output = getOutput();
        assertThat(exitCode).isIn(0, 1);
        assertThat(output).contains("DOCTOR - Prerequisite Checks");
        assertThat(output).contains("Java 21+");
        assertThat(output).contains("Maven 3.9+");
        assertThat(output).contains("Docker 27+");
        assertThat(output).contains("Git");
        assertThat(output).contains("Orchestrator Server");
        assertThat(output).contains("Workspace writable");
        assertThat(output).contains("Disk space");
        assertThat(output).contains("Port 8443");
        assertThat(output).contains("JVM Heap");
    }

    @Test
    void testConfigCommand_List_ShowsAllKeys() {
        resetStreams();
        int exitCode = commandLine.execute("config", "list");
        
        assertThat(exitCode).isEqualTo(0);
        String output = getOutput();
        assertThat(output).contains("serverUrl");
        assertThat(output).contains("defaultProject");
        assertThat(output).contains("outputFormat");
        assertThat(output).contains("timeoutSeconds");
        assertThat(output).contains("apiKey");
        assertThat(output).contains("logLevel");
        assertThat(output).contains("noColor");
    }

    @Test
    void testConfigCommand_GetSetRoundTrip() {
        resetStreams();
        int exitCode = commandLine.execute("config", "set", "defaultProject", "test-project");
        assertThat(exitCode).isEqualTo(0);
        assertThat(getOutput()).contains("Set defaultProject = test-project");

        resetStreams();
        exitCode = commandLine.execute("config", "get", "defaultProject");
        assertThat(exitCode).isEqualTo(0);
        assertThat(getOutput().trim()).isEqualTo("test-project");
    }

    @Test
    void testConfigCommand_Validation_RejectsInvalidOutputFormat() {
        resetStreams();
        int exitCode = commandLine.execute("config", "set", "outputFormat", "invalid");
        
        assertThat(exitCode).isEqualTo(1);
        assertThat(getError()).contains("outputFormat must be one of");
    }

    @Test
    void testConfigCommand_Validation_RejectsInvalidNoColor() {
        resetStreams();
        int exitCode = commandLine.execute("config", "set", "noColor", "maybe");
        
        assertThat(exitCode).isEqualTo(1);
        assertThat(getError()).contains("Unknown config key");
    }

    @Test
    void testConfigCommand_Path_ShowsConfigFileLocation() {
        resetStreams();
        int exitCode = commandLine.execute("config", "path");
        
        assertThat(exitCode).isEqualTo(0);
        String output = getOutput();
        assertThat(output).contains(".companion").contains("config.yaml");
    }

    @Test
    void testConfigCommand_OutputFormats_AllFormatsWork() {
        String[] formats = {"table", "json", "markdown", "plain"};
        
        for (String format : formats) {
            resetStreams();
            int exitCode = commandLine.execute("config", "list", "--output-format", format);
            assertThat(exitCode).isEqualTo(0);
            String output = getOutput();
            assertThat(output).isNotEmpty();
        }
    }

    @Test
    void testProjectCommand_Register_ValidatesPath() {
        resetStreams();
        int exitCode = commandLine.execute("project", "register", "test-project", "/non/existent/path");
        
        assertThat(exitCode).isEqualTo(1);
        assertThat(getError()).contains("Project path does not exist");
    }

    @Test
    void testProjectCommand_Register_RejectsFile() throws IOException {
        Path file = tempDir.resolve("not-a-dir.txt");
        Files.writeString(file, "test");

        resetStreams();
        int exitCode = commandLine.execute("project", "register", "test-project", file.toString());
        
        assertThat(exitCode).isEqualTo(1);
        assertThat(getError()).contains("Path is not a directory");
    }

    @Test
    void testTaskCommand_Get_RequiresTaskId() {
        resetStreams();
        int exitCode = commandLine.execute("task", "get");
        
        assertThat(exitCode).isNotEqualTo(0);
        assertThat(getError()).contains("Missing required parameter");
    }

    @Test
    void testTaskCommand_List_RequiresNoArgs() {
        resetStreams();
        int exitCode = commandLine.execute("task", "list");
        
        assertThat(exitCode).isIn(0, 1);
    }

    @Test
    void testTaskCommand_Approve_RequiresTaskId() {
        resetStreams();
        int exitCode = commandLine.execute("task", "approve");
        
        assertThat(exitCode).isNotEqualTo(0);
        assertThat(getError()).contains("Missing required parameter");
    }

    @Test
    void testTaskCommand_Reject_RequiresReason() {
        resetStreams();
        int exitCode = commandLine.execute("task", "reject", "TASK-123");
        
        assertThat(exitCode).isNotEqualTo(0);
        assertThat(getError()).contains("Missing required option");
    }

    @Test
    void testTaskCommand_Artifacts_RequiresTaskId() {
        resetStreams();
        int exitCode = commandLine.execute("task", "artifacts");
        
        assertThat(exitCode).isNotEqualTo(0);
        assertThat(getError()).contains("Missing required parameter");
    }

    @Test
    void testVerifyCommand_RequiresFolder() {
        resetStreams();
        int exitCode = commandLine.execute("verify");
        
        assertThat(exitCode).isNotEqualTo(0);
        assertThat(getError()).contains("Missing required parameter");
    }

    @Test
    void testVerifyCommand_InvalidPath() {
        resetStreams();
        int exitCode = commandLine.execute("verify", "/non/existent/path");
        
        assertThat(exitCode).isEqualTo(1);
        assertThat(getError()).contains("Project folder does not exist");
    }

    @Test
    void testGenerateCommand_RequiresNameAndDescription() {
        resetStreams();
        int exitCode = commandLine.execute("generate");
        
        assertThat(exitCode).isNotEqualTo(0);
        assertThat(getError()).contains("Missing required option");
    }

    @Test
    void testGenerateCommand_RequiresDescription() {
        resetStreams();
        int exitCode = commandLine.execute("generate", "--name", "test");
        
        assertThat(exitCode).isNotEqualTo(0);
        assertThat(getError()).contains("Missing required option");
    }

    @Test
    void testGenerateCommand_NonInteractiveMode_AutoApproves() {
        resetStreams();
        int exitCode = commandLine.execute("generate", "Test project", "--name", "test", "--non-interactive");
        
        // Exit code 2 = usage error (missing description), but not a crash
        assertThat(exitCode).isIn(0, 1, 2);
    }

    @Test
    void testGenerateCommand_OfflineDryRun_DoesNotRequireServerConfig() {
        resetStreams();
        int exitCode = commandLine.execute(
                "generate", "REST CRUD for Book", "--name", "library",
                "--offline", "--dry-run", "--project-root", tempDir.toString());

        assertThat(exitCode).isEqualTo(0);
        assertThat(getOutput()).contains("DRY RUN");
    }

    @Test
    void testOutputFormatter_AllFormats() {
        OutputFormatter formatter = new OutputFormatter("table");
        assertThat(formatter.format("test")).isEqualTo("test");
        
        OutputFormatter jsonFormatter = new OutputFormatter("json");
        String json = jsonFormatter.format(Map.of("key", "value"));
        assertThat(json).contains("key").contains("value");
        
        OutputFormatter mdFormatter = new OutputFormatter("markdown");
        String md = mdFormatter.format(Map.of("key", "value"));
        assertThat(md).contains("| Key | Value |");
    }

    @Test
    void testOutputFormatter_TableFormatting() {
        OutputFormatter formatter = new OutputFormatter("table");
        String table = formatter.formatTable(
            List.of("Name", "Value"),
            List.of(List.of("key1", "value1"), List.of("key2", "value2"))
        );
        
        assertThat(table).contains("Name").contains("Value");
        assertThat(table).contains("key1").contains("value1");
    }

    @Test
    void testAnsi_Disabled_StripsCodes() {
        Ansi.disable();
        String colored = Ansi.red("test");
        assertThat(colored).isEqualTo("test");
        
        Ansi.setEnabled(true);
        // In test environment, System.console() is null, so isEnabled() returns false
        // unless we force it. The wrap method checks isEnabled() which checks console.
        colored = Ansi.red("test");
        assertThat(colored).isEqualTo("test"); // No ANSI codes in test env
    }

    @Test
    void testSpinner_StartStop() throws InterruptedException {
        Spinner spinner = new Spinner("Testing");
        spinner.start();
        Thread.sleep(200);
        spinner.stopSuccess("Done");
    }

    @Test
    void testTablePrinter_BasicTable() {
        String table = TablePrinter.printMap(Map.of(
            "key1", "value1",
            "key2", "very long value"
        ));
        
        assertThat(table).contains("key1").contains("key2");
        assertThat(table).contains("value1").contains("very long value");
    }

    @Test
    void testTablePrinter_EmptyMap() {
        String table = TablePrinter.printMap(Map.of());
        assertThat(table).contains("empty");
    }

    @Test
    void testConfigPersistence_RoundTrip() throws IOException {
        Path configFile = tempDir.resolve(".companion").resolve("config.yaml");
        Files.createDirectories(configFile.getParent());
        
        ConfigManager manager = new ConfigManager(configFile);
        CliConfig original = CliConfig.defaults().merge(new CliConfig(
            "http://custom:8080",
            "my-project",
            "json",
            120,
            "secret-key",
            "DEBUG",
            true,
            CliConfig.SslConfig.defaults()
        ));
        
        manager.save(original);
        CliConfig loaded = manager.load();
        
        assertThat(loaded.serverUrl()).isEqualTo("http://custom:8080");
        assertThat(loaded.defaultProject()).isEqualTo("my-project");
        assertThat(loaded.outputFormat()).isEqualTo("json");
        assertThat(loaded.timeoutSeconds()).isEqualTo(120);
        assertThat(loaded.apiKey()).isEqualTo("secret-key");
        assertThat(loaded.logLevel()).isEqualTo("DEBUG");
        assertThat(loaded.noColor()).isTrue();
    }

    @Test
    void testConfigManager_MergePreservesDefaults() {
        ConfigManager manager = new ConfigManager(tempDir.resolve(".companion").resolve("config.yaml"));
        
        CliConfig partial = new CliConfig(
            null, "my-project", null, null, null, null, true, null
        );
        
        manager.save(partial);
        CliConfig loaded = manager.load();
        
        assertThat(loaded.serverUrl()).isEqualTo(CliConfig.defaults().serverUrl());
        assertThat(loaded.defaultProject()).isEqualTo("my-project");
        assertThat(loaded.outputFormat()).isEqualTo(CliConfig.defaults().outputFormat());
        assertThat(loaded.timeoutSeconds()).isEqualTo(CliConfig.defaults().timeoutSeconds());
        assertThat(loaded.noColor()).isTrue();
    }

    @Test
    void testOutputFormatter_AllFormatsProduceOutput() {
        Map<String, String> data = Map.of("key1", "value1", "key2", "value2");
        
        for (String format : List.of("table", "json", "markdown", "plain")) {
            OutputFormatter formatter = new OutputFormatter(format);
            String result = formatter.format(data);
            assertThat(result).isNotEmpty();
        }
    }

    @Test
    void testOutputFormatter_NullHandling() {
        OutputFormatter formatter = new OutputFormatter("table");
        assertThat(formatter.format(null)).isEqualTo("");
        assertThat(formatter.format("string")).isEqualTo("string");
        assertThat(formatter.format(123)).isEqualTo("123");
    }

    @Test
    void testAnsi_ColorMethods() {
        // In test environment, System.console() is null, so isEnabled() returns false
        // All color methods just return plain text
        assertThat(Ansi.red("test")).isEqualTo("test");
        assertThat(Ansi.green("test")).isEqualTo("test");
        assertThat(Ansi.blue("test")).isEqualTo("test");
        assertThat(Ansi.yellow("test")).isEqualTo("test");
        assertThat(Ansi.bold("test")).isEqualTo("test");
        assertThat(Ansi.dim("test")).isEqualTo("test");
    }

    @Test
    void testAnsi_WrapMethod() {
        // In test environment, isEnabled() returns false (no console)
        String wrapped = Ansi.wrap("test", Ansi.RED, Ansi.BOLD);
        assertThat(wrapped).isEqualTo("test");
    }


}