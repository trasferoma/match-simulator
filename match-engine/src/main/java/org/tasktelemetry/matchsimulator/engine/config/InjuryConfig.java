package org.tasktelemetry.matchsimulator.engine.config;

import java.util.Objects;

public record InjuryConfig(double probabilityPerAction, DurationRange duration) {

    public InjuryConfig {
        Objects.requireNonNull(duration, "injury duration must not be null");
        requireBelowOne(probabilityPerAction);
    }

    public static InjuryConfig defaults() {
        double probabilityPerAction = 0.002;
        DurationRange duration = new DurationRange(60, 150);
        return new InjuryConfig(probabilityPerAction, duration);
    }

    private static void requireBelowOne(double probability) {
        boolean isBelowOne = probability >= 0.0 && probability < 1.0;
        if (!isBelowOne) {
            throw new IllegalArgumentException("injury probability per action must be at least 0.0 and below 1.0,"
                    + " was " + probability);
        }
    }
}
