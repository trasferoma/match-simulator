package org.tasktelemetry.matchsimulator.engine.resolution.fatigue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.tasktelemetry.matchsimulator.engine.config.FatigueConfig;
import org.tasktelemetry.matchsimulator.engine.state.TeamEnergy;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class HalfTimeRecovery {

    private final FatigueConfig config;

    public HalfTimeRecovery(FatigueConfig config) {
        this.config = config;
    }

    public double recoveredEnergyOf(TeamOnPitch team, Player player) {
        double energy = team.energy().energyOf(player);
        double share = config.recoveryShareOf(player.attributes().stamina());
        double missingEnergy = TeamEnergy.FULL - energy;
        return energy + missingEnergy * share;
    }

    public FatigueUpdate recover(TeamOnPitch team) {
        Map<Player, Double> energyByPlayer = new LinkedHashMap<>();
        for (Player player : team.players()) {
            double energy = recoveredEnergyOf(team, player);
            energyByPlayer.put(player, energy);
        }
        TeamEnergy recovered = new TeamEnergy(energyByPlayer);
        TeamOnPitch rested = team.withEnergy(recovered);
        return FatigueUpdate.between(team, rested, config);
    }
}
