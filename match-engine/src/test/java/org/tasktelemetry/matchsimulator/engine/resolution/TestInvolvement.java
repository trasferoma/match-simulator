package org.tasktelemetry.matchsimulator.engine.resolution;

import java.util.List;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class TestInvolvement {

    private TestInvolvement() {
    }

    public static List<Player> involvedOn(ActionOutcome outcome, MatchSide side) {
        return outcome.involved().involvements().stream()
                .filter(involvement -> involvement.side() == side)
                .map(InvolvedPlayers.Involvement::player)
                .toList();
    }
}
