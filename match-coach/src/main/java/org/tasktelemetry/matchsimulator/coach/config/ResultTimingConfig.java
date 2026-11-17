package org.tasktelemetry.matchsimulator.coach.config;

public record ResultTimingConfig(
        int riskBaseMinute,
        int riskMinutesPerGoalBehind,
        int protectLeadMinute,
        int riskInDrawMinute,
        int reserveHoldMinute) {

    public ResultTimingConfig {
        ConfigGuards.requireMinutesInMatch("risk base minute", riskBaseMinute);
        ConfigGuards.requireMinutesInMatch("risk minutes per goal behind", riskMinutesPerGoalBehind);
        ConfigGuards.requireMinutesInMatch("protect lead minute", protectLeadMinute);
        ConfigGuards.requireMinutesInMatch("risk in draw minute", riskInDrawMinute);
        ConfigGuards.requireMinutesInMatch("reserve hold minute", reserveHoldMinute);
    }

    public static ResultTimingConfig defaults() {
        int riskBaseMinute = 85;
        int riskMinutesPerGoalBehind = 5;
        int protectLeadMinute = 85;
        int riskInDrawMinute = 80;
        int reserveHoldMinute = 80;
        return new ResultTimingConfig(riskBaseMinute, riskMinutesPerGoalBehind, protectLeadMinute,
                riskInDrawMinute, reserveHoldMinute);
    }
}
