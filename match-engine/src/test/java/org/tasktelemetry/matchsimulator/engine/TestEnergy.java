package org.tasktelemetry.matchsimulator.engine;

import java.util.LinkedHashMap;
import java.util.Map;

import org.tasktelemetry.matchsimulator.engine.state.TeamEnergy;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class TestEnergy {

    private TestEnergy() {
    }

    public static TeamOnPitch withPlayerOnPitch(TeamOnPitch team, Player player) {
        Map<Player, Double> energy = new LinkedHashMap<>(team.energy().energyByPlayer());
        energy.putIfAbsent(player, TeamEnergy.FULL);
        return team.withEnergy(new TeamEnergy(energy));
    }

    public static TeamOnPitch at(TeamOnPitch team, double energy, Player... players) {
        TeamEnergy updated = team.energy();
        for (Player player : players) {
            updated = updated.withEnergy(player, energy);
        }
        return team.withEnergy(updated);
    }

    public static TeamOnPitch allAt(TeamOnPitch team, double energy) {
        return at(team, energy, team.players().toArray(new Player[0]));
    }
}
