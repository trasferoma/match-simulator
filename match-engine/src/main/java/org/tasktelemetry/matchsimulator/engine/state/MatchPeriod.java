package org.tasktelemetry.matchsimulator.engine.state;

public enum MatchPeriod {
    BEFORE_KICK_OFF,
    FIRST_HALF,
    HALF_TIME,
    SECOND_HALF,
    FINISHED,
    SUSPENDED;

    public static final int MINUTES_PER_HALF = 45;
    public static final int SECONDS_PER_MINUTE = 60;
    public static final int REGULATION_SECONDS = MINUTES_PER_HALF * SECONDS_PER_MINUTE;

    public boolean isPlayed() {
        return this == FIRST_HALF || this == SECOND_HALF;
    }

    public boolean isOver() {
        return this == FINISHED || this == SUSPENDED;
    }
}
