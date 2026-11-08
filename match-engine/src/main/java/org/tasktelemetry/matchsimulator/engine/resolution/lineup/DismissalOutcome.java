package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;

public record DismissalOutcome(TeamOnPitch team, boolean suspendsTheMatch, List<Substitution> substitutions) {

    public DismissalOutcome {
        Objects.requireNonNull(team, "team after the dismissal must not be null");
        Objects.requireNonNull(substitutions, "substitutions must not be null");
        substitutions = List.copyOf(substitutions);
        if (suspendsTheMatch && !substitutions.isEmpty()) {
            throw new IllegalArgumentException("a suspended match has no substitutions after the dismissal");
        }
    }

    public static DismissalOutcome suspended(TeamOnPitch team) {
        return new DismissalOutcome(team, true, List.of());
    }

    public static DismissalOutcome continuing(RepairedLineup repaired) {
        return new DismissalOutcome(repaired.team(), false, repaired.substitutions());
    }
}
