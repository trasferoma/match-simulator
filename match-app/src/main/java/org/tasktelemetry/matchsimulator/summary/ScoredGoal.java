package org.tasktelemetry.matchsimulator.summary;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public record ScoredGoal(MatchSide side, Player scorer, MatchTime time) {

    public ScoredGoal {
        Objects.requireNonNull(side, "goal side must not be null");
        Objects.requireNonNull(scorer, "goal scorer must not be null");
        Objects.requireNonNull(time, "goal time must not be null");
    }
}
