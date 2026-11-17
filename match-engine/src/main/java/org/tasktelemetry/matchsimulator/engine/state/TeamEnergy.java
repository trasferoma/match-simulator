package org.tasktelemetry.matchsimulator.engine.state;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.team.Player;

public record TeamEnergy(Map<Player, Double> energyByPlayer) {

    public static final double EMPTY = 0.0;
    public static final double FULL = 100;

    public TeamEnergy {
        Objects.requireNonNull(energyByPlayer, "energy by player must not be null");
        energyByPlayer.forEach(TeamEnergy::requireInRange);
        energyByPlayer = Collections.unmodifiableMap(new LinkedHashMap<>(energyByPlayer));
    }

    public static TeamEnergy fullFor(List<Player> players) {
        Map<Player, Double> energy = new LinkedHashMap<>();
        players.forEach(player -> energy.put(player, FULL));
        return new TeamEnergy(energy);
    }

    public double energyOf(Player player) {
        Double energy = energyByPlayer.get(player);
        if (energy == null) {
            throw new IllegalArgumentException(player.name() + " has no energy because is not on the pitch");
        }
        return energy;
    }

    public TeamEnergy withEnergy(Player player, double energy) {
        energyOf(player);
        Map<Player, Double> updated = new LinkedHashMap<>(energyByPlayer);
        updated.put(player, energy);
        return new TeamEnergy(updated);
    }

    public TeamEnergy withSubstitution(Player leaving, Player entering) {
        energyOf(leaving);
        Map<Player, Double> updated = new LinkedHashMap<>();
        energyByPlayer.forEach((player, energy) -> {
            if (player.equals(leaving)) {
                updated.put(entering, FULL);
            } else {
                updated.put(player, energy);
            }
        });
        return new TeamEnergy(updated);
    }

    public TeamEnergy without(Player leaving) {
        energyOf(leaving);
        Map<Player, Double> remaining = new LinkedHashMap<>(energyByPlayer);
        remaining.remove(leaving);
        return new TeamEnergy(remaining);
    }

    private static void requireInRange(Player player, Double energy) {
        boolean isInRange = energy != null && energy >= EMPTY && energy <= FULL;
        if (!isInRange) {
            throw new IllegalArgumentException("energy of " + player.name() + " must be between " + EMPTY + " and "
                    + FULL + ", was " + energy);
        }
    }
}
