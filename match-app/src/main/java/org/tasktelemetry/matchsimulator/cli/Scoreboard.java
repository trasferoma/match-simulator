package org.tasktelemetry.matchsimulator.cli;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.summary.MatchSummary;

record Scoreboard(String homeName, String awayName, MatchSummary summary, long seed) {

    Scoreboard {
        Objects.requireNonNull(homeName, "home name must not be null");
        Objects.requireNonNull(awayName, "away name must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
    }

    String nameOf(MatchSide side) {
        return switch (side) {
            case HOME -> homeName;
            case AWAY -> awayName;
        };
    }
}
