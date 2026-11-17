package org.tasktelemetry.matchsimulator.coach.config;

public record SectorThresholds(double difficulty, double severeCrisis) {

    public SectorThresholds {
        ConfigGuards.requireOpenProbability("difficulty threshold", difficulty);
        ConfigGuards.requireOpenProbability("severe crisis threshold", severeCrisis);
        requireSevereCrisisNotBelowDifficulty(difficulty, severeCrisis);
    }

    private static void requireSevereCrisisNotBelowDifficulty(double difficulty, double severeCrisis) {
        if (severeCrisis < difficulty) {
            throw new IllegalArgumentException("severe crisis threshold " + severeCrisis
                    + " must not be below the difficulty threshold " + difficulty);
        }
    }
}
