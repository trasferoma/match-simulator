package org.tasktelemetry.matchsimulator.coach.human;

import java.util.List;

import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;

public interface DugoutSession {

    boolean isPauseRequested();

    void prepareForPause();

    List<Intervention> open(TeamSheet sheet, PlannedMoves plannedMoves);

    void showOutcomes(List<Intervention> moves, List<InterventionResult> results);
}
