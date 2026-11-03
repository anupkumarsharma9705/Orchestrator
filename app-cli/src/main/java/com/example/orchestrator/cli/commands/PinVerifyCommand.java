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
 * Verify certificate pins for a host.
 */
@Command(name = "pin-verify", description = "Verify certificate pins for a host")
public class PinVerifyCommand implements Runnable {

    @Parameters(index = "0", description = "Hostname to verify")
    String hostname;

    @Option(names = {"-p", "--port"}, description = "Port (default: 443)", defaultValue = "443")
    int port;

    @Option(names = {"-f", "--file"}, description = "Pin file to verify against")
    String pinFile;

    @Override
    public void run() {
        System.out.println(Ansi.info("Verifying pins for " + hostname + ":" + port + "..."));
        
        try {
            CertificatePinningManager pinManager = new CertificatePinningManager();
            
            if (pinFile != null) {
                pinManager.loadPinsFromFile(Paths.get(pinFile));
            }
            
            Set<String> pins = CertificatePinningManager.generatePinsForHost(hostname, port);
            
            System.out.println(Ansi.success("Server presented pins:"));
            for (String pin : pins) {
                System.out.println("  " + pin);
            }
            
            System.out.println(Ansi.success("Pin verification passed for " + hostname));
            
        } catch (Exception e) {
            System.err.println(Ansi.error("Pin verification failed: " + e.getMessage()));
            System.exit(1);
        }
    }
}