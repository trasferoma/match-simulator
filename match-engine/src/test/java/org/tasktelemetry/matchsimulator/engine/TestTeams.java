package org.tasktelemetry.matchsimulator.engine;

import java.util.ArrayList;
import java.util.List;

import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public final class TestTeams {

    private static final int NEUTRAL_RATING = 50;

    private TestTeams() {
    }

    public static PlayerAttributes uniform(int rating) {
        return new PlayerAttributes(rating, rating, rating, rating, rating, rating, rating, rating);
    }

    public static PlayerAttributes withDuelAttributes(int passing, int dribbling, int tackling, int positioning) {
        return new PlayerAttributes(passing, dribbling, NEUTRAL_RATING, tackling, positioning, NEUTRAL_RATING,
                NEUTRAL_RATING, NEUTRAL_RATING);
    }

    public static PlayerAttributes withShootingAndGoalkeeping(int shooting, int goalkeeping) {
        return new PlayerAttributes(NEUTRAL_RATING, NEUTRAL_RATING, shooting, NEUTRAL_RATING, NEUTRAL_RATING,
                NEUTRAL_RATING, NEUTRAL_RATING, goalkeeping);
    }

    public static Player player(String name, Role role, PlayerAttributes attributes) {
        return new Player(name, role, attributes);
    }

    public static Team uniformTeam(String name, int rating) {
        return team(name, PlayStyle.BALANCED, rating);
    }

    public static Team team(String name, PlayStyle style, int rating) {
        PlayerAttributes attributes = uniform(rating);
        List<Player> starters = squad(name, "S", attributes, 1, 4, 4, 2);
        List<Player> bench = squad(name, "B", attributes, 1, 2, 2, 2);
        return new Team(name, style, starters, bench);
    }

    public static Team teamWithKeeperAndForwards(String name, PlayerAttributes keeper, PlayerAttributes forwards) {
        PlayerAttributes others = uniform(NEUTRAL_RATING);
        List<Player> starters = new ArrayList<>();
        starters.addAll(squad(name, "S", keeper, 1, 0, 0, 0));
        starters.addAll(squad(name, "S", others, 0, 4, 4, 0));
        starters.addAll(squad(name, "S", forwards, 0, 0, 0, 2));
        List<Player> bench = squad(name, "B", others, 1, 2, 2, 2);
        return new Team(name, PlayStyle.BALANCED, starters, bench);
    }

    public static Team teamOf(String name, List<Player> starters) {
        return new Team(name, PlayStyle.BALANCED, starters, List.of());
    }

    public static List<Player> squad(String teamName, String group, PlayerAttributes attributes, int goalkeepers,
            int defenders, int midfielders, int forwards) {
        List<Player> players = new ArrayList<>();
        players.addAll(players(teamName, group, Role.GOALKEEPER, goalkeepers, attributes));
        players.addAll(players(teamName, group, Role.DEFENDER, defenders, attributes));
        players.addAll(players(teamName, group, Role.MIDFIELDER, midfielders, attributes));
        players.addAll(players(teamName, group, Role.FORWARD, forwards, attributes));
        return players;
    }

    private static List<Player> players(String teamName, String group, Role role, int count,
            PlayerAttributes attributes) {
        List<Player> players = new ArrayList<>();
        for (int number = 1; number <= count; number++) {
            String name = teamName + "-" + group + "-" + role + "-" + number;
            players.add(new Player(name, role, attributes));
        }
        return players;
    }
}
