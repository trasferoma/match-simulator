package org.tasktelemetry.matchsimulator.engine.event;

import java.util.Objects;

public record ShotProfile(double expectedGoals, ShotKind kind, ShotQuality quality, boolean penalty) {

    public ShotProfile {
        Objects.requireNonNull(kind, "shot kind must not be null");
        Objects.requireNonNull(quality, "shot quality must not be null");
        boolean isOpenInterval = expectedGoals > 0.0 && expectedGoals < 1.0;
        if (!isOpenInterval) {
            throw new IllegalArgumentException("expected goals must be strictly between 0.0 and 1.0, was "
                    + expectedGoals);
        }
    }

    public static ShotProfile ofShot(double expectedGoals, ShotKind kind, ShotQuality quality) {
        return new ShotProfile(expectedGoals, kind, quality, false);
    }

    public static ShotProfile ofPenalty(double expectedGoals, ShotQuality quality) {
        return new ShotProfile(expectedGoals, ShotKind.FOOT, quality, true);
    }
}
