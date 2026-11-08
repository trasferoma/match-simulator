package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;

public final class MinimumPlayersRule {

    private static final int MINIMUM_PLAYERS = 7;
    private static final int FORFEIT_GOALS = 3;

    public boolean isBelowMinimum(TeamOnPitch team) {
        return team.players().size() < MINIMUM_PLAYERS;
    }

    public Score officialResult(MatchSide responsibleSide, Score fieldScore) {
        MatchSide winningSide = responsibleSide.opposite();
        int winnerLead = fieldScore.leadOf(winningSide);
        if (winnerLead >= FORFEIT_GOALS) {
            return fieldScore;
        }
        return forfeitWonBy(winningSide);
    }

    private Score forfeitWonBy(MatchSide winningSide) {
        return switch (winningSide) {
            case HOME -> new Score(FORFEIT_GOALS, 0);
            case AWAY -> new Score(0, FORFEIT_GOALS);
        };
    }
}
