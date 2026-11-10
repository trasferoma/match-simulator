package org.tasktelemetry.matchsimulator.narration;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.MatchSide;

public record TeamNames(String home, String away) {

    public TeamNames {
        Objects.requireNonNull(home, "home name must not be null");
        Objects.requireNonNull(away, "away name must not be null");
    }

    public String nameOf(MatchSide side) {
        return switch (side) {
            case HOME -> home;
            case AWAY -> away;
        };
    }
}
