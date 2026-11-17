package org.tasktelemetry.matchsimulator.coach.reading;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;

public record ScoreReading(int homeGoals, int awayGoals) {

    public ScoreReading {
        if (homeGoals < 0 || awayGoals < 0) {
            throw new IllegalArgumentException("goals must not be negative, were " + homeGoals + " and " + awayGoals);
        }
    }

    public static ScoreReading goalless() {
        return new ScoreReading(0, 0);
    }

    public ScoreReading after(MatchEvent event) {
        if (event.type() != EventType.GOAL) {
            return this;
        }
        if (event.side() == MatchSide.HOME) {
            return new ScoreReading(homeGoals + 1, awayGoals);
        }
        return new ScoreReading(homeGoals, awayGoals + 1);
    }

    public int goalsOf(MatchSide side) {
        return side == MatchSide.HOME ? homeGoals : awayGoals;
    }

    public int leadOf(MatchSide side) {
        MatchSide opponentSide = side.opposite();
        int own = goalsOf(side);
        int opponent = goalsOf(opponentSide);
        return own - opponent;
    }
}
