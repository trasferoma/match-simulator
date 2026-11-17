package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public record Substitution(Player leaving, Player entering, Role sector, ChangeReason reason) {

    public Substitution {
        Objects.requireNonNull(leaving, "leaving player must not be null");
        Objects.requireNonNull(entering, "entering player must not be null");
        Objects.requireNonNull(sector, "sector of the entering player must not be null");
        Objects.requireNonNull(reason, "substitution reason must not be null");
    }
}
