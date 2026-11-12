package org.tasktelemetry.matchsimulator.engine.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public record ZonePresenceConfig(
        Map<ZoneRole, Map<Role, Double>> presenceByZoneRole,
        Map<Role, Integer> referenceFormation,
        double advantageFactorPerMan,
        double advantageCap,
        double representativeWeight,
        double passMinimumPlayers) {

    private static final Set<Role> MOVEMENT_ROLES = EnumSet.complementOf(EnumSet.of(Role.GOALKEEPER));
    private static final int GOALKEEPERS_ON_PITCH = 1;
    private static final int REFERENCE_MOVEMENT_PLAYERS = Team.STARTERS_COUNT - GOALKEEPERS_ON_PITCH;

    public ZonePresenceConfig {
        presenceByZoneRole = presenceForEveryZoneRole(presenceByZoneRole);
        referenceFormation = referenceWithEveryMovementRole(referenceFormation);
        requireAtLeastOne("advantage factor per man", advantageFactorPerMan);
        requirePositiveAndFinite("advantage cap", advantageCap);
        requireProbability("representative weight", representativeWeight);
        requireNotNegativeAndFinite("pass minimum players", passMinimumPlayers);
    }

    public static ZonePresenceConfig defaults() {
        Map<ZoneRole, Map<Role, Double>> presenceByZoneRole = new EnumMap<>(ZoneRole.class);
        Map<Role, Double> ownBoxPresence = presenceRow(1.0, 0.3, 0.0);
        Map<Role, Double> defencePresence = presenceRow(0.9, 0.5, 0.1);
        Map<Role, Double> midfieldPresence = presenceRow(0.3, 1.0, 0.4);
        Map<Role, Double> attackingThirdPresence = presenceRow(0.1, 0.5, 0.9);
        Map<Role, Double> opponentBoxPresence = presenceRow(0.05, 0.3, 1.0);
        presenceByZoneRole.put(ZoneRole.OWN_BOX, ownBoxPresence);
        presenceByZoneRole.put(ZoneRole.DEFENCE, defencePresence);
        presenceByZoneRole.put(ZoneRole.MIDFIELD, midfieldPresence);
        presenceByZoneRole.put(ZoneRole.ATTACKING_THIRD, attackingThirdPresence);
        presenceByZoneRole.put(ZoneRole.OPPONENT_BOX, opponentBoxPresence);
        Map<Role, Integer> referenceFormation = new EnumMap<>(Role.class);
        referenceFormation.put(Role.DEFENDER, 4);
        referenceFormation.put(Role.MIDFIELDER, 4);
        referenceFormation.put(Role.FORWARD, 2);
        double advantageFactorPerMan = 1.25;
        double advantageCap = 4.0;
        double representativeWeight = 0.5;
        double passMinimumPlayers = 1.0;
        return new ZonePresenceConfig(presenceByZoneRole, referenceFormation, advantageFactorPerMan, advantageCap,
                representativeWeight, passMinimumPlayers);
    }

    private static Map<Role, Double> presenceRow(double defenders, double midfielders, double forwards) {
        Map<Role, Double> row = new EnumMap<>(Role.class);
        row.put(Role.DEFENDER, defenders);
        row.put(Role.MIDFIELDER, midfielders);
        row.put(Role.FORWARD, forwards);
        return row;
    }

    private static Map<ZoneRole, Map<Role, Double>> presenceForEveryZoneRole(
            Map<ZoneRole, Map<Role, Double>> presence) {
        if (presence == null) {
            throw new NullPointerException("presence by zone role must not be null");
        }
        Map<ZoneRole, Map<Role, Double>> copy = new EnumMap<>(ZoneRole.class);
        for (ZoneRole zoneRole : ZoneRole.values()) {
            Map<Role, Double> row = presence.get(zoneRole);
            if (row == null) {
                throw new IllegalArgumentException("presence by zone role needs a row for " + zoneRole);
            }
            Map<Role, Double> checkedRow = presenceForEveryMovementRole(zoneRole, row);
            copy.put(zoneRole, checkedRow);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<Role, Double> presenceForEveryMovementRole(ZoneRole zoneRole, Map<Role, Double> row) {
        Map<Role, Double> copy = new EnumMap<>(Role.class);
        copy.putAll(row);
        requireOnlyMovementRoles("presence for " + zoneRole, copy.keySet());
        boolean hasPositivePresence = false;
        for (Role role : MOVEMENT_ROLES) {
            Double value = copy.get(role);
            if (value == null) {
                throw new IllegalArgumentException("presence for " + zoneRole + " needs a value for " + role);
            }
            requireProbability("presence for " + zoneRole + " of " + role, value);
            hasPositivePresence = hasPositivePresence || value > 0.0;
        }
        if (!hasPositivePresence) {
            throw new IllegalArgumentException("presence for " + zoneRole + " needs at least one positive value");
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<Role, Integer> referenceWithEveryMovementRole(Map<Role, Integer> reference) {
        if (reference == null) {
            throw new NullPointerException("reference formation must not be null");
        }
        Map<Role, Integer> copy = new EnumMap<>(Role.class);
        copy.putAll(reference);
        requireOnlyMovementRoles("reference formation", copy.keySet());
        int total = 0;
        for (Role role : MOVEMENT_ROLES) {
            Integer players = copy.get(role);
            if (players == null || players < 1) {
                throw new IllegalArgumentException("reference formation needs at least one player for " + role
                        + ", was " + players);
            }
            total += players;
        }
        if (total != REFERENCE_MOVEMENT_PLAYERS) {
            throw new IllegalArgumentException("reference formation must have exactly " + REFERENCE_MOVEMENT_PLAYERS
                    + " movement players, was " + total);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static void requireOnlyMovementRoles(String name, Set<Role> roles) {
        if (!MOVEMENT_ROLES.containsAll(roles)) {
            throw new IllegalArgumentException(name + " must not include the goalkeeper");
        }
    }

    private static void requireProbability(String name, double value) {
        boolean isProbability = value >= 0.0 && value <= 1.0;
        if (!isProbability) {
            throw new IllegalArgumentException(name + " must be between 0.0 and 1.0, was " + value);
        }
    }

    private static void requireAtLeastOne(String name, double value) {
        boolean isAtLeastOneAndFinite = value >= 1.0 && !Double.isInfinite(value);
        if (!isAtLeastOneAndFinite) {
            throw new IllegalArgumentException(name + " must be at least 1.0 and finite, was " + value);
        }
    }

    private static void requirePositiveAndFinite(String name, double value) {
        boolean isPositiveAndFinite = value > 0.0 && !Double.isInfinite(value);
        if (!isPositiveAndFinite) {
            throw new IllegalArgumentException(name + " must be positive and finite, was " + value);
        }
    }

    private static void requireNotNegativeAndFinite(String name, double value) {
        boolean isNotNegativeAndFinite = value >= 0.0 && !Double.isInfinite(value);
        if (!isNotNegativeAndFinite) {
            throw new IllegalArgumentException(name + " must not be negative and must be finite, was " + value);
        }
    }
}
