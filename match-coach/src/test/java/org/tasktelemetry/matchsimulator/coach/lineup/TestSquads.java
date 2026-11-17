package org.tasktelemetry.matchsimulator.coach.lineup;

import java.util.ArrayList;
import java.util.List;

import org.tasktelemetry.matchsimulator.coach.squad.CoachAttributes;
import org.tasktelemetry.matchsimulator.coach.squad.CoachProfile;
import org.tasktelemetry.matchsimulator.coach.squad.Squad;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;

final class TestSquads {

    private static final int NEUTRAL_VALUE = 1;

    private TestSquads() {
    }

    static Player outfield(String name, Role role, int movement) {
        PlayerAttributes attributes = new PlayerAttributes(movement, movement, movement, movement, movement,
                movement, movement, NEUTRAL_VALUE, 50);
        return new Player(name, role, attributes);
    }

    static Player goalkeeper(String name, int goalkeeping) {
        return goalkeeper(name, goalkeeping, NEUTRAL_VALUE);
    }

    static Player goalkeeper(String name, int goalkeeping, int movement) {
        PlayerAttributes attributes = new PlayerAttributes(movement, movement, movement, movement, movement,
                movement, movement, goalkeeping, 50);
        return new Player(name, Role.GOALKEEPER, attributes);
    }

    static Player uniform(String name, Role role, int rating) {
        PlayerAttributes attributes = new PlayerAttributes(rating, rating, rating, rating, rating, rating, rating,
                rating, 50);
        return new Player(name, role, attributes);
    }

    static Squad squad(String name, List<Player> players) {
        CoachProfile coach = new CoachProfile("Coach " + name, new CoachAttributes(50));
        return new Squad(name, coach, players);
    }

    static List<Player> uniformPlayers(String prefix, int goalkeepers, int defenders, int midfielders,
            int forwards, int rating) {
        List<Player> players = new ArrayList<>();
        players.addAll(uniformGroup(prefix + "-gk", Role.GOALKEEPER, goalkeepers, rating));
        players.addAll(uniformGroup(prefix + "-d", Role.DEFENDER, defenders, rating));
        players.addAll(uniformGroup(prefix + "-m", Role.MIDFIELDER, midfielders, rating));
        players.addAll(uniformGroup(prefix + "-f", Role.FORWARD, forwards, rating));
        return players;
    }

    static Squad uniformSquad(String name, int goalkeepers, int defenders, int midfielders, int forwards,
            int rating) {
        List<Player> players = uniformPlayers(name, goalkeepers, defenders, midfielders, forwards, rating);
        return squad(name, players);
    }

    static Squad fullSquad(String name, int rating) {
        return uniformSquad(name, 2, 6, 6, 4, rating);
    }

    private static List<Player> uniformGroup(String prefix, Role role, int count, int rating) {
        List<Player> players = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            players.add(uniform(prefix + index, role, rating));
        }
        return players;
    }
}
