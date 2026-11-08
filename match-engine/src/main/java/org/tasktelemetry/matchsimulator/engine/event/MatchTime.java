package org.tasktelemetry.matchsimulator.engine.event;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;

public record MatchTime(MatchPeriod period, int seconds) {

    public MatchTime {
        Objects.requireNonNull(period, "period must not be null");
        if (!period.isPlayed()) {
            throw new IllegalArgumentException("a match time belongs to a half, but the period is " + period);
        }
        if (seconds < 0) {
            throw new IllegalArgumentException("seconds into the half must not be negative, was " + seconds);
        }
    }

    public int minute() {
        return minutesBeforeThisHalf() + minutesIntoThisHalf();
    }

    public int second() {
        return seconds % MatchPeriod.SECONDS_PER_MINUTE;
    }

    public String display() {
        int intoHalf = minutesIntoThisHalf();
        if (intoHalf <= MatchPeriod.MINUTES_PER_HALF) {
            return minute() + "'";
        }
        int regulationEnd = minutesBeforeThisHalf() + MatchPeriod.MINUTES_PER_HALF;
        int stoppageMinute = intoHalf - MatchPeriod.MINUTES_PER_HALF;
        return regulationEnd + "+" + stoppageMinute + "'";
    }

    private int minutesIntoThisHalf() {
        return seconds / MatchPeriod.SECONDS_PER_MINUTE;
    }

    private int minutesBeforeThisHalf() {
        if (period == MatchPeriod.SECOND_HALF) {
            return MatchPeriod.MINUTES_PER_HALF;
        }
        return 0;
    }
}
