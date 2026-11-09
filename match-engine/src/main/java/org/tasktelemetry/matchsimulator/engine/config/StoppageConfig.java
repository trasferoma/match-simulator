package org.tasktelemetry.matchsimulator.engine.config;

public record StoppageConfig(int minimumMinutes, int secondsPerEvent, int maximumFirstHalfMinutes,
        int maximumSecondHalfMinutes) {

    public StoppageConfig {
        requireAtLeastOneMinute(minimumMinutes);
        requireAtLeast("seconds per stoppage event", secondsPerEvent, 1);
        requireAtLeast("maximum first-half stoppage minutes", maximumFirstHalfMinutes, minimumMinutes);
        requireAtLeast("maximum second-half stoppage minutes", maximumSecondHalfMinutes, minimumMinutes);
    }

    public static StoppageConfig defaults() {
        int minimumMinutes = 1;
        int secondsPerEvent = 30;
        int maximumFirstHalfMinutes = 5;
        int maximumSecondHalfMinutes = 8;
        return new StoppageConfig(minimumMinutes, secondsPerEvent, maximumFirstHalfMinutes,
                maximumSecondHalfMinutes);
    }

    private static void requireAtLeastOneMinute(int minimumMinutes) {
        if (minimumMinutes < 1) {
            throw new IllegalArgumentException("minimum stoppage must be at least 1 minute, was " + minimumMinutes);
        }
    }

    private static void requireAtLeast(String name, int value, int minimum) {
        if (value < minimum) {
            throw new IllegalArgumentException(name + " must be at least " + minimum + ", was " + value);
        }
    }
}
