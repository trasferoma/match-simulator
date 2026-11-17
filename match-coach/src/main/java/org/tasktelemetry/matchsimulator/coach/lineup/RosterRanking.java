package org.tasktelemetry.matchsimulator.coach.lineup;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

final class RosterRanking {

    public List<Player> bestFirstIn(List<Player> players, Role role) {
        Stream<Player> inRole = players.stream()
                .filter(player -> player.role() == role);
        return bestFirst(inRole);
    }

    public List<Player> bestFirstOutfield(List<Player> players) {
        Stream<Player> outfield = players.stream()
                .filter(player -> player.role() != Role.GOALKEEPER);
        return bestFirst(outfield);
    }

    public List<Player> inRosterOrder(List<Player> roster, List<Player> chosen) {
        return roster.stream()
                .filter(chosen::contains)
                .toList();
    }

    private List<Player> bestFirst(Stream<Player> players) {
        Comparator<Player> byRatingDescending = Comparator.comparingDouble(this::ratingOf).reversed();
        return players
                .sorted(byRatingDescending)
                .toList();
    }

    private double ratingOf(Player player) {
        if (player.role() == Role.GOALKEEPER) {
            return player.attributes().goalkeeping();
        }
        return player.attributes().movementAverage();
    }
}
