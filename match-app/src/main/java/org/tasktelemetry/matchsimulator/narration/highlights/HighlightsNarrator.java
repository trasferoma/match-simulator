package org.tasktelemetry.matchsimulator.narration.highlights;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.narration.NarrationContext;
import org.tasktelemetry.matchsimulator.narration.Narrator;
import org.tasktelemetry.matchsimulator.narration.PitchDrawing;
import org.tasktelemetry.matchsimulator.narration.TeamNames;

public final class HighlightsNarrator {

    private static final String WORD_SEPARATOR = " ";

    private final Narrator narrator;
    private final PitchDrawing pitchDrawing;

    public HighlightsNarrator(Narrator narrator, PitchDrawing pitchDrawing) {
        this.narrator = narrator;
        this.pitchDrawing = pitchDrawing;
    }

    public static HighlightsNarrator italian() {
        Narrator narrator = Narrator.italian();
        PitchDrawing pitchDrawing = new PitchDrawing();
        return new HighlightsNarrator(narrator, pitchDrawing);
    }

    public List<NarratedSegment> narrate(List<HighlightSegment> segments, List<MatchEvent> events,
            TeamNames teamNames) {
        Objects.requireNonNull(segments, "segments must not be null");
        Objects.requireNonNull(events, "events must not be null");
        Objects.requireNonNull(teamNames, "team names must not be null");
        NarrationContext context = new NarrationContext(events, teamNames);
        return segments.stream()
                .map(segment -> narrateSegment(segment, context))
                .toList();
    }

    private NarratedSegment narrateSegment(HighlightSegment segment, NarrationContext context) {
        return switch (segment) {
            case HighlightSegment.Frame frame -> showFrame(frame.event(), context);
            case HighlightSegment.Stop stop -> tellStop(stop, context);
        };
    }

    private NarratedSegment showFrame(MatchEvent event, NarrationContext context) {
        String frameLine = drawFrame(event, context);
        return new NarratedSegment.Frame(frameLine);
    }

    private String drawFrame(MatchEvent event, NarrationContext context) {
        return drawFieldLine(event, event, context);
    }

    private String drawFieldLine(MatchEvent positionEvent, MatchEvent scoreEvent, NarrationContext context) {
        TeamNames teamNames = context.teamNames();
        Score score = context.scoreAfter(scoreEvent);
        String field = pitchDrawing.draw(positionEvent, teamNames, score);
        String time = positionEvent.time().display();
        return field + WORD_SEPARATOR + time;
    }

    private NarratedSegment tellStop(HighlightSegment.Stop stop, NarrationContext context) {
        boolean containsChance = stop.containsChance();
        MatchEvent firstEvent = stop.firstEvent();
        String fieldLine = drawFrame(firstEvent, context);
        List<NarratedLine> lines = stop.events().stream()
                .flatMap(event -> tellEvent(event, firstEvent, containsChance, context).stream())
                .toList();
        return new NarratedSegment.Stop(fieldLine, lines, containsChance);
    }

    private List<NarratedLine> tellEvent(MatchEvent event, MatchEvent firstEvent, boolean stopContainsChance,
            NarrationContext context) {
        String fieldLine = drawFieldLine(firstEvent, event, context);
        String text = narrator.narrateEventLine(event, context);
        LinePause pause = pauseOfEventLine(event, stopContainsChance);
        NarratedLine eventLine = new NarratedLine(text, pause, fieldLine);
        Optional<NarratedLine> contextLine = tellGoalContext(event, fieldLine, context);
        return contextLine
                .map(goalContext -> List.of(eventLine, goalContext))
                .orElse(List.of(eventLine));
    }

    private Optional<NarratedLine> tellGoalContext(MatchEvent event, String fieldLine, NarrationContext context) {
        Optional<String> text = narrator.narrateGoalContextLine(event, context);
        return text.map(contextText -> new NarratedLine(contextText, LinePause.GOAL_CONTEXT_LINE, fieldLine));
    }

    private LinePause pauseOfEventLine(MatchEvent event, boolean stopContainsChance) {
        if (event.type() == EventType.GOAL) {
            return LinePause.GOAL_LINE;
        }
        return stopContainsChance ? LinePause.CHANCE_LINE : LinePause.EVENT_LINE;
    }
}
