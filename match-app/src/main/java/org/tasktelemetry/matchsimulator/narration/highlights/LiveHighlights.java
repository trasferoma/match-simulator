package org.tasktelemetry.matchsimulator.narration.highlights;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.live.MatchObserver;
import org.tasktelemetry.matchsimulator.narration.TeamNames;

public final class LiveHighlights implements MatchObserver {

    private final HighlightsSelector selector;
    private final HighlightsNarrator narrator;
    private final TeamNames teamNames;
    private final HighlightsListener listener;
    private final List<MatchEvent> happenedEvents = new ArrayList<>();
    private int deliveredSegmentCount;

    public LiveHighlights(HighlightsSelector selector, HighlightsNarrator narrator, TeamNames teamNames,
            HighlightsListener listener) {
        this.selector = Objects.requireNonNull(selector, "selector must not be null");
        this.narrator = Objects.requireNonNull(narrator, "narrator must not be null");
        this.teamNames = Objects.requireNonNull(teamNames, "team names must not be null");
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
    }

    @Override
    public void eventsHappened(List<MatchEvent> newEvents) {
        rememberHappened(newEvents);
        List<MatchEvent> eventsSoFar = findHappenedSoFar();
        List<HighlightSegment> selected = selectHighlights(eventsSoFar);
        List<HighlightSegment> decided = findDecided(selected, eventsSoFar.size());
        deliverTheNotDelivered(decided, eventsSoFar);
    }

    @Override
    public void matchFinished() {
        List<MatchEvent> allEvents = findHappenedSoFar();
        List<HighlightSegment> selected = selectHighlights(allEvents);
        deliverTheNotDelivered(selected, allEvents);
        announceChronicleFinished();
    }

    private void rememberHappened(List<MatchEvent> newEvents) {
        happenedEvents.addAll(newEvents);
    }

    private List<MatchEvent> findHappenedSoFar() {
        return List.copyOf(happenedEvents);
    }

    private List<HighlightSegment> selectHighlights(List<MatchEvent> events) {
        return selector.select(events);
    }

    private void announceChronicleFinished() {
        listener.chronicleFinished();
    }

    private List<HighlightSegment> findDecided(List<HighlightSegment> segments, int happenedCount) {
        int lastHappenedPosition = happenedCount - 1;
        int eventsAfterADecidedSegment = selector.lookAhead() + 1;
        int lastDecidedPosition = lastHappenedPosition - eventsAfterADecidedSegment;
        List<HighlightSegment> decided = new ArrayList<>();
        int endPosition = -1;
        for (HighlightSegment segment : segments) {
            endPosition += segment.events().size();
            if (endPosition > lastDecidedPosition) {
                break;
            }
            decided.add(segment);
        }
        return decided;
    }

    private void deliverTheNotDelivered(List<HighlightSegment> decided, List<MatchEvent> eventsSoFar) {
        List<HighlightSegment> notDelivered = decided.subList(deliveredSegmentCount, decided.size());
        List<NarratedSegment> narrated = narrator.narrate(notDelivered, eventsSoFar, teamNames);
        deliveredSegmentCount = decided.size();
        narrated.forEach(listener::segmentNarrated);
    }
}
