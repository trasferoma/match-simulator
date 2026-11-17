package org.tasktelemetry.matchsimulator.calibration;

import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;

public record BenchConfig(
        int teamCount,
        int weakestStrength,
        int strongestStrength,
        int seasons,
        long baseSeed,
        int individualVariation,
        double possessionTolerance,
        int coachTacticalReading) {

    public BenchConfig {
        requireAtLeast("team count", teamCount, 2);
        requireInScale("weakest strength", weakestStrength);
        requireInScale("strongest strength", strongestStrength);
        requireWeakestBelowStrongest(weakestStrength, strongestStrength);
        requireAtLeast("seasons", seasons, 1);
        requireAtLeast("individual variation", individualVariation, 0);
        requireNotNegative(possessionTolerance);
        requireInScale("coach tactical reading", coachTacticalReading);
    }

    public static BenchConfig defaults() {
        int teamCount = 20;
        int weakestStrength = 55;
        int strongestStrength = 80;
        int seasons = 10;
        long baseSeed = 42L;
        int individualVariation = 3;
        double possessionTolerance = 3.0;
        int coachTacticalReading = 50;
        return new BenchConfig(teamCount, weakestStrength, strongestStrength, seasons, baseSeed,
                individualVariation, possessionTolerance, coachTacticalReading);
    }

    private static void requireAtLeast(String name, int value, int minimum) {
        if (value < minimum) {
            throw new IllegalArgumentException(name + " must be at least " + minimum + ", was " + value);
        }
    }

    private static void requireInScale(String name, int value) {
        boolean isInScale = value >= PlayerAttributes.MIN_VALUE && value <= PlayerAttributes.MAX_VALUE;
        if (!isInScale) {
            throw new IllegalArgumentException(name + " must be between " + PlayerAttributes.MIN_VALUE + " and "
                    + PlayerAttributes.MAX_VALUE + ", was " + value);
        }
    }

    private static void requireWeakestBelowStrongest(int weakest, int strongest) {
        if (weakest >= strongest) {
            throw new IllegalArgumentException("weakest strength " + weakest + " must be below the strongest "
                    + strongest);
        }
    }

    private static void requireNotNegative(double tolerance) {
        boolean isNotNegativeAndFinite = tolerance >= 0.0 && !Double.isInfinite(tolerance);
        if (!isNotNegativeAndFinite) {
            throw new IllegalArgumentException("possession tolerance must not be negative and must be finite, was "
                    + tolerance);
        }
    }
}
