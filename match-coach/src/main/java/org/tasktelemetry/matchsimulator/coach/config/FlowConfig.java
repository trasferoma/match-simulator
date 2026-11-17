package org.tasktelemetry.matchsimulator.coach.config;

public record FlowConfig(int windowMinutes, double dominatedIndex, double dominantIndex) {

    public FlowConfig {
        ConfigGuards.requireMinutesInMatch("flow window", windowMinutes);
        ConfigGuards.requireOpenProbability("dominated index", dominatedIndex);
        ConfigGuards.requireOpenProbability("dominant index", dominantIndex);
        requireDominatedBelowDominant(dominatedIndex, dominantIndex);
    }

    public static FlowConfig defaults() {
        int windowMinutes = 30;
        double dominatedIndex = 0.30;
        double dominantIndex = 0.70;
        return new FlowConfig(windowMinutes, dominatedIndex, dominantIndex);
    }

    private static void requireDominatedBelowDominant(double dominated, double dominant) {
        if (dominated >= dominant) {
            throw new IllegalArgumentException("dominated index " + dominated + " must be below the dominant index "
                    + dominant);
        }
    }
}
