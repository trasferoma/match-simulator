package org.tasktelemetry.matchsimulator.engine.resolution.fatigue;

import org.tasktelemetry.matchsimulator.engine.config.FatigueConfig;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class FatiguePenalty {

    private static final double NEUTRAL = 1.0;

    private final FatigueConfig config;

    public FatiguePenalty(FatigueConfig config) {
        this.config = config;
    }

    public double factorOf(TeamOnPitch team, Player player) {
        double energy = team.energy().energyOf(player);
        return factorAt(energy);
    }

    private double factorAt(double energy) {
        double threshold = config.performanceThreshold();
        if (energy >= threshold) {
            return NEUTRAL;
        }
        double minimum = config.minimumFactor();
        return minimum + (NEUTRAL - minimum) * energy / threshold;
    }
}
