package com.example.orchestrator.cli.commands;

import com.example.orchestrator.cli.CompanionCli;
import com.example.orchestrator.cli.util.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * Certificate pinning management commands.
 */
@Command(
    name = "pin",
    description = "Manage certificate pinning for mTLS connections",
    subcommands = {
        PinGenerateCommand.class,
        PinVerifyCommand.class,
        PinListCommand.class,
        PinAddCommand.class,
        PinRemoveCommand.class
    }
)
public class PinCommand implements Runnable {

    @ParentCommand
    private CompanionCli parent;

    @Override
    public void run() {
        System.out.println(Ansi.bold("Certificate Pinning Commands:"));
        System.out.println("  pin generate  - Generate pins for a host (TOFU)");
        System.out.println("  pin verify    - Verify pins for a host");
        System.out.println("  pin list      - List all pinned certificates");
        System.out.println("  pin add       - Add a pin for a host");
        System.out.println("  pin remove    - Remove a pin for a host");
    }
}