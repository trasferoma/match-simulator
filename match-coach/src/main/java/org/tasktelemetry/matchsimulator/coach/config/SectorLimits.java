package org.tasktelemetry.matchsimulator.coach.config;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.team.Role;

public record SectorLimits(SectorLimit defenders, SectorLimit midfielders, SectorLimit forwards) {

    public SectorLimits {
        Objects.requireNonNull(defenders, "defenders limit must not be null");
        Objects.requireNonNull(midfielders, "midfielders limit must not be null");
        Objects.requireNonNull(forwards, "forwards limit must not be null");
    }

    public SectorLimit limitOf(Role sector) {
        return switch (sector) {
            case DEFENDER -> defenders;
            case MIDFIELDER -> midfielders;
            case FORWARD -> forwards;
            case GOALKEEPER -> throw new IllegalArgumentException("the goalkeeper has no sector limit");
        };
    }

    public static SectorLimits defaults() {
        SectorLimit defenders = new SectorLimit(3, 5);
        SectorLimit midfielders = new SectorLimit(2, 5);
        SectorLimit forwards = new SectorLimit(1, 4);
        return new SectorLimits(defenders, midfielders, forwards);
    }
}
