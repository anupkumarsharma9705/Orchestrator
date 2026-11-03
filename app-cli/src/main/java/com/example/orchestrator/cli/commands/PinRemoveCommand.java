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

/**
 * Remove a pin for a host.
 */
@Command(name = "pin-remove", description = "Remove a pin for a host")
public class PinRemoveCommand implements Runnable {

    @Parameters(index = "0", description = "Hostname")
    String hostname;

    @Parameters(index = "1", description = "Pin to remove (sha256/...)")
    String pin;

    @Option(names = {"-f", "--file"}, description = "Pin file to update")
    String pinFile;

    @Override
    public void run() {
        if (!pin.startsWith("sha256/")) {
            System.err.println(Ansi.error("Pin must start with 'sha256/'"));
            System.exit(1);
        }
        
        CertificatePinningManager pinManager = new CertificatePinningManager();
        
        if (pinFile != null) {
            try {
                pinManager.loadPinsFromFile(java.nio.file.Paths.get(pinFile));
            } catch (Exception e) {
                System.err.println(Ansi.error("Failed to load pin file: " + e.getMessage()));
                System.exit(1);
            }
        }
        
        pinManager.removePin(hostname, pin);
        System.out.println(Ansi.success("Removed pin for " + hostname + ": " + pin));
    }
}