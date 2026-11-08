package org.tasktelemetry.matchsimulator.engine.config;

public record LineupConfig(int maximumSubstitutions, double inferiorityMultiplier) {

    public LineupConfig {
        requireAtLeastOneSubstitution(maximumSubstitutions);
        requireWeakeningMultiplier(inferiorityMultiplier);
    }

    public static LineupConfig defaults() {
        int maximumSubstitutions = 5;
        double inferiorityMultiplier = 0.90;
        return new LineupConfig(maximumSubstitutions, inferiorityMultiplier);
    }

    private static void requireAtLeastOneSubstitution(int maximumSubstitutions) {
        if (maximumSubstitutions < 1) {
            throw new IllegalArgumentException("maximum substitutions must be at least 1, was "
                    + maximumSubstitutions);
        }
    }

    private static void requireWeakeningMultiplier(double multiplier) {
        boolean isWeakening = multiplier > 0.0 && multiplier <= 1.0;
        if (!isWeakening) {
            throw new IllegalArgumentException("inferiority multiplier must be above 0.0 and at most 1.0, was "
                    + multiplier);
        }
    }
}
