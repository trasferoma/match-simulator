package org.tasktelemetry.matchsimulator.coach.reading;

import org.tasktelemetry.matchsimulator.coach.config.TacticalReadingConfig;

public final class ConfirmationDelay {

    private final TacticalReadingConfig config;

    public ConfirmationDelay(TacticalReadingConfig config) {
        this.config = config;
    }

    public double minutesFor(int tacticalReading) {
        double neutral = config.neutralReading();
        double gapFromNeutral = neutral - tacticalReading;
        double relativeGap = gapFromNeutral / neutral;
        double stretch = 1 + config.sensitivity() * relativeGap;
        return config.baseDelayMinutes() * stretch;
    }
}
