package org.tasktelemetry.matchsimulator.narration;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.MatchSide;

public record TeamNames(String home, String away, String homeCoach, String awayCoach) {

    public TeamNames {
        Objects.requireNonNull(home, "home name must not be null");
        Objects.requireNonNull(away, "away name must not be null");
        Objects.requireNonNull(homeCoach, "home coach name must not be null");
        Objects.requireNonNull(awayCoach, "away coach name must not be null");
    }

    public String nameOf(MatchSide side) {
        return switch (side) {
            case HOME -> home;
            case AWAY -> away;
        };
    }

    public String coachOf(MatchSide side) {
        return switch (side) {
            case HOME -> homeCoach;
            case AWAY -> awayCoach;
        };
    }
}
