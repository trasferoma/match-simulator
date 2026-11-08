package org.tasktelemetry.matchsimulator.engine.pitch;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

import org.tasktelemetry.matchsimulator.engine.MatchSide;

public final class PitchLayout {

    private static final int DEFAULT_ZONE_COUNT = 5;

    private final List<Zone> zones;

    public PitchLayout(int zoneCount) {
        requireOneZonePerRole(zoneCount);
        this.zones = IntStream.range(0, zoneCount)
                .mapToObj(Zone::new)
                .toList();
    }

    public static PitchLayout standard() {
        return new PitchLayout(DEFAULT_ZONE_COUNT);
    }

    public List<Zone> zones() {
        return zones;
    }

    public Zone kickOffZone() {
        int middle = zones.size() / 2;
        return zones.get(middle);
    }

    public Zone ownBoxZone(MatchSide possession) {
        return zoneAtAttackPosition(0, possession);
    }

    public Zone defenceZone(MatchSide possession) {
        return zoneAtAttackPosition(1, possession);
    }

    public ZoneRole roleOf(Zone zone, MatchSide possession) {
        int position = attackPosition(zone, possession);
        return roleAt(position);
    }

    public Optional<Zone> advance(Zone zone, MatchSide possession) {
        return neighbour(zone, possession, 1);
    }

    public Optional<Zone> retreat(Zone zone, MatchSide possession) {
        return neighbour(zone, possession, -1);
    }

    private ZoneRole roleAt(int position) {
        if (position == 0) {
            return ZoneRole.OWN_BOX;
        }
        if (position == lastPosition()) {
            return ZoneRole.OPPONENT_BOX;
        }
        if (position == lastPosition() - 1) {
            return ZoneRole.ATTACKING_THIRD;
        }
        if (position == 1) {
            return ZoneRole.DEFENCE;
        }
        return ZoneRole.MIDFIELD;
    }

    private Optional<Zone> neighbour(Zone zone, MatchSide possession, int step) {
        int target = attackPosition(zone, possession) + step;
        if (target < 0 || target > lastPosition()) {
            return Optional.empty();
        }
        Zone neighbour = zoneAtAttackPosition(target, possession);
        return Optional.of(neighbour);
    }

    private int attackPosition(Zone zone, MatchSide possession) {
        requireInLayout(zone);
        return switch (possession) {
            case HOME -> zone.index();
            case AWAY -> lastPosition() - zone.index();
        };
    }

    private Zone zoneAtAttackPosition(int position, MatchSide possession) {
        int index = switch (possession) {
            case HOME -> position;
            case AWAY -> lastPosition() - position;
        };
        return zones.get(index);
    }

    private int lastPosition() {
        return zones.size() - 1;
    }

    private void requireInLayout(Zone zone) {
        Objects.requireNonNull(zone, "zone must not be null");
        if (!zones.contains(zone)) {
            throw new IllegalArgumentException("zone " + zone + " does not belong to this layout of "
                    + zones.size() + " zones");
        }
    }

    private static void requireOneZonePerRole(int zoneCount) {
        int minimumZones = ZoneRole.values().length;
        if (zoneCount < minimumZones) {
            throw new IllegalArgumentException("a pitch needs at least " + minimumZones
                    + " zones, one for each zone role, was " + zoneCount);
        }
    }
}
