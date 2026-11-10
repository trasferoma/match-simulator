package org.tasktelemetry.matchsimulator.engine.config;

public record OffsideConfig(double baseProbability, double positioningSlope) {

    public OffsideConfig {
        requireOpenProbability(baseProbability);
        requireNotNegativeAndFinite(positioningSlope);
    }

    public static OffsideConfig defaults() {
        double baseProbability = 0.07;
        double positioningSlope = 0.03;
        return new OffsideConfig(baseProbability, positioningSlope);
    }

    private static void requireOpenProbability(double value) {
        boolean isOpenInterval = value > 0.0 && value < 1.0;
        if (!isOpenInterval) {
            throw new IllegalArgumentException("base offside probability must be strictly between 0.0 and 1.0, was "
                    + value);
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
