package org.tasktelemetry.matchsimulator.coach.config;

public record TimingConfig(
        int checkIntervalMinutes,
        int finalPhaseStartMinute,
        int pauseMinutes,
        int finalPhasePauseMinutes,
        int styleReversalHoldMinutes) {

    public TimingConfig {
        ConfigGuards.requireMinutesInMatch("check interval", checkIntervalMinutes);
        ConfigGuards.requireMinutesInMatch("final phase start minute", finalPhaseStartMinute);
        ConfigGuards.requireMinutesInMatch("pause", pauseMinutes);
        ConfigGuards.requireMinutesInMatch("final phase pause", finalPhasePauseMinutes);
        ConfigGuards.requireMinutesInMatch("style reversal hold", styleReversalHoldMinutes);
        requireFinalPauseNotLongerThanPause(pauseMinutes, finalPhasePauseMinutes);
    }

    public static TimingConfig defaults() {
        int checkIntervalMinutes = 1;
        int finalPhaseStartMinute = 75;
        int pauseMinutes = 10;
        int finalPhasePauseMinutes = 3;
        int styleReversalHoldMinutes = 15;
        return new TimingConfig(checkIntervalMinutes, finalPhaseStartMinute, pauseMinutes, finalPhasePauseMinutes,
                styleReversalHoldMinutes);
    }

    private static void requireFinalPauseNotLongerThanPause(int pause, int finalPhasePause) {
        if (finalPhasePause > pause) {
            throw new IllegalArgumentException("final phase pause " + finalPhasePause
                    + " must not exceed the pause " + pause);
        }
    }
}
