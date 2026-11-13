package org.tasktelemetry.matchsimulator.narration.highlights;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;

public record HighlightsConfig(
        Map<EventType, HighlightTreatment> treatments,
        Map<EventType, Set<ZoneRole>> alsoStopsInZones,
        int maxPreparationLines,
        int consequenceWindow,
        Duration framePause,
        Duration eventLinePause,
        Duration chanceLinePause,
        Duration afterGoalPause,
        Duration resumePause) {

    public HighlightsConfig {
        Objects.requireNonNull(treatments, "treatments must not be null");
        Objects.requireNonNull(alsoStopsInZones, "zones must not be null");
        requireTreatmentForEveryEventType(treatments);
        requireTreatmentsAllowedOnTheirTypes(treatments);
        requireZonesExactlyForLinkedTypes(treatments, alsoStopsInZones);
        requireNotNegative("max preparation lines", maxPreparationLines);
        requireNotNegative("consequence window", consequenceWindow);
        requireNotNegative("frame pause millis", framePause);
        requireNotNegative("event line pause millis", eventLinePause);
        requireNotNegative("chance line pause millis", chanceLinePause);
        requireNotNegative("after goal pause millis", afterGoalPause);
        requireNotNegative("resume pause millis", resumePause);
        treatments = immutableTreatments(treatments);
        alsoStopsInZones = immutableZones(alsoStopsInZones);
    }

    public HighlightTreatment treatmentOf(EventType type) {
        return treatments.get(type);
    }

    public Set<ZoneRole> alsoStopsInZones(EventType type) {
        return alsoStopsInZones.getOrDefault(type, Set.of());
    }

    private static void requireTreatmentForEveryEventType(Map<EventType, HighlightTreatment> treatments) {
        for (EventType type : EventType.values()) {
            if (treatments.get(type) == null) {
                throw new IllegalArgumentException("no treatment for event type " + type);
            }
        }
    }

    private static void requireTreatmentsAllowedOnTheirTypes(Map<EventType, HighlightTreatment> treatments) {
        for (EventType type : EventType.values()) {
            HighlightTreatment treatment = treatments.get(type);
            if (!treatment.isAllowedFor(type)) {
                throw new IllegalArgumentException("treatment " + treatment + " is not allowed for event type "
                        + type);
            }
        }
    }

    private static void requireZonesExactlyForLinkedTypes(Map<EventType, HighlightTreatment> treatments,
            Map<EventType, Set<ZoneRole>> zones) {
        for (EventType type : EventType.values()) {
            HighlightTreatment treatment = treatments.get(type);
            boolean isLinked = treatment == HighlightTreatment.ONE_LINE_IF_LINKED_TO_CHANCE;
            boolean hasZones = zones.containsKey(type);
            if (isLinked && !hasZones) {
                throw new IllegalArgumentException("event type " + type + " treated as " + treatment
                        + " needs its also-stops-in zones");
            }
            if (!isLinked && hasZones) {
                throw new IllegalArgumentException("event type " + type + " treated as " + treatment
                        + " does not accept zones, was " + zones.get(type));
            }
        }
    }

    private static void requireNotNegative(String name, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative, was " + value);
        }
    }

    private static void requireNotNegative(String name, Duration pause) {
        Objects.requireNonNull(pause, name + " must not be null");
        if (pause.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative, was " + pause.toMillis());
        }
    }

    private static Map<EventType, HighlightTreatment> immutableTreatments(
            Map<EventType, HighlightTreatment> treatments) {
        Map<EventType, HighlightTreatment> copy = new EnumMap<>(EventType.class);
        copy.putAll(treatments);
        return Collections.unmodifiableMap(copy);
    }

    private static Map<EventType, Set<ZoneRole>> immutableZones(Map<EventType, Set<ZoneRole>> zones) {
        Map<EventType, Set<ZoneRole>> copy = new EnumMap<>(EventType.class);
        for (Map.Entry<EventType, Set<ZoneRole>> entry : zones.entrySet()) {
            Set<ZoneRole> roles = EnumSet.noneOf(ZoneRole.class);
            roles.addAll(entry.getValue());
            Set<ZoneRole> immutableRoles = Collections.unmodifiableSet(roles);
            copy.put(entry.getKey(), immutableRoles);
        }
        return Collections.unmodifiableMap(copy);
    }
}
