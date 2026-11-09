package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;

public record LineupOutcome(TeamOnPitch team, boolean suspendsTheMatch, List<Substitution> substitutions) {

    public LineupOutcome {
        Objects.requireNonNull(team, "team after the loss of a player must not be null");
        Objects.requireNonNull(substitutions, "substitutions must not be null");
        substitutions = List.copyOf(substitutions);
        if (suspendsTheMatch && !substitutions.isEmpty()) {
            throw new IllegalArgumentException("a suspended match has no substitutions after the loss of a player");
        }
    }

    public static LineupOutcome suspended(TeamOnPitch team) {
        return new LineupOutcome(team, true, List.of());
    }

    public static LineupOutcome continuing(RepairedLineup repaired) {
        return new LineupOutcome(repaired.team(), false, repaired.substitutions());
    }
}
