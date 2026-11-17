package org.tasktelemetry.matchsimulator.coach.reading;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public record Dismissal(MatchSide side, Player player, Role sector) {

    public Dismissal {
        Objects.requireNonNull(side, "side must not be null");
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(sector, "sector must not be null");
    }
}
