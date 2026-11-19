package org.tasktelemetry.matchsimulator.live;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.coach.Coach;
import org.tasktelemetry.matchsimulator.coach.MatchCoaches;
import org.tasktelemetry.matchsimulator.coach.config.Formation;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.team.Team;
import org.tasktelemetry.matchsimulator.narration.MatchSetups;
import org.tasktelemetry.matchsimulator.narration.TeamNames;
import org.tasktelemetry.matchsimulator.narration.TeamSetup;

public record PreparedMatch(PreMatchDecision homeDecision, PreMatchDecision awayDecision, TeamNames names,
        MatchCoaches coaches) {

    public PreparedMatch {
        Objects.requireNonNull(homeDecision, "home decision must not be null");
        Objects.requireNonNull(awayDecision, "away decision must not be null");
        Objects.requireNonNull(names, "team names must not be null");
        Objects.requireNonNull(coaches, "coaches must not be null");
    }

    public Team home() {
        return homeDecision.team();
    }

    public Team away() {
        return awayDecision.team();
    }

    public PreparedMatch withCoach(MatchSide side, Coach coach) {
        MatchCoaches replaced = coaches.replacing(side, coach);
        return new PreparedMatch(homeDecision, awayDecision, names, replaced);
    }

    public MatchSetups openingSetups() {
        TeamSetup homeSetup = openingSetupOf(homeDecision);
        TeamSetup awaySetup = openingSetupOf(awayDecision);
        return new MatchSetups(homeSetup, awaySetup);
    }

    private TeamSetup openingSetupOf(PreMatchDecision decision) {
        Formation formation = decision.formation();
        Deployment deployment = new Deployment(formation.defenders(), formation.midfielders(), formation.forwards());
        return new TeamSetup(deployment, decision.team().style());
    }
}
