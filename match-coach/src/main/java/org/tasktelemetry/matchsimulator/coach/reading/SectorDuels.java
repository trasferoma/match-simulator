package org.tasktelemetry.matchsimulator.coach.reading;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.team.Role;

public record SectorDuels(DuelCount defence, DuelCount midfield, DuelCount attack) {

    public SectorDuels {
        Objects.requireNonNull(defence, "defence duels must not be null");
        Objects.requireNonNull(midfield, "midfield duels must not be null");
        Objects.requireNonNull(attack, "attack duels must not be null");
    }

    public DuelCount countOf(Role sector) {
        return switch (sector) {
            case DEFENDER -> defence;
            case MIDFIELDER -> midfield;
            case FORWARD -> attack;
            case GOALKEEPER -> throw new IllegalArgumentException("the goalkeeper is not a sector with duels");
        };
    }
}
