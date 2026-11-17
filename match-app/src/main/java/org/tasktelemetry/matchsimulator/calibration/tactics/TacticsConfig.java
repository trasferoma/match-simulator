package org.tasktelemetry.matchsimulator.calibration.tactics;

import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;

public record TacticsConfig(int samples, int strength, long baseSeed) {

    public TacticsConfig {
        requireAtLeastTwoSamples(samples);
        requireInScale(strength);
    }

    public static TacticsConfig defaults() {
        int samples = 2000;
        int strength = 68;
        long baseSeed = 42L;
        return new TacticsConfig(samples, strength, baseSeed);
    }

    private static void requireAtLeastTwoSamples(int samples) {
        if (samples < 2) {
            throw new IllegalArgumentException("samples must be at least 2, was " + samples);
        }
    }

    private static void requireInScale(int strength) {
        boolean isInScale = strength >= PlayerAttributes.MIN_VALUE && strength <= PlayerAttributes.MAX_VALUE;
        if (!isInScale) {
            throw new IllegalArgumentException("strength must be between " + PlayerAttributes.MIN_VALUE + " and "
                    + PlayerAttributes.MAX_VALUE + ", was " + strength);
        }
    }
}
