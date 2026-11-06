package com.example.orchestrator.tool.impl;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeBoundedCharSequenceTest {

    @Test
    void normalMatchCompletesWithinBudget() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        var matcher = Pattern.compile("AKIA[0-9A-Z]{16}")
            .matcher(new TimeBoundedCharSequence("key=AKIAIOSFODNN7EXAMPLE", deadline));

        assertTrue(matcher.find());
    }

    @Test
    void catastrophicBacktrackingIsAbortedByTheDeadline() {
        // (a+)+$ is the canonical catastrophic-backtracking pattern. The
        // trailing '!' forces the engine to explore an exponential number of
        // ways to partition the a's.
        String input = "a".repeat(10_000) + "!";
        long timeoutMs = 50;

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            var matcher = Pattern.compile("(a+)+$")
                .matcher(new TimeBoundedCharSequence(input, deadline));

            assertThrows(TimeBoundedCharSequence.RegexTimeoutException.class, matcher::find);
        });
    }
}
