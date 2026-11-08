package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;

public record RepairedLineup(TeamOnPitch team, List<Substitution> substitutions) {

    public RepairedLineup {
        Objects.requireNonNull(team, "repaired team must not be null");
        Objects.requireNonNull(substitutions, "substitutions must not be null");
        substitutions = List.copyOf(substitutions);
    }

    public static RepairedLineup withoutSubstitution(TeamOnPitch team) {
        return new RepairedLineup(team, List.of());
    }
}
