package org.tasktelemetry.matchsimulator.coach.config;

public record FatigueResponseConfig(int tiredReplacementMinute, int freshAttackersWindowMinutes) {

    public FatigueResponseConfig {
        ConfigGuards.requireMinutesInMatch("tired replacement minute", tiredReplacementMinute);
        ConfigGuards.requireMinutesInMatch("fresh attackers window", freshAttackersWindowMinutes);
    }

    public static FatigueResponseConfig defaults() {
        int tiredReplacementMinute = 70;
        int freshAttackersWindowMinutes = 10;
        return new FatigueResponseConfig(tiredReplacementMinute, freshAttackersWindowMinutes);
    }
}
