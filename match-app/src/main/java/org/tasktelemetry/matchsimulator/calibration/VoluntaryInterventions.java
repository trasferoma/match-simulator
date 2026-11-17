package org.tasktelemetry.matchsimulator.calibration;

import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;

public record VoluntaryInterventions(int styleChanges, int substitutions, int moves) {

    public VoluntaryInterventions {
        requireNotNegative("style changes", styleChanges);
        requireNotNegative("substitutions", substitutions);
        requireNotNegative("moves", moves);
    }

    public static VoluntaryInterventions none() {
        return new VoluntaryInterventions(0, 0, 0);
    }

    public static VoluntaryInterventions from(List<MatchEvent> events) {
        Objects.requireNonNull(events, "events must not be null");
        List<MatchEvent> voluntary = events.stream().filter(MatchEvent::isVoluntaryIntervention).toList();
        int styleChanges = countOfType(voluntary, EventType.STYLE_CHANGED);
        int substitutions = countOfType(voluntary, EventType.SUBSTITUTION);
        int moves = countOfType(voluntary, EventType.PLAYER_MOVED);
        return new VoluntaryInterventions(styleChanges, substitutions, moves);
    }

    public int total() {
        return styleChanges + substitutions + moves;
    }

    private static int countOfType(List<MatchEvent> events, EventType type) {
        return (int) events.stream().filter(event -> event.type() == type).count();
    }

    private static void requireNotNegative(String name, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative, was " + value);
        }
    }
}
