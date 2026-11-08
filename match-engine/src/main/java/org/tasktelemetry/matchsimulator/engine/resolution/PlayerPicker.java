package org.tasktelemetry.matchsimulator.engine.resolution;

import java.util.List;

import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class PlayerPicker {

    private final MatchRandom random;

    public PlayerPicker(MatchRandom random) {
        this.random = random;
    }

    public Player pickFrom(TeamOnPitch team, ZoneRole zoneRole) {
        Role sector = sectorOf(zoneRole);
        List<Player> candidates = team.playersIn(sector);
        return pickWeighted(candidates);
    }

    private Player pickWeighted(List<Player> candidates) {
        double totalWeight = totalWeightOf(candidates);
        double target = random.nextDouble() * totalWeight;
        double cumulativeWeight = 0.0;
        for (Player candidate : candidates) {
            cumulativeWeight += weightOf(candidate);
            if (target < cumulativeWeight) {
                return candidate;
            }
        }
        return candidates.getLast();
    }

    private double totalWeightOf(List<Player> candidates) {
        return candidates.stream()
                .mapToDouble(this::weightOf)
                .sum();
    }

    private double weightOf(Player player) {
        return player.attributes().movementAverage();
    }

    private static Role sectorOf(ZoneRole zoneRole) {
        return switch (zoneRole) {
            case OWN_BOX, DEFENCE -> Role.DEFENDER;
            case MIDFIELD -> Role.MIDFIELDER;
            case ATTACKING_THIRD, OPPONENT_BOX -> Role.FORWARD;
        };
    }
}
