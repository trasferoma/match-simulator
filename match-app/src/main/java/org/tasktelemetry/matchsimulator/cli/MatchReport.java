package org.tasktelemetry.matchsimulator.cli;

import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.summary.MatchSummary;

record MatchReport(String homeName, String awayName, List<List<String>> chronicle, MatchSummary summary, long seed) {

    MatchReport {
        Objects.requireNonNull(homeName, "home name must not be null");
        Objects.requireNonNull(awayName, "away name must not be null");
        Objects.requireNonNull(chronicle, "chronicle must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        chronicle = List.copyOf(chronicle);
    }

    Scoreboard scoreboard() {
        return new Scoreboard(homeName, awayName, summary, seed);
    }
}
