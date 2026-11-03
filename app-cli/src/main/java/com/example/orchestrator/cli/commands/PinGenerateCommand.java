package com.example.orchestrator.cli.commands;

import com.example.orchestrator.cli.CompanionCli;
import com.example.orchestrator.cli.security.CertificatePinningManager;
import com.example.orchestrator.cli.util.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * Generate certificate pins for a host (Trust On First Use).
 */
@Command(name = "pin-generate", description = "Generate certificate pins for a host (Trust On First Use)")
public class PinGenerateCommand implements Runnable {

    @Parameters(index = "0", description = "Hostname to generate pins for")
    String hostname;

    @Option(names = {"-p", "--port"}, description = "Port (default: 443)", defaultValue = "443")
    int port;

    @Option(names = {"-o", "--output"}, description = "Output pin file path")
    String outputFile;

    @Override
    public void run() {
        System.out.println(Ansi.info("Generating pins for " + hostname + ":" + port + "..."));
        
        try {
            CertificatePinningManager pinManager = new CertificatePinningManager();
            Set<String> pins = CertificatePinningManager.generatePinsForHost(hostname, port);
            
            System.out.println(Ansi.success("Generated pins for " + hostname + ":" + port));
            for (String pin : pins) {
                System.out.println("  " + Ansi.bold(pin));
            }

            if (outputFile != null) {
                Path outputPath = Paths.get(outputFile).toAbsolutePath();
                StringBuilder content = new StringBuilder();
                content.append("# Certificate pins for ").append(hostname).append(":").append(port).append("\n");
                content.append("# Generated on ").append(java.time.Instant.now()).append("\n\n");
                for (String pin : pins) {
                    content.append(hostname).append(" ").append(pin).append("\n");
                }
                
                java.nio.file.Files.writeString(Paths.get(outputFile), content.toString());
                System.out.println(Ansi.success("Pins saved to: " + outputPath));
            }
            
        } catch (Exception e) {
            System.err.println(Ansi.error("Failed to generate pins: " + e.getMessage()));
            System.exit(1);
        }
    }
}