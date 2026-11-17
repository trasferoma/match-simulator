package org.tasktelemetry.matchsimulator.coach;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public record MatchCoaches(Coach home, Coach away) {

    public MatchCoaches {
        Objects.requireNonNull(home, "home coach must not be null");
        Objects.requireNonNull(away, "away coach must not be null");
    }

    public void open(PreMatchDecision homeDecision, PreMatchDecision awayDecision, TieBreakDraw homeDraw,
            TieBreakDraw awayDraw) {
        Team homeTeam = homeDecision.team();
        Team awayTeam = awayDecision.team();
        home.open(MatchSide.HOME, homeDecision, awayTeam, homeDraw);
        away.open(MatchSide.AWAY, awayDecision, homeTeam, awayDraw);
    }

    public List<Intervention> decide(List<MatchEvent> newEvents) {
        Optional<Intervention> homeRequest = home.decide(newEvents);
        Optional<Intervention> awayRequest = away.decide(newEvents);
        return Stream.of(homeRequest, awayRequest)
                .flatMap(Optional::stream)
                .toList();
    }

    public void close() {
        home.close();
        away.close();
    }
}
