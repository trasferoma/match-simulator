package org.tasktelemetry.matchsimulator.coach.lineup;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

final class BenchPicker {

    private static final List<Role> ROTATION = List.of(Role.DEFENDER, Role.MIDFIELDER, Role.FORWARD);

    private final int maximumBenchSize;
    private final RosterRanking ranking;

    public BenchPicker(int maximumBenchSize, RosterRanking ranking) {
        this.maximumBenchSize = maximumBenchSize;
        this.ranking = ranking;
    }

    public List<Player> pick(List<Player> players, List<Player> starters) {
        List<Player> reserves = listReserves(players, starters);
        List<Player> byPriority = orderByPriority(reserves);
        List<Player> bench = byPriority.stream()
                .limit(maximumBenchSize)
                .toList();
        return ranking.inRosterOrder(players, bench);
    }

    private List<Player> listReserves(List<Player> players, List<Player> starters) {
        return players.stream()
                .filter(player -> !starters.contains(player))
                .toList();
    }

    private List<Player> orderByPriority(List<Player> reserves) {
        List<Player> goalkeepers = ranking.bestFirstIn(reserves, Role.GOALKEEPER);
        List<Player> bestGoalkeeper = goalkeepers.stream()
                .limit(1)
                .toList();
        List<Player> spareGoalkeepers = goalkeepers.stream()
                .skip(1)
                .toList();
        List<Player> rotation = rotateThroughSectors(reserves);
        return Stream.of(bestGoalkeeper, rotation, spareGoalkeepers)
                .flatMap(List::stream)
                .toList();
    }

    private List<Player> rotateThroughSectors(List<Player> reserves) {
        List<List<Player>> rankedBySector = ROTATION.stream()
                .map(role -> ranking.bestFirstIn(reserves, role))
                .toList();
        return IntStream.range(0, reserves.size())
                .boxed()
                .flatMap(round -> rankedBySector.stream()
                        .filter(sector -> round < sector.size())
                        .map(sector -> sector.get(round)))
                .toList();
    }
}
