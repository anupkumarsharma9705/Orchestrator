package com.example.orchestrator.cli.util;

/**
 * Terminal spinner for progress indication.
 * Supports both Unicode braille and ASCII frames.
 */
public class Spinner {

    private final String[] frames;
    private volatile String message;
    private Thread spinnerThread;
    private volatile boolean running;
    private long startTime;

    public Spinner(String message) {
        this(message, true);
    }

    public Spinner(String message, boolean useBraille) {
        this.frames = TerminalSupport.spinnerFrames(useBraille);
        this.message = message;
    }

    public void start() {
        if (running) return;
        running = true;
        startTime = System.currentTimeMillis();

        spinnerThread = new Thread(() -> {
            int i = 0;
            while (running) {
                String frame = frames[i % frames.length];
                String elapsed = formatElapsed(System.currentTimeMillis() - startTime);
                System.out.print("\r" + Ansi.CYAN + frame + Ansi.RESET + " " + message + " " + Ansi.DIM + elapsed + Ansi.RESET);
                System.out.flush();
                i++;
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "spinner-" + message.hashCode());
        spinnerThread.setDaemon(true);
        spinnerThread.start();
    }

    // H-11: Allow updating the spinner message without recreating
    public void updateMessage(String newMessage) {
        this.message = newMessage;
    }

    public void stop(boolean success, String finalMessage) {
        running = false;
        if (spinnerThread != null) {
            spinnerThread.interrupt();
            try {
                spinnerThread.join(100);
            } catch (InterruptedException ignored) {}
        }

        String icon = success
            ? Ansi.GREEN + TerminalSupport.CHECK + Ansi.RESET
            : Ansi.RED + TerminalSupport.CROSS + Ansi.RESET;
        String elapsed = formatElapsed(System.currentTimeMillis() - startTime);
        System.out.print("\r" + icon + " " + finalMessage + " " + Ansi.DIM + elapsed + Ansi.RESET + "\n");
    }

    public void stopSuccess(String finalMessage) {
        stop(true, finalMessage);
    }

    public void stopFailure(String finalMessage) {
        stop(false, finalMessage);
    }

    private String formatElapsed(long ms) {
        long seconds = ms / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        return minutes + "m " + (seconds % 60) + "s";
    }
}