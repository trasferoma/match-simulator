package org.tasktelemetry.matchsimulator.coach.reading;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public record LineupReading(Map<Player, Role> sectors, PlayStyle style, int initialSetup, int wantedSetup,
        LastIntervention lastIntervention, LastStyleChange lastStyleChange, BenchReading bench, Set<Player> cautioned,
        List<Player> rosterOrder) {

    public LineupReading {
        Objects.requireNonNull(sectors, "sectors must not be null");
        Objects.requireNonNull(style, "style must not be null");
        Objects.requireNonNull(lastIntervention, "last intervention must not be null");
        Objects.requireNonNull(lastStyleChange, "last style change must not be null");
        Objects.requireNonNull(bench, "bench must not be null");
        Objects.requireNonNull(cautioned, "cautioned players must not be null");
        Objects.requireNonNull(rosterOrder, "roster order must not be null");
        rosterOrder = List.copyOf(rosterOrder);
        Map<Player, Role> sectorsCopy = new LinkedHashMap<>(sectors);
        sectors = Collections.unmodifiableMap(sectorsCopy);
        Set<Player> cautionedCopy = new LinkedHashSet<>(cautioned);
        cautioned = Collections.unmodifiableSet(cautionedCopy);
    }

    public int playersOnPitch() {
        return sectors.size();
    }

    public Role sectorOf(Player player) {
        Role sector = sectors.get(player);
        if (sector == null) {
            throw new IllegalStateException("player " + player.name() + " is not on the pitch");
        }
        return sector;
    }

    public List<Player> playersIn(Role sector) {
        return sectors.entrySet().stream()
                .filter(entry -> entry.getValue() == sector)
                .map(Map.Entry::getKey)
                .toList();
    }

    public int rosterPositionOf(Player player) {
        int position = rosterOrder.indexOf(player);
        return position < 0 ? Integer.MAX_VALUE : position;
    }

    public LineupReading withRosterOrder(List<Player> order) {
        return new LineupReading(sectors, style, initialSetup, wantedSetup, lastIntervention, lastStyleChange, bench,
                cautioned, order);
    }

    public int countIn(Role sector) {
        List<Player> inSector = playersIn(sector);
        return inSector.size();
    }

    public List<Player> cautionedPlayersIn(Role sector) {
        return playersIn(sector).stream()
                .filter(cautioned::contains)
                .toList();
    }

    public boolean isMoreOffensiveThanAtStart() {
        return wantedSetup > initialSetup;
    }

    public boolean isMorePrudentThanAtStart() {
        return wantedSetup < initialSetup;
    }

    public Posture posture() {
        if (isMoreOffensiveThanAtStart()) {
            return Posture.UNBALANCED;
        }
        if (isMorePrudentThanAtStart()) {
            return Posture.MORE_CAUTIOUS;
        }
        return Posture.AS_AT_START;
    }

    public LineupReading withoutPlayer(Player player) {
        Map<Player, Role> remaining = new LinkedHashMap<>(sectors);
        remaining.remove(player);
        Set<Player> stillCautioned = new LinkedHashSet<>(cautioned);
        stillCautioned.remove(player);
        return new LineupReading(remaining, style, initialSetup, wantedSetup, lastIntervention, lastStyleChange, bench,
                stillCautioned, rosterOrder);
    }

    public LineupReading withPlayerIn(Player player, Role sector) {
        Map<Player, Role> updated = new LinkedHashMap<>(sectors);
        updated.put(player, sector);
        return new LineupReading(updated, style, initialSetup, wantedSetup, lastIntervention, lastStyleChange, bench,
                cautioned, rosterOrder);
    }

    public LineupReading withStyle(PlayStyle newStyle) {
        return new LineupReading(sectors, newStyle, initialSetup, wantedSetup, lastIntervention, lastStyleChange, bench,
                cautioned, rosterOrder);
    }

    public LineupReading withWantedSetupShiftedBy(int shift) {
        int shifted = wantedSetup + shift;
        return new LineupReading(sectors, style, initialSetup, shifted, lastIntervention, lastStyleChange, bench,
                cautioned, rosterOrder);
    }

    public LineupReading withInterventionAt(PlayedTime time) {
        LastIntervention intervention = LastIntervention.at(time);
        return new LineupReading(sectors, style, initialSetup, wantedSetup, intervention, lastStyleChange, bench,
                cautioned, rosterOrder);
    }

    public LineupReading withSubstitutionOf(Player entering) {
        BenchReading afterSubstitution = bench.afterSubstitution(entering);
        return new LineupReading(sectors, style, initialSetup, wantedSetup, lastIntervention, lastStyleChange,
                afterSubstitution, cautioned, rosterOrder);
    }

    public LineupReading withStyleChangeAt(PlayedTime time, int gradeShift) {
        LastStyleChange change = LastStyleChange.at(time, gradeShift);
        return new LineupReading(sectors, style, initialSetup, wantedSetup, lastIntervention, change, bench,
                cautioned, rosterOrder);
    }

    public LineupReading withCautioned(Player player) {
        Set<Player> updated = new LinkedHashSet<>(cautioned);
        updated.add(player);
        return new LineupReading(sectors, style, initialSetup, wantedSetup, lastIntervention, lastStyleChange, bench,
                updated, rosterOrder);
    }
}
