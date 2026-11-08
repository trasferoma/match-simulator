package org.tasktelemetry.matchsimulator.engine.state;

import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public record TeamOnPitch(Team team, List<Player> players) {

    public TeamOnPitch {
        Objects.requireNonNull(team, "team must not be null");
        Objects.requireNonNull(players, "players on the pitch must not be null");
        players = List.copyOf(players);
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
                .filter(player -> player.role() == role)
                .toList();
    }
}
