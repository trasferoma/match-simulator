package org.tasktelemetry.matchsimulator.coach.config;

public record TacticalReadingConfig(int neutralReading, int baseDelayMinutes, double sensitivity) {

    public TacticalReadingConfig {
        ConfigGuards.requireInScale("neutral reading", neutralReading);
        ConfigGuards.requireMinutesInMatch("base delay", baseDelayMinutes);
        ConfigGuards.requirePositiveAndFinite("reading sensitivity", sensitivity);
    }

    public static TacticalReadingConfig defaults() {
        int neutralReading = 50;
        int baseDelayMinutes = 4;
        double sensitivity = 0.5;
        return new TacticalReadingConfig(neutralReading, baseDelayMinutes, sensitivity);
    }
}
