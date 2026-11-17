package org.tasktelemetry.matchsimulator.coach;

import java.util.List;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public interface Coach {

    void open(MatchSide side, PreMatchDecision own, Team opponent, TieBreakDraw draw);

    Optional<Intervention> decide(List<MatchEvent> newEvents);

    void close();
}
