package org.tasktelemetry.matchsimulator.engine.event;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;

public record MatchEvent(
        int order,
        MatchTime time,
        EventType type,
        MatchSide side,
        ZoneRole zoneRole,
        PlayPhase phase,
        Participants participants,
        int durationSeconds,
        EventDetail detail) {

    public MatchEvent {
        Objects.requireNonNull(time, "event time must not be null");
        Objects.requireNonNull(type, "event type must not be null");
        Objects.requireNonNull(side, "event side must not be null");
        Objects.requireNonNull(zoneRole, "event zone role must not be null");
        Objects.requireNonNull(phase, "event phase must not be null");
        Objects.requireNonNull(participants, "event participants must not be null");
        Objects.requireNonNull(detail, "event detail must not be null");
        if (order < 1) {
            throw new IllegalArgumentException("event order starts from 1, was " + order);
        }
        if (durationSeconds < 0) {
            throw new IllegalArgumentException("event duration must not be negative, was " + durationSeconds);
        }
    }
}
