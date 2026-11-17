package org.tasktelemetry.matchsimulator.coach.reading;

import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;

public record PlayedTime(int seconds) {

    public PlayedTime {
        if (seconds < 0) {
            throw new IllegalArgumentException("played time must not be negative, was " + seconds);
        }
    }

    public static PlayedTime kickOff() {
        return new PlayedTime(0);
    }

    public static PlayedTime at(MatchTime time) {
        int secondsIntoHalf = time.seconds();
        if (time.period() == MatchPeriod.SECOND_HALF) {
            return new PlayedTime(MatchPeriod.REGULATION_SECONDS + secondsIntoHalf);
        }
        int secondsWithinRegulation = Math.min(secondsIntoHalf, MatchPeriod.REGULATION_SECONDS);
        return new PlayedTime(secondsWithinRegulation);
    }

    public int secondsSince(PlayedTime earlier) {
        return seconds - earlier.seconds;
    }
}
