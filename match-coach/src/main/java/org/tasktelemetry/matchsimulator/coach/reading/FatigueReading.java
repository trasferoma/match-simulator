package org.tasktelemetry.matchsimulator.coach.reading;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public record FatigueReading(Map<Player, FatigueBand> bands, Map<Player, PlayedTime> forwardEntries) {

    private static final FatigueReading NOBODY_TIRED = nobodyTired();

    public FatigueReading {
        Objects.requireNonNull(bands, "bands must not be null");
        Objects.requireNonNull(forwardEntries, "forward entries must not be null");
        Map<Player, FatigueBand> bandsCopy = new LinkedHashMap<>(bands);
        bands = Collections.unmodifiableMap(bandsCopy);
        Map<Player, PlayedTime> entriesCopy = new LinkedHashMap<>(forwardEntries);
        forwardEntries = Collections.unmodifiableMap(entriesCopy);
    }

    public static FatigueReading none() {
        return NOBODY_TIRED;
    }

    private static FatigueReading nobodyTired() {
        Map<Player, FatigueBand> noBands = new LinkedHashMap<>();
        Map<Player, PlayedTime> noEntries = new LinkedHashMap<>();
        return new FatigueReading(noBands, noEntries);
    }

    public FatigueBand bandOf(Player player) {
        return bands.getOrDefault(player, FatigueBand.FRESH);
    }

    public List<Player> forwardsEnteredWithin(PlayedTime now, int minutes) {
        int seconds = minutes * MatchPeriod.SECONDS_PER_MINUTE;
        return forwardEntries.entrySet().stream()
                .filter(entry -> now.secondsSince(entry.getValue()) < seconds)
                .map(Map.Entry::getKey)
                .toList();
    }

    public FatigueReading withBand(Player player, FatigueBand band) {
        Map<Player, FatigueBand> updated = new LinkedHashMap<>(bands);
        updated.put(player, band);
        return new FatigueReading(updated, forwardEntries);
    }

    public FatigueReading withForwardEnteredAt(Player player, PlayedTime time) {
        Map<Player, PlayedTime> updated = new LinkedHashMap<>(forwardEntries);
        updated.put(player, time);
        return new FatigueReading(bands, updated);
    }

    public FatigueReading without(Player player) {
        Map<Player, FatigueBand> remainingBands = new LinkedHashMap<>(bands);
        remainingBands.remove(player);
        Map<Player, PlayedTime> remainingEntries = new LinkedHashMap<>(forwardEntries);
        remainingEntries.remove(player);
        return new FatigueReading(remainingBands, remainingEntries);
    }
}
