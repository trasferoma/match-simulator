package org.tasktelemetry.matchsimulator.narration.highlights;

import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;

public sealed interface HighlightSegment {

    List<MatchEvent> events();

    record Frame(MatchEvent event) implements HighlightSegment {

        public Frame {
            Objects.requireNonNull(event, "frame event must not be null");
        }

        @Override
        public List<MatchEvent> events() {
            return List.of(event);
        }
    }

    record Stop(List<MatchEvent> events, boolean containsChance) implements HighlightSegment {

        public Stop {
            Objects.requireNonNull(events, "stop events must not be null");
            if (events.isEmpty()) {
                throw new IllegalArgumentException("a stop needs at least one event");
            }
            events = List.copyOf(events);
        }

        public MatchEvent firstEvent() {
            return events.get(0);
        }
    }
}
