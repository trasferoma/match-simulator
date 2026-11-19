package org.tasktelemetry.matchsimulator.coach;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public record MatchCoaches(Coach home, Coach away) {

    public MatchCoaches {
        Objects.requireNonNull(home, "home coach must not be null");
        Objects.requireNonNull(away, "away coach must not be null");
    }

    public MatchCoaches replacing(MatchSide side, Coach coach) {
        Objects.requireNonNull(side, "match side must not be null");
        return switch (side) {
            case HOME -> new MatchCoaches(coach, away);
            case AWAY -> new MatchCoaches(home, coach);
        };
    }

    public void open(PreMatchDecision homeDecision, PreMatchDecision awayDecision, TieBreakDraw homeDraw,
            TieBreakDraw awayDraw) {
        Team homeTeam = homeDecision.team();
        Team awayTeam = awayDecision.team();
        home.open(MatchSide.HOME, homeDecision, awayTeam, homeDraw);
        away.open(MatchSide.AWAY, awayDecision, homeTeam, awayDraw);
    }

    public List<Intervention> decide(List<MatchEvent> newEvents, MatchMoment moment) {
        List<Intervention> homeRequests = home.decide(newEvents, moment);
        List<Intervention> awayRequests = away.decide(newEvents, moment);
        return Stream.concat(homeRequests.stream(), awayRequests.stream()).toList();
    }

    public void settle(List<Intervention> requests, List<InterventionResult> results) {
        if (requests.size() != results.size()) {
            throw new IllegalArgumentException("each request needs exactly one result: " + requests.size()
                    + " requests, " + results.size() + " results");
        }
        settleFor(home, MatchSide.HOME, requests, results);
        settleFor(away, MatchSide.AWAY, requests, results);
    }

    private void settleFor(Coach coach, MatchSide side, List<Intervention> requests,
            List<InterventionResult> results) {
        List<Intervention> ownRequests = new ArrayList<>();
        List<InterventionResult> ownResults = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            if (requests.get(index).side() == side) {
                ownRequests.add(requests.get(index));
                ownResults.add(results.get(index));
            }
        }
        if (ownRequests.isEmpty()) {
            return;
        }
        coach.interventionsSettled(ownRequests, ownResults);
    }

    public void close() {
        home.close();
        away.close();
    }
}
