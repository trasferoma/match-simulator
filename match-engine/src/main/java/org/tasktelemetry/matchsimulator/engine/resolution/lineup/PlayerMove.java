package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public record PlayerMove(Player player, Role sector, ChangeReason reason) {

    public PlayerMove {
        Objects.requireNonNull(player, "moved player must not be null");
        Objects.requireNonNull(sector, "new sector must not be null");
        Objects.requireNonNull(reason, "move reason must not be null");
    }
}
