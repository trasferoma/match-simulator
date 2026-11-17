package org.tasktelemetry.matchsimulator.live;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.coach.MatchCoaches;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.engine.team.Team;
import org.tasktelemetry.matchsimulator.narration.TeamNames;

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
}
