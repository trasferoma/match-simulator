package org.tasktelemetry.matchsimulator.engine.config;

import java.util.Objects;

public record LineupConfig(int maximumSubstitutions, double outOfPositionMultiplier,
        DurationRange substitutionDuration) {

    public LineupConfig {
        Objects.requireNonNull(substitutionDuration, "substitution duration must not be null");
        requireAtLeastOneSubstitution(maximumSubstitutions);
        requireWeakeningMultiplier("out-of-position multiplier", outOfPositionMultiplier);
    }

    public static LineupConfig defaults() {
        int maximumSubstitutions = 5;
        double outOfPositionMultiplier = 0.90;
        DurationRange substitutionDuration = new DurationRange(30, 45);
        return new LineupConfig(maximumSubstitutions, outOfPositionMultiplier, substitutionDuration);
    }

    private static void requireAtLeastOneSubstitution(int maximumSubstitutions) {
        if (maximumSubstitutions < 1) {
            throw new IllegalArgumentException("maximum substitutions must be at least 1, was "
                    + maximumSubstitutions);
        }
    }

    private static void requireWeakeningMultiplier(String name, double multiplier) {
        boolean isWeakening = multiplier > 0.0 && multiplier <= 1.0;
        if (!isWeakening) {
            throw new IllegalArgumentException(name + " must be above 0.0 and at most 1.0, was " + multiplier);
        }
    }
}
