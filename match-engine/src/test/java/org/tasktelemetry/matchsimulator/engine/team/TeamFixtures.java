package org.tasktelemetry.matchsimulator.engine.team;

import java.util.ArrayList;
import java.util.List;

final class TeamFixtures {

    private static final PlayerAttributes DEFAULT_ATTRIBUTES = new PlayerAttributes(50, 50, 50, 50, 50, 50, 50, 50);

    private TeamFixtures() {
    }

    static Player player(String name, Role role) {
        return new Player(name, role, DEFAULT_ATTRIBUTES);
    }

    static List<Player> lineup(String prefix, int goalkeepers, int defenders, int midfielders, int forwards) {
        List<Player> players = new ArrayList<>();
        players.addAll(playersOf(prefix, Role.GOALKEEPER, goalkeepers));
        players.addAll(playersOf(prefix, Role.DEFENDER, defenders));
        players.addAll(playersOf(prefix, Role.MIDFIELDER, midfielders));
        players.addAll(playersOf(prefix, Role.FORWARD, forwards));
        return players;
    }

    static List<Player> standardStarters() {
        return lineup("H", 1, 4, 4, 2);
    }

    static List<Player> standardBench() {
        return lineup("B", 1, 2, 2, 2);
    }

    static Team teamWithStarters(List<Player> starters) {
        return new Team("Home", PlayStyle.BALANCED, starters, List.of());
    }

    static Team teamWithBench(List<Player> bench) {
        return new Team("Home", PlayStyle.BALANCED, standardStarters(), bench);
    }

    private static List<Player> playersOf(String prefix, Role role, int count) {
        List<Player> players = new ArrayList<>();
        for (int number = 1; number <= count; number++) {
            players.add(player(prefix + "-" + role + "-" + number, role));
        }
        return players;
    }
}
