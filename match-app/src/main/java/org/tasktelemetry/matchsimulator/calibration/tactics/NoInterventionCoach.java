package org.tasktelemetry.matchsimulator.calibration.tactics;

import java.util.List;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.coach.Coach;
import org.tasktelemetry.matchsimulator.coach.TieBreakDraw;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.Team;

final class NoInterventionCoach implements Coach {

    @Override
    public void open(MatchSide side, PreMatchDecision own, Team opponent, TieBreakDraw draw) {
    }

    @Override
    public Optional<Intervention> decide(List<MatchEvent> newEvents) {
        return Optional.empty();
    }

    @Override
    public void close() {
    }
}
