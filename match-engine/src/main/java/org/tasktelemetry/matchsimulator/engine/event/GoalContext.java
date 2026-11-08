package org.tasktelemetry.matchsimulator.engine.event;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.state.Score;

public enum GoalContext {
    TAKES_THE_LEAD,
    EQUALISER,
    EXTENDS_THE_LEAD,
    CONSOLATION;

    public static GoalContext of(MatchSide scorer, Score before) {
        int scorerGoals = before.goalsOf(scorer);
        int opponentGoals = before.goalsOf(scorer.opposite());
        int lead = scorerGoals - opponentGoals;
        if (lead > 0) {
            return EXTENDS_THE_LEAD;
        }
        if (lead == 0) {
            return TAKES_THE_LEAD;
        }
        if (lead == -1) {
            return EQUALISER;
        }
        return CONSOLATION;
    }
}
