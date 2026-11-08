package org.tasktelemetry.matchsimulator.engine.config;

import java.util.Objects;

public record CrossConfig(
        double crossProbability,
        double crossDifficulty,
        double clearedToCornerShare,
        DurationRange crossDuration,
        DurationRange clearanceDuration) {

    public CrossConfig {
        Objects.requireNonNull(crossDuration, "cross duration must not be null");
        Objects.requireNonNull(clearanceDuration, "clearance duration must not be null");
        requireOpenProbability("cross probability", crossProbability);
        requirePositiveAndFinite("cross difficulty", crossDifficulty);
        requireShare("cleared-to-corner share", clearedToCornerShare);
    }

    public static CrossConfig defaults() {
        double crossProbability = 0.20;
        double crossDifficulty = 200.0;
        double clearedToCornerShare = 0.20;
        DurationRange crossDuration = new DurationRange(3, 6);
        DurationRange clearanceDuration = new DurationRange(1, 3);
        return new CrossConfig(crossProbability, crossDifficulty, clearedToCornerShare, crossDuration,
                clearanceDuration);
    }

    private static void requireOpenProbability(String name, double value) {
        boolean isOpenInterval = value > 0.0 && value < 1.0;
        if (!isOpenInterval) {
            throw new IllegalArgumentException(name + " must be strictly between 0.0 and 1.0, was " + value);
        }
    }

    private static void requirePositiveAndFinite(String name, double value) {
        boolean isPositiveAndFinite = value > 0.0 && !Double.isInfinite(value);
        if (!isPositiveAndFinite) {
            throw new IllegalArgumentException(name + " must be positive and finite, was " + value);
        }
    }

    private static void requireShare(String name, double share) {
        boolean isShare = share >= 0.0 && share <= 1.0;
        if (!isShare) {
            throw new IllegalArgumentException(name + " must be between 0.0 and 1.0, was " + share);
        }
    }
}
