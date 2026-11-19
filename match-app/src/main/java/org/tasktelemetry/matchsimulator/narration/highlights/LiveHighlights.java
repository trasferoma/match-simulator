package org.tasktelemetry.matchsimulator.narration.highlights;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.live.MatchObserver;
import org.tasktelemetry.matchsimulator.narration.MatchSetups;
import org.tasktelemetry.matchsimulator.narration.TeamNames;

public final class LiveHighlights implements MatchObserver {

    private static final int NOTHING_NARRATED = -1;

    private final HighlightsSelector selector;
    private final HighlightsNarrator narrator;
    private final TeamNames teamNames;
    private final MatchSetups openingSetups;
    private final HighlightsListener listener;
    private final List<MatchEvent> happenedEvents = new ArrayList<>();
    private int lastNarratedPosition = NOTHING_NARRATED;
    private int selectionStart;

    public LiveHighlights(HighlightsSelector selector, HighlightsNarrator narrator, TeamNames teamNames,
            MatchSetups openingSetups, HighlightsListener listener) {
        this.selector = Objects.requireNonNull(selector, "selector must not be null");
        this.narrator = Objects.requireNonNull(narrator, "narrator must not be null");
        this.teamNames = Objects.requireNonNull(teamNames, "team names must not be null");
        this.openingSetups = Objects.requireNonNull(openingSetups, "opening setups must not be null");
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
    }

    @Override
    public void eventsHappened(List<MatchEvent> newEvents) {
        rememberHappened(newEvents);
        List<MatchEvent> eventsSoFar = findHappenedSoFar();
        List<MatchEvent> selectable = findSelectable(eventsSoFar);
        List<HighlightSegment> selected = selectHighlights(selectable);
        List<HighlightSegment> decided = findDecided(selected, selectable);
        deliverTheNotDelivered(decided, eventsSoFar);
    }

    @Override
    public void matchFinished() {
        narrateEverythingHappened();
        announceChronicleFinished();
    }

    public void catchUpForPause() {
        narrateEverythingHappened();
        startSelectingAfterTheLastNarrated();
        announceChroniclePaused();
    }

    private void narrateEverythingHappened() {
        List<MatchEvent> allEvents = findHappenedSoFar();
        List<MatchEvent> selectable = findSelectable(allEvents);
        List<HighlightSegment> selected = selectHighlights(selectable);
        deliverTheNotDelivered(selected, allEvents);
    }

    private void rememberHappened(List<MatchEvent> newEvents) {
        happenedEvents.addAll(newEvents);
    }

    private List<MatchEvent> findHappenedSoFar() {
        return List.copyOf(happenedEvents);
    }

    private List<MatchEvent> findSelectable(List<MatchEvent> eventsSoFar) {
        return eventsSoFar.subList(selectionStart, eventsSoFar.size());
    }

    private List<HighlightSegment> selectHighlights(List<MatchEvent> events) {
        return selector.select(events);
    }

    private void startSelectingAfterTheLastNarrated() {
        selectionStart = lastNarratedPosition + 1;
    }

    private void announceChronicleFinished() {
        listener.chronicleFinished();
    }

    private void announceChroniclePaused() {
        listener.chroniclePaused();
    }

    private List<HighlightSegment> findDecided(List<HighlightSegment> segments, List<MatchEvent> selectable) {
        int lastDecidedPosition = selector.findLastDecidedPosition(selectable);
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
        List<HighlightSegment> notDelivered = dropAlreadyNarrated(decided);
        List<NarratedSegment> narrated = narrator.narrate(notDelivered, eventsSoFar, teamNames, openingSetups);
        lastNarratedPosition = selectionStart + countEvents(decided) - 1;
        narrated.forEach(listener::segmentNarrated);
    }

    private List<HighlightSegment> dropAlreadyNarrated(List<HighlightSegment> segments) {
        List<HighlightSegment> notNarrated = new ArrayList<>();
        int endPosition = selectionStart - 1;
        for (HighlightSegment segment : segments) {
            endPosition += segment.events().size();
            if (endPosition > lastNarratedPosition) {
                notNarrated.add(segment);
            }
        }
        return notNarrated;
    }

    private int countEvents(List<HighlightSegment> segments) {
        return segments.stream()
                .mapToInt(segment -> segment.events().size())
                .sum();
    }
}
