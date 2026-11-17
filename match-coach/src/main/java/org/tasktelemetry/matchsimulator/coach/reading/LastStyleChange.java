package org.tasktelemetry.matchsimulator.coach.reading;

import java.util.Objects;

public record LastStyleChange(boolean made, PlayedTime at, int gradeShift) {

    private static final PlayedTime KICK_OFF = PlayedTime.kickOff();
    private static final LastStyleChange NEVER = new LastStyleChange(false, KICK_OFF, 0);

    public LastStyleChange {
        Objects.requireNonNull(at, "style change time must not be null");
    }

    public static LastStyleChange never() {
        return NEVER;
    }

    public static LastStyleChange at(PlayedTime time, int gradeShift) {
        return new LastStyleChange(true, time, gradeShift);
    }

    public boolean isReversedBy(int newGradeShift, int holdSeconds, PlayedTime now) {
        boolean isRecent = made && now.secondsSince(at) < holdSeconds;
        boolean isOppositeDirection = Integer.signum(gradeShift) * Integer.signum(newGradeShift) < 0;
        return isRecent && isOppositeDirection;
    }
}
