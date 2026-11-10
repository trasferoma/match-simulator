package org.tasktelemetry.matchsimulator.engine.resolution;

import java.util.List;
import java.util.function.ToDoubleFunction;

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

    public Player pickOtherThan(TeamOnPitch team, ZoneRole zoneRole, Player excluded) {
        Role sector = sectorOf(zoneRole);
        List<Player> candidates = team.playersIn(sector).stream()
                .filter(player -> !player.equals(excluded))
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("no " + sector + " of " + team.team().name() + " other than "
                    + excluded.name());
        }
        return pickWeighted(candidates);
    }

    public Player pickByPassing(List<Player> candidates) {
        return pickWeighted(candidates, this::passingOf);
    }

    private Player pickWeighted(List<Player> candidates) {
        return pickWeighted(candidates, this::movementAverageOf);
    }

    private Player pickWeighted(List<Player> candidates, ToDoubleFunction<Player> weightOf) {
        double totalWeight = totalWeightOf(candidates, weightOf);
        double target = random.nextDouble() * totalWeight;
        double cumulativeWeight = 0.0;
        for (Player candidate : candidates) {
            cumulativeWeight += weightOf.applyAsDouble(candidate);
            if (target < cumulativeWeight) {
                return candidate;
            }
        }
        return candidates.getLast();
    }

    private double totalWeightOf(List<Player> candidates, ToDoubleFunction<Player> weightOf) {
        return candidates.stream()
                .mapToDouble(weightOf)
                .sum();
    }

    private double movementAverageOf(Player player) {
        return player.attributes().movementAverage();
    }

    private double passingOf(Player player) {
        return player.attributes().passing();
    }

    public static Role sectorOf(ZoneRole zoneRole) {
        return switch (zoneRole) {
            case OWN_BOX, DEFENCE -> Role.DEFENDER;
            case MIDFIELD -> Role.MIDFIELDER;
            case ATTACKING_THIRD, OPPONENT_BOX -> Role.FORWARD;
        };
    }
}
