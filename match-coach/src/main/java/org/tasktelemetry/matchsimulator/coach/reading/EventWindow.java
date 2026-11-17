package org.tasktelemetry.matchsimulator.coach.reading;

import java.util.ArrayList;
import java.util.List;

import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;

public record EventWindow(List<MatchEvent> events) {

    private static final EventWindow EMPTY = new EventWindow(List.of());

    public EventWindow {
        events = List.copyOf(events);
    }

    public static EventWindow empty() {
        return EMPTY;
    }

    public EventWindow after(List<MatchEvent> newEvents, int retainedMinutes) {
        if (newEvents.isEmpty()) {
            return this;
        }
        List<MatchEvent> all = new ArrayList<>(events);
        all.addAll(newEvents);
        PlayedTime now = timeOfLast(all);
        int retainedSeconds = retainedMinutes * MatchPeriod.SECONDS_PER_MINUTE;
        int firstRetained = indexOfFirstWithin(all, now, retainedSeconds);
        List<MatchEvent> retained = all.subList(firstRetained, all.size());
        return new EventWindow(retained);
    }

    public List<MatchEvent> within(int minutes) {
        if (events.isEmpty()) {
            return events;
        }
        PlayedTime now = timeOfLast(events);
        int seconds = minutes * MatchPeriod.SECONDS_PER_MINUTE;
        int first = indexOfFirstWithin(events, now, seconds);
        return events.subList(first, events.size());
    }

    private int indexOfFirstWithin(List<MatchEvent> sorted, PlayedTime now, int seconds) {
        int first = sorted.size();
        while (first > 0 && isWithin(sorted.get(first - 1), now, seconds)) {
            first--;
        }
        return first;
    }

    private boolean isWithin(MatchEvent event, PlayedTime now, int seconds) {
        PlayedTime eventTime = PlayedTime.at(event.time());
        return now.secondsSince(eventTime) < seconds;
    }

    private PlayedTime timeOfLast(List<MatchEvent> sorted) {
        MatchEvent last = sorted.get(sorted.size() - 1);
        return PlayedTime.at(last.time());
    }
}
