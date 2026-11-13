package org.tasktelemetry.matchsimulator.narration.highlights;

import java.util.EnumSet;
import java.util.Set;

import org.tasktelemetry.matchsimulator.engine.event.EventType;

public enum HighlightTreatment {
    CHANCE,
    ONE_LINE,
    ONE_LINE_IF_LINKED_TO_CHANCE,
    ANIMATION_ONLY;

    private static final Set<EventType> CHANCE_TYPES = EnumSet.of(EventType.GOAL, EventType.SHOT_SAVED,
            EventType.SHOT_OFF_TARGET, EventType.SHOT_BLOCKED);
    private static final Set<EventType> LINKABLE_TYPES = EnumSet.of(EventType.CORNER_KICK, EventType.OFFSIDE);

    public boolean isAllowedFor(EventType type) {
        return switch (this) {
            case CHANCE -> CHANCE_TYPES.contains(type);
            case ONE_LINE_IF_LINKED_TO_CHANCE -> LINKABLE_TYPES.contains(type);
            case ONE_LINE, ANIMATION_ONLY -> true;
        };
    }
}
