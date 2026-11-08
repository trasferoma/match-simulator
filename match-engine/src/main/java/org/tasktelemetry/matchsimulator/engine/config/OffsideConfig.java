package org.tasktelemetry.matchsimulator.engine.config;

import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;

public record OffsideConfig(double baseProbability, int neutralPositioning, double positioningSlope) {

    public OffsideConfig {
        requireOpenProbability(baseProbability);
        requireInScale(neutralPositioning);
        requireNotNegativeAndFinite(positioningSlope);
    }

    public static OffsideConfig defaults() {
        double baseProbability = 0.07;
        int neutralPositioning = 70;
        double positioningSlope = 0.03;
        return new OffsideConfig(baseProbability, neutralPositioning, positioningSlope);
    }

    private static void requireOpenProbability(double value) {
        boolean isOpenInterval = value > 0.0 && value < 1.0;
        if (!isOpenInterval) {
            throw new IllegalArgumentException("base offside probability must be strictly between 0.0 and 1.0, was "
                    + value);
        }
    }

    private static void requireInScale(int rating) {
        boolean isInScale = rating >= PlayerAttributes.MIN_VALUE && rating <= PlayerAttributes.MAX_VALUE;
        if (!isInScale) {
            throw new IllegalArgumentException("neutral positioning must be between " + PlayerAttributes.MIN_VALUE
                    + " and " + PlayerAttributes.MAX_VALUE + ", was " + rating);
        }
    }

    private static void requireNotNegativeAndFinite(double value) {
        boolean isNotNegativeAndFinite = value >= 0.0 && !Double.isInfinite(value);
        if (!isNotNegativeAndFinite) {
            throw new IllegalArgumentException("positioning slope must not be negative and must be finite, was "
                    + value);
        }
    }
}
