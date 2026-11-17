package org.tasktelemetry.matchsimulator.coach.reading;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;

public record SituationDuration(boolean holding, PlayedTime since) {

    private static final PlayedTime KICK_OFF = PlayedTime.kickOff();
    private static final SituationDuration NOT_HOLDING = new SituationDuration(false, KICK_OFF);

    public SituationDuration {
        Objects.requireNonNull(since, "start of the situation must not be null");
    }

    public static SituationDuration notHolding() {
        return NOT_HOLDING;
    }

    public SituationDuration after(boolean holdsNow, PlayedTime now) {
        if (!holdsNow) {
            return NOT_HOLDING;
        }
        if (holding) {
            return this;
        }
        return new SituationDuration(true, now);
    }

    public boolean hasLastedAtLeast(double minutes, PlayedTime now) {
        if (!holding) {
            return false;
        }
        double requiredSeconds = minutes * MatchPeriod.SECONDS_PER_MINUTE;
        int lastedSeconds = now.secondsSince(since);
        return lastedSeconds >= requiredSeconds;
    }
}
