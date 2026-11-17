package org.tasktelemetry.matchsimulator.coach.lineup;

import java.util.List;

import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.coach.config.Formation;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public record PreMatchDecision(Team team, double evaluation, AssessmentBand band, Formation formation,
        List<Player> rosterOrder) {

    public PreMatchDecision {
        rosterOrder = List.copyOf(rosterOrder);
    }
}
