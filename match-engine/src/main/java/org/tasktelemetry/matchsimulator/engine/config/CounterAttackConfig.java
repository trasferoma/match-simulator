package org.tasktelemetry.matchsimulator.engine.config;

import java.util.Objects;

public record CounterAttackConfig(double startProbability, DurationRange advanceDuration) {

    public CounterAttackConfig {
        Objects.requireNonNull(advanceDuration, "counter-attack advance duration must not be null");
        requireOpenProbability(startProbability);
    }

    public static CounterAttackConfig defaults() {
        double startProbability = 0.08;
        DurationRange advanceDuration = new DurationRange(2, 6);
        return new CounterAttackConfig(startProbability, advanceDuration);
    }

    private static void requireOpenProbability(double value) {
        boolean isOpenInterval = value > 0.0 && value < 1.0;
        if (!isOpenInterval) {
            throw new IllegalArgumentException("counter-attack start probability must be strictly between 0.0 and"
                    + " 1.0, was " + value);
        }
    }
}
