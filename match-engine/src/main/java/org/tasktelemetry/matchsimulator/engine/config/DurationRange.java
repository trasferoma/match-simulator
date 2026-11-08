package org.tasktelemetry.matchsimulator.engine.config;

public record DurationRange(int minSeconds, int maxSeconds) {

    public DurationRange {
        if (minSeconds < 1) {
            throw new IllegalArgumentException("duration minimum must be at least 1 second, was " + minSeconds);
        }
        if (maxSeconds < minSeconds) {
            throw new IllegalArgumentException("duration maximum must not be below the minimum " + minSeconds
                    + ", was " + maxSeconds);
        }
    }

    public boolean contains(int seconds) {
        return seconds >= minSeconds && seconds <= maxSeconds;
    }
}
