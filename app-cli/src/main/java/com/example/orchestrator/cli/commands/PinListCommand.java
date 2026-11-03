package com.example.orchestrator.cli.commands;

import com.example.orchestrator.cli.CompanionCli;
import com.example.orchestrator.cli.security.CertificatePinningManager;
import com.example.orchestrator.cli.util.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * List all pinned certificates.
 */
@Command(name = "pin-list", description = "List all pinned certificates")
public class PinListCommand implements Runnable {

    @Option(names = {"-f", "--file"}, description = "Pin file to list")
    String pinFile;

    @Override
    public void run() {
        CertificatePinningManager pinManager = new CertificatePinningManager();
        
        if (pinFile != null) {
            try {
                pinManager.loadPinsFromFile(java.nio.file.Paths.get(pinFile));
            } catch (Exception e) {
                System.err.println(Ansi.error("Failed to load pin file: " + e.getMessage()));
                System.exit(1);
            }
        }
        
        System.out.println(Ansi.bold("Pinned Certificates:"));
        for (String hostname : pinManager.getPinnedHosts()) {
            System.out.println(Ansi.bold("\n  " + hostname + ":"));
            for (String pin : pinManager.getPins(hostname)) {
                System.out.println("    " + pin);
            }
        }
        
        if (pinManager.getPinnedHosts().isEmpty()) {
            System.out.println(Ansi.dim("  No pins configured"));
        }
    }
}