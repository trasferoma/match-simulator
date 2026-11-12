package org.tasktelemetry.matchsimulator.engine.resolution;

import java.util.List;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class PlayerPicker {

    private final MatchRandom random;
    private final ZonePresence zonePresence;

    public PlayerPicker(MatchRandom random, ZonePresence zonePresence) {
        this.random = random;
        this.zonePresence = Objects.requireNonNull(zonePresence, "zone presence must not be null");
    }

    public Player pickFrom(TeamOnPitch team, ZoneRole zoneRole) {
        List<Player> candidates = zonePresence.playersPresentIn(team, zoneRole);
        if (candidates.isEmpty()) {
            throw new IllegalStateException("no player of " + team.team().name() + " has presence in " + zoneRole);
        }
        return pickByPresence(team, zoneRole, candidates);
    }

    public Player pickOtherThan(TeamOnPitch team, ZoneRole zoneRole, Player excluded) {
        List<Player> candidates = zonePresence.playersPresentIn(team, zoneRole).stream()
                .filter(player -> !player.equals(excluded))
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("no player of " + team.team().name() + " has presence in " + zoneRole
                    + " other than " + excluded.name());
        }
        return pickByPresence(team, zoneRole, candidates);
    }

    public Player pickByPassing(List<Player> candidates) {
        return pickWeighted(candidates, this::passingOf);
    }

    private Player pickByPresence(TeamOnPitch team, ZoneRole zoneRole, List<Player> candidates) {
        return pickWeighted(candidates, player -> zonePresence.presenceOf(team, player, zoneRole));
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

    private double passingOf(Player player) {
        return player.attributes().passing();
    }
}
