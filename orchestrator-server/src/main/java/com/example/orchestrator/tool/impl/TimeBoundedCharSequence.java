package com.example.orchestrator.tool.impl;

/**
 * CharSequence wrapper that aborts a regular-expression match when a wall-clock
 * deadline is exceeded.
 *
 * <p>Java's {@link java.util.regex.Matcher} can backtrack catastrophically and
 * does not expose an interruptible or time-limited API on Java 21. Checking
 * elapsed time after {@code find()} returns does not protect against this: the
 * thread is already stuck inside the engine. This wrapper is consulted by the
 * regex engine for every character access, so a pathological pattern throws
 * {@link RegexTimeoutException} while it is running.</p>
 *
 * <p>The deadline is checked every 256 character accesses to keep the overhead
 * negligible. The overshoot is bounded by 256 cheap character reads.</p>
 */
final class TimeBoundedCharSequence implements CharSequence {

    private static final int CHECK_INTERVAL_MASK = 0xFF;

    private final CharSequence delegate;
    private final long deadlineNanos;
    private int accesses;

    TimeBoundedCharSequence(CharSequence delegate, long deadlineNanos) {
        this.delegate = delegate;
        this.deadlineNanos = deadlineNanos;
    }

    @Override
    public int length() {
        return delegate.length();
    }

    @Override
    public char charAt(int index) {
        if ((++accesses & CHECK_INTERVAL_MASK) == 0 && System.nanoTime() > deadlineNanos) {
            throw new RegexTimeoutException();
        }
        return delegate.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return new TimeBoundedCharSequence(delegate.subSequence(start, end), deadlineNanos);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    /**
     * Thrown when a regex exceeds its time budget. The stack trace is disabled
     * because this is a control-flow signal, not an unexpected error.
     */
    static final class RegexTimeoutException extends RuntimeException {
        RegexTimeoutException() {
            super("Regex execution exceeded its time budget", null, false, false);
        }
    }
}
