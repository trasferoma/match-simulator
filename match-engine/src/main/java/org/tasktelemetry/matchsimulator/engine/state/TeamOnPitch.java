package org.tasktelemetry.matchsimulator.engine.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public record TeamOnPitch(Team team, List<Player> players, Map<Player, Role> reassigned, List<Player> cautioned,
        int substitutionsMade, List<Player> withdrawn) {

    public TeamOnPitch {
        Objects.requireNonNull(team, "team must not be null");
        Objects.requireNonNull(players, "players on the pitch must not be null");
        Objects.requireNonNull(reassigned, "reassigned roles must not be null");
        Objects.requireNonNull(cautioned, "cautioned players must not be null");
        Objects.requireNonNull(withdrawn, "withdrawn players must not be null");
        if (substitutionsMade < 0) {
            throw new IllegalArgumentException("substitutions made must not be negative, was " + substitutionsMade);
        }
        players = List.copyOf(players);
        reassigned = Collections.unmodifiableMap(new LinkedHashMap<>(reassigned));
        cautioned = List.copyOf(cautioned);
        withdrawn = List.copyOf(withdrawn);
    }

    public TeamOnPitch(Team team, List<Player> players) {
        this(team, players, new LinkedHashMap<>(), List.of(), 0);
    }

    public TeamOnPitch(Team team, List<Player> players, Map<Player, Role> reassigned, List<Player> cautioned,
            int substitutionsMade) {
        this(team, players, reassigned, cautioned, substitutionsMade, List.of());
    }

    public static TeamOnPitch startingWith(Team team) {
        return new TeamOnPitch(team, team.starters());
    }

    public Player goalkeeper() {
        return playersIn(Role.GOALKEEPER).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no goalkeeper on the pitch for " + team.name()));
    }

    public List<Player> playersIn(Role role) {
        return players.stream()
                .filter(player -> roleOf(player) == role)
                .toList();
    }

    public Role roleOf(Player player) {
        return reassigned.getOrDefault(player, player.role());
    }

    public int missingIn(Role sector) {
        long startersInSector = team.starters().stream()
                .filter(player -> player.role() == sector)
                .count();
        int onPitchInSector = playersIn(sector).size();
        return Math.max(0, (int) startersInSector - onPitchInSector);
    }

    public boolean isCautioned(Player player) {
        return cautioned.contains(player);
    }

    public List<Player> availableBench() {
        return team.bench().stream()
                .filter(player -> !players.contains(player))
                .filter(player -> !withdrawn.contains(player))
                .toList();
    }

    public TeamOnPitch withoutPlayer(Player player) {
        requireOnPitch(player);
        List<Player> remaining = players.stream().filter(other -> !other.equals(player)).toList();
        Map<Player, Role> remainingRoles = new LinkedHashMap<>(reassigned);
        remainingRoles.remove(player);
        List<Player> remainingCautioned = cautioned.stream().filter(other -> !other.equals(player)).toList();
        List<Player> nowWithdrawn = withdrawnWith(player);
        return new TeamOnPitch(team, remaining, remainingRoles, remainingCautioned, substitutionsMade,
                nowWithdrawn);
    }

    public TeamOnPitch withRole(Player player, Role role) {
        requireOnPitch(player);
        Map<Player, Role> roles = new LinkedHashMap<>(reassigned);
        roles.put(player, role);
        return new TeamOnPitch(team, players, roles, cautioned, substitutionsMade, withdrawn);
    }

    public TeamOnPitch withCautioned(Player player) {
        requireOnPitch(player);
        List<Player> nowCautioned = new ArrayList<>(cautioned);
        nowCautioned.add(player);
        return new TeamOnPitch(team, players, reassigned, nowCautioned, substitutionsMade, withdrawn);
    }

    public TeamOnPitch withSubstitution(Player leaving, Player entering, Role enteringAs) {
        requireOnPitch(leaving);
        List<Player> lineup = new ArrayList<>(players);
        lineup.set(lineup.indexOf(leaving), entering);
        Map<Player, Role> roles = new LinkedHashMap<>(reassigned);
        roles.remove(leaving);
        roles.put(entering, enteringAs);
        List<Player> remainingCautioned = cautioned.stream().filter(other -> !other.equals(leaving)).toList();
        List<Player> nowWithdrawn = withdrawnWith(leaving);
        return new TeamOnPitch(team, lineup, roles, remainingCautioned, substitutionsMade + 1, nowWithdrawn);
    }

    private List<Player> withdrawnWith(Player leaving) {
        List<Player> nowWithdrawn = new ArrayList<>(withdrawn);
        nowWithdrawn.add(leaving);
        return nowWithdrawn;
    }

    private void requireOnPitch(Player player) {
        if (!players.contains(player)) {
            throw new IllegalArgumentException(player.name() + " is not on the pitch for " + team.name());
        }
    }
}
