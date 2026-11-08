package org.tasktelemetry.matchsimulator.engine.state;

import java.util.Objects;

public record MatchClock(MatchPeriod period, int seconds, int announcedStoppageMinutes) {

    public MatchClock {
        Objects.requireNonNull(period, "clock period must not be null");
        requireNotNegative("clock seconds", seconds);
        requireNotNegative("announced stoppage minutes", announcedStoppageMinutes);
    }

    public static MatchClock beforeKickOff() {
        return new MatchClock(MatchPeriod.BEFORE_KICK_OFF, 0, 0);
    }

    public MatchClock inPeriod(MatchPeriod next) {
        return new MatchClock(next, 0, 0);
    }

    public MatchClock advancedBy(int elapsedSeconds) {
        requirePlayed();
        requireNotNegative("elapsed seconds", elapsedSeconds);
        return new MatchClock(period, seconds + elapsedSeconds, announcedStoppageMinutes);
    }

    public MatchClock withAnnouncedStoppage(int minutes) {
        requirePlayed();
        if (minutes < 1) {
            throw new IllegalArgumentException("announced stoppage must be at least 1 minute, was " + minutes);
        }
        return new MatchClock(period, seconds, minutes);
    }

    public boolean reachedRegulationTime() {
        return seconds >= MatchPeriod.REGULATION_SECONDS;
    }

    public boolean isStoppageAnnounced() {
        return announcedStoppageMinutes > 0;
    }

    public boolean hasExpired() {
        int stoppageSeconds = announcedStoppageMinutes * MatchPeriod.SECONDS_PER_MINUTE;
        return isStoppageAnnounced() && seconds > MatchPeriod.REGULATION_SECONDS + stoppageSeconds;
    }

    private void requirePlayed() {
        if (!period.isPlayed()) {
            throw new IllegalStateException("the clock runs only in a half, but the period is " + period);
        }
    }

    private static void requireNotNegative(String name, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative, was " + value);
        }
    }
}
