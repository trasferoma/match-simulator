package org.tasktelemetry.matchsimulator.coach.decision;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.coach.TieBreakDraw;
import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.coach.reading.EventWindow;
import org.tasktelemetry.matchsimulator.coach.reading.MatchReading;
import org.tasktelemetry.matchsimulator.coach.reading.SituationClocks;
import org.tasktelemetry.matchsimulator.engine.MatchSide;

public record MatchNotes(MatchSide side, AssessmentBand band, MatchReading reading, EventWindow window,
        SituationClocks clocks, TieBreakDraw draw) {

    public MatchNotes {
        Objects.requireNonNull(side, "match side must not be null");
        Objects.requireNonNull(band, "assessment band must not be null");
        Objects.requireNonNull(reading, "reading must not be null");
        Objects.requireNonNull(window, "event window must not be null");
        Objects.requireNonNull(clocks, "situation clocks must not be null");
        Objects.requireNonNull(draw, "tie-break draw must not be null");
    }

    public static MatchNotes blank(MatchSide side, AssessmentBand band, MatchReading reading, TieBreakDraw draw) {
        EventWindow noEvents = EventWindow.empty();
        SituationClocks idle = SituationClocks.idle();
        return new MatchNotes(side, band, reading, noEvents, idle, draw);
    }

    public MatchNotes after(MatchReading newReading, EventWindow newWindow, SituationClocks newClocks) {
        return new MatchNotes(side, band, newReading, newWindow, newClocks, draw);
    }
}
