package org.tasktelemetry.matchsimulator.coach.decision;

import org.tasktelemetry.matchsimulator.coach.TieBreakDraw;
import org.tasktelemetry.matchsimulator.coach.reading.FatigueReading;
import org.tasktelemetry.matchsimulator.coach.reading.LineupReading;
import org.tasktelemetry.matchsimulator.coach.reading.PlayedTime;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;

record TeamNow(MatchSide side, LineupReading lineup, FatigueReading fatigue, int minute, PlayedTime playedTime,
        TieBreakDraw draw) {

    PlayStyle style() {
        return lineup.style();
    }
}
