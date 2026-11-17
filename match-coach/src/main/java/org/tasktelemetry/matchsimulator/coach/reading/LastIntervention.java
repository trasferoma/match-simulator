package org.tasktelemetry.matchsimulator.coach.reading;

import java.util.Objects;

public record LastIntervention(boolean made, PlayedTime at) {

    private static final PlayedTime KICK_OFF = PlayedTime.kickOff();
    private static final LastIntervention NEVER = new LastIntervention(false, KICK_OFF);

    public LastIntervention {
        Objects.requireNonNull(at, "intervention time must not be null");
    }

    public static LastIntervention never() {
        return NEVER;
    }

    public static LastIntervention at(PlayedTime time) {
        return new LastIntervention(true, time);
    }

    public boolean isWithin(int seconds, PlayedTime now) {
        return made && now.secondsSince(at) < seconds;
    }
}
