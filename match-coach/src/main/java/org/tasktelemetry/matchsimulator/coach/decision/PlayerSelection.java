package org.tasktelemetry.matchsimulator.coach.decision;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.coach.reading.FatigueReading;
import org.tasktelemetry.matchsimulator.coach.reading.LineupReading;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

final class PlayerSelection {

    Optional<Player> bestReserveWithRole(LineupReading lineup, Role role) {
        List<Player> reserves = lineup.bench().available();
        List<Player> inRole = withRole(reserves, role);
        return strongestOf(inRole, lineup);
    }

    Optional<Player> weakestPlayingIn(TeamNow team, Role sector) {
        List<Player> inSector = team.lineup().playersIn(sector);
        return weakestAmong(inSector, team);
    }

    Optional<Player> weakestAmong(List<Player> players, TeamNow team) {
        if (players.isEmpty()) {
            return Optional.empty();
        }
        List<Player> mostTired = thoseInTheWorstFatigueBand(players, team);
        List<Player> lowestAverage = thoseWithLowestMovementAverage(mostTired);
        Player drawn = drawOneOf(lowestAverage, team);
        return Optional.of(drawn);
    }

    Optional<Player> strongestPlayingIn(LineupReading lineup, Role sector) {
        List<Player> inSector = lineup.playersIn(sector);
        return strongestOf(inSector, lineup);
    }

    Optional<Player> strongestPlayingInWithRole(LineupReading lineup, Role sector, Role role) {
        List<Player> inSector = lineup.playersIn(sector);
        List<Player> inRole = withRole(inSector, role);
        return strongestOf(inRole, lineup);
    }

    private List<Player> withRole(List<Player> players, Role role) {
        return players.stream()
                .filter(player -> player.role() == role)
                .toList();
    }

    private Optional<Player> strongestOf(List<Player> players, LineupReading lineup) {
        Comparator<Player> byRating = byMovementAverage();
        Comparator<Player> earlierInTheRosterWins = Comparator.comparingInt(lineup::rosterPositionOf).reversed();
        Comparator<Player> strength = byRating.thenComparing(earlierInTheRosterWins);
        return players.stream().max(strength);
    }

    private List<Player> thoseInTheWorstFatigueBand(List<Player> players, TeamNow team) {
        FatigueReading fatigue = team.fatigue();
        FatigueBand worst = players.stream()
                .map(fatigue::bandOf)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        return players.stream()
                .filter(player -> fatigue.bandOf(player) == worst)
                .toList();
    }

    private List<Player> thoseWithLowestMovementAverage(List<Player> players) {
        double lowest = players.stream().mapToDouble(this::movementAverageOf).min().orElseThrow();
        return players.stream()
                .filter(player -> movementAverageOf(player) == lowest)
                .toList();
    }

    private double movementAverageOf(Player player) {
        return player.attributes().movementAverage();
    }

    private Player drawOneOf(List<Player> tied, TeamNow team) {
        if (tied.size() == 1) {
            return tied.get(0);
        }
        Comparator<Player> byRosterPosition = Comparator.comparingInt(team.lineup()::rosterPositionOf);
        List<Player> inRosterOrder = tied.stream().sorted(byRosterPosition).toList();
        double draw = team.draw().nextDouble();
        int index = (int) (draw * inRosterOrder.size());
        return inRosterOrder.get(index);
    }

    private Comparator<Player> byMovementAverage() {
        return Comparator.comparingDouble(this::movementAverageOf);
    }
}
