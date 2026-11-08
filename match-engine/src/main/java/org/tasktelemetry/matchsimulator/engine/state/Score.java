package org.tasktelemetry.matchsimulator.engine.state;

import org.tasktelemetry.matchsimulator.engine.MatchSide;

public record Score(int home, int away) {

    public Score {
        if (home < 0 || away < 0) {
            throw new IllegalArgumentException("goals must not be negative, were " + home + " and " + away);
        }
    }

    public static Score goalless() {
        return new Score(0, 0);
    }

    public int goalsOf(MatchSide side) {
        return switch (side) {
            case HOME -> home;
            case AWAY -> away;
        };
    }

    public int leadOf(MatchSide side) {
        return goalsOf(side) - goalsOf(side.opposite());
    }

    public Score withGoalBy(MatchSide side) {
        return switch (side) {
            case HOME -> new Score(home + 1, away);
            case AWAY -> new Score(home, away + 1);
        };
    }
}
