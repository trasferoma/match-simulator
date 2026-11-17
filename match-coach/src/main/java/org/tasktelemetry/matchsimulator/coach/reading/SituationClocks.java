package org.tasktelemetry.matchsimulator.coach.reading;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record SituationClocks(Map<Situation, SituationDuration> durations) {

    private static final SituationDuration NOT_HOLDING = SituationDuration.notHolding();

    public SituationClocks {
        Objects.requireNonNull(durations, "durations must not be null");
        Map<Situation, SituationDuration> complete = new EnumMap<>(Situation.class);
        for (Situation situation : Situation.values()) {
            SituationDuration known = durations.getOrDefault(situation, NOT_HOLDING);
            complete.put(situation, known);
        }
        durations = Collections.unmodifiableMap(complete);
    }

    public static SituationClocks idle() {
        Map<Situation, SituationDuration> nothingHolding = new EnumMap<>(Situation.class);
        return new SituationClocks(nothingHolding);
    }

    public SituationClocks after(Set<Situation> holdingNow, PlayedTime now) {
        Map<Situation, SituationDuration> updated = new EnumMap<>(Situation.class);
        for (Situation situation : Situation.values()) {
            boolean holds = holdingNow.contains(situation);
            SituationDuration updatedDuration = durations.get(situation).after(holds, now);
            updated.put(situation, updatedDuration);
        }
        return new SituationClocks(updated);
    }

    public boolean hasLastedAtLeast(Situation situation, double minutes, PlayedTime now) {
        SituationDuration duration = durations.get(situation);
        return duration.hasLastedAtLeast(minutes, now);
    }
}
