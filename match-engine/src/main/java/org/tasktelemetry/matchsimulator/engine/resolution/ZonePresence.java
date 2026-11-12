package org.tasktelemetry.matchsimulator.engine.resolution;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;

import org.tasktelemetry.matchsimulator.engine.config.ZonePresenceConfig;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class ZonePresence {

    private final ZonePresenceConfig config;

    public ZonePresence(ZonePresenceConfig config) {
        Objects.requireNonNull(config, "zone presence configuration must not be null");
        this.config = config;
    }

    public double presenceOf(TeamOnPitch team, Player player, ZoneRole zoneRole) {
        Role currentRole = team.roleOf(player);
        if (currentRole == Role.GOALKEEPER) {
            return 0.0;
        }
        Map<Role, Double> row = config.presenceByZoneRole().get(zoneRole);
        return row.get(currentRole);
    }

    public double representativeWeight() {
        return config.representativeWeight();
    }

    public List<Player> playersPresentIn(TeamOnPitch team, ZoneRole zoneRole) {
        return team.players().stream()
                .filter(player -> presenceOf(team, player, zoneRole) > 0.0)
                .toList();
    }

    public double menInZone(TeamOnPitch team, ZoneRole zoneRole) {
        return menAmong(team, team.players(), zoneRole);
    }

    public double menInZoneWithout(TeamOnPitch team, ZoneRole zoneRole, Player excluded) {
        List<Player> others = team.players().stream()
                .filter(player -> !player.equals(excluded))
                .toList();
        return menAmong(team, others, zoneRole);
    }

    public double advantage(TeamOnPitch attackers, ZoneRole attackingRole, TeamOnPitch defenders,
            ZoneRole defendingRole) {
        double attackersEdge = edgeOverReference(attackers, attackingRole);
        double defendersEdge = edgeOverReference(defenders, defendingRole);
        double rawAdvantage = attackersEdge - defendersEdge;
        return limitToCap(rawAdvantage);
    }

    public double superiorityFactor(TeamOnPitch attackers, ZoneRole attackingRole, TeamOnPitch defenders,
            ZoneRole defendingRole) {
        double advantage = advantage(attackers, attackingRole, defenders, defendingRole);
        return StrictMath.pow(config.advantageFactorPerMan(), advantage);
    }

    public double averageAttribute(TeamOnPitch team, ZoneRole zoneRole, ToIntFunction<PlayerAttributes> attribute) {
        double weightedSum = 0.0;
        double totalPresence = 0.0;
        for (Player player : team.players()) {
            double presence = presenceOf(team, player, zoneRole);
            weightedSum += presence * attribute.applyAsInt(player.attributes());
            totalPresence += presence;
        }
        if (totalPresence == 0.0) {
            throw new IllegalStateException("no player of " + team.team().name() + " has presence in " + zoneRole);
        }
        return weightedSum / totalPresence;
    }

    private double edgeOverReference(TeamOnPitch team, ZoneRole zoneRole) {
        double men = menInZone(team, zoneRole);
        double reference = weightedMen(config.referenceFormation(), zoneRole);
        return men - reference;
    }

    private double menAmong(TeamOnPitch team, List<Player> players, ZoneRole zoneRole) {
        Map<Role, Integer> countByRole = countMovementPlayersByRole(team, players);
        return weightedMen(countByRole, zoneRole);
    }

    private Map<Role, Integer> countMovementPlayersByRole(TeamOnPitch team, List<Player> players) {
        Map<Role, Integer> countByRole = new EnumMap<>(Role.class);
        for (Player player : players) {
            Role currentRole = team.roleOf(player);
            if (currentRole != Role.GOALKEEPER) {
                countByRole.merge(currentRole, 1, Integer::sum);
            }
        }
        return countByRole;
    }

    private double weightedMen(Map<Role, Integer> countByRole, ZoneRole zoneRole) {
        Map<Role, Double> row = config.presenceByZoneRole().get(zoneRole);
        double men = 0.0;
        for (Map.Entry<Role, Double> presence : row.entrySet()) {
            int players = countByRole.getOrDefault(presence.getKey(), 0);
            men += players * presence.getValue();
        }
        return men;
    }

    private double limitToCap(double rawAdvantage) {
        double cap = config.advantageCap();
        double belowCeiling = Math.min(cap, rawAdvantage);
        return Math.max(-cap, belowCeiling);
    }
}
