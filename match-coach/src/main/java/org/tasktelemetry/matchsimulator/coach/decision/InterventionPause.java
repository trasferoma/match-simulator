package org.tasktelemetry.matchsimulator.coach.decision;

import org.tasktelemetry.matchsimulator.coach.config.TimingConfig;
import org.tasktelemetry.matchsimulator.coach.reading.LastIntervention;
import org.tasktelemetry.matchsimulator.coach.reading.PlayedTime;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;

public final class InterventionPause {

    private final TimingConfig timing;

    public InterventionPause(TimingConfig timing) {
        this.timing = timing;
    }

    public boolean isActive(LastIntervention last, MatchTime now) {
        int pauseMinutes = pauseMinutesAt(now);
        int pauseSeconds = pauseMinutes * MatchPeriod.SECONDS_PER_MINUTE;
        PlayedTime nowPlayed = PlayedTime.at(now);
        return last.isWithin(pauseSeconds, nowPlayed);
    }

    private int pauseMinutesAt(MatchTime now) {
        boolean isInFinalPhase = now.minute() >= timing.finalPhaseStartMinute();
        return isInFinalPhase ? timing.finalPhasePauseMinutes() : timing.pauseMinutes();
    }
}
