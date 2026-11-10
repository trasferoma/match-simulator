package org.tasktelemetry.matchsimulator.engine.resolution;

import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class OutOfPositionPenalty {

    private static final double NEUTRAL = 1.0;

    private final double multiplier;

    public OutOfPositionPenalty(double multiplier) {
        this.multiplier = multiplier;
    }

    public double factorOf(TeamOnPitch team, Player player) {
        return team.isOutOfPosition(player) ? multiplier : NEUTRAL;
    }
}
