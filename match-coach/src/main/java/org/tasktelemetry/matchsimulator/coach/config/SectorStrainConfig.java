package org.tasktelemetry.matchsimulator.coach.config;

import java.util.Objects;

public record SectorStrainConfig(
        int windowMinutes,
        int minimumDuels,
        SectorThresholds defence,
        SectorThresholds midfield,
        SectorThresholds attack) {

    public SectorStrainConfig {
        ConfigGuards.requireMinutesInMatch("sector window", windowMinutes);
        ConfigGuards.requireAtLeastOne("minimum duels", minimumDuels);
        Objects.requireNonNull(defence, "defence thresholds must not be null");
        Objects.requireNonNull(midfield, "midfield thresholds must not be null");
        Objects.requireNonNull(attack, "attack thresholds must not be null");
    }

    public static SectorStrainConfig defaults() {
        int windowMinutes = 30;
        int minimumDuels = 8;
        SectorThresholds defence = new SectorThresholds(0.60, 0.75);
        SectorThresholds midfield = new SectorThresholds(0.75, 0.85);
        SectorThresholds attack = new SectorThresholds(0.85, 0.90);
        return new SectorStrainConfig(windowMinutes, minimumDuels, defence, midfield, attack);
    }
}
