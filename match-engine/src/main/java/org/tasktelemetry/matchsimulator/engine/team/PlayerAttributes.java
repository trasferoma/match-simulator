package org.tasktelemetry.matchsimulator.engine.team;

import java.util.stream.IntStream;

public record PlayerAttributes(
        int passing,
        int dribbling,
        int shooting,
        int tackling,
        int positioning,
        int pace,
        int aerial,
        int goalkeeping) {

    public static final int MIN_VALUE = 1;
    public static final int MAX_VALUE = 100;

    public PlayerAttributes {
        requireInScale("passing", passing);
        requireInScale("dribbling", dribbling);
        requireInScale("shooting", shooting);
        requireInScale("tackling", tackling);
        requireInScale("positioning", positioning);
        requireInScale("pace", pace);
        requireInScale("aerial", aerial);
        requireInScale("goalkeeping", goalkeeping);
    }

    public double movementAverage() {
        return IntStream.of(passing, dribbling, shooting, tackling, positioning, pace, aerial)
                .average()
                .orElseThrow();
    }

    private static void requireInScale(String attribute, int value) {
        if (value < MIN_VALUE || value > MAX_VALUE) {
            throw new IllegalArgumentException("attribute " + attribute + " must be between " + MIN_VALUE
                    + " and " + MAX_VALUE + ", was " + value);
        }
    }
}
