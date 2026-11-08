package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.team.Player;

public record Substitution(Player leaving, Player entering) {

    public Substitution {
        Objects.requireNonNull(leaving, "leaving player must not be null");
        Objects.requireNonNull(entering, "entering player must not be null");
    }
}
