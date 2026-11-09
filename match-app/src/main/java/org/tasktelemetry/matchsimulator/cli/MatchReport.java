package org.tasktelemetry.matchsimulator.cli;

import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.summary.MatchSummary;

record MatchReport(String homeName, String awayName, List<String> chronicle, MatchSummary summary, long seed) {

    MatchReport {
        Objects.requireNonNull(homeName, "home name must not be null");
        Objects.requireNonNull(awayName, "away name must not be null");
        Objects.requireNonNull(chronicle, "chronicle must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        chronicle = List.copyOf(chronicle);
    }

    String nameOf(MatchSide side) {
        return switch (side) {
            case HOME -> homeName;
            case AWAY -> awayName;
        };
    }
}
