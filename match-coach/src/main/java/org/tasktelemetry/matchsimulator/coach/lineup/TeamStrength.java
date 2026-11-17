package org.tasktelemetry.matchsimulator.coach.lineup;

import java.util.List;

import org.tasktelemetry.matchsimulator.coach.squad.Squad;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

final class TeamStrength {

    private static final int OUTFIELD_COUNT = Team.STARTERS_COUNT - 1;

    private final RosterRanking ranking;

    public TeamStrength(RosterRanking ranking) {
        this.ranking = ranking;
    }

    public double of(Squad squad) {
        List<Player> bestOutfield = pickBestOutfield(squad.players());
        Player bestGoalkeeper = pickBestGoalkeeper(squad.players());
        double outfieldTotal = sumMovementAverages(bestOutfield);
        double goalkeeping = bestGoalkeeper.attributes().goalkeeping();
        return (outfieldTotal + goalkeeping) / Team.STARTERS_COUNT;
    }

    private List<Player> pickBestOutfield(List<Player> players) {
        List<Player> outfield = ranking.bestFirstOutfield(players);
        if (outfield.size() < OUTFIELD_COUNT) {
            throw new IllegalArgumentException("a squad needs at least " + OUTFIELD_COUNT
                    + " outfield players to measure its strength, found " + outfield.size());
        }
        return outfield.subList(0, OUTFIELD_COUNT);
    }

    private Player pickBestGoalkeeper(List<Player> players) {
        return ranking.bestFirstIn(players, Role.GOALKEEPER).getFirst();
    }

    private double sumMovementAverages(List<Player> players) {
        return players.stream()
                .mapToDouble(player -> player.attributes().movementAverage())
                .sum();
    }
}
