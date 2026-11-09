package org.tasktelemetry.matchsimulator.engine.resolution.stoppage;

import java.util.List;

import org.tasktelemetry.matchsimulator.engine.config.StoppageConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;

public final class StoppageTime {

    private final StoppageConfig config;

    public StoppageTime(StoppageConfig config) {
        this.config = config;
    }

    public int announcedMinutes(List<MatchEvent> events, MatchPeriod period) {
        List<MatchEvent> periodEvents = eventsOf(events, period);
        int lostSeconds = lostSecondsIn(periodEvents);
        int roundedUpMinutes = roundUpToWholeMinutes(lostSeconds);
        return keepWithinTheAllowedMinutes(roundedUpMinutes, period);
    }

    private int roundUpToWholeMinutes(int seconds) {
        return Math.ceilDiv(seconds, MatchPeriod.SECONDS_PER_MINUTE);
    }

    private int keepWithinTheAllowedMinutes(int minutes, MatchPeriod period) {
        int maximumMinutes = maximumMinutesIn(period);
        int cappedMinutes = Math.min(minutes, maximumMinutes);
        return Math.max(cappedMinutes, config.minimumMinutes());
    }

    private List<MatchEvent> eventsOf(List<MatchEvent> events, MatchPeriod period) {
        return events.stream()
                .filter(event -> event.time().period() == period)
                .toList();
    }

    private int lostSecondsIn(List<MatchEvent> periodEvents) {
        long countedEvents = periodEvents.stream()
                .filter(event -> costsFixedStoppage(event.type()))
                .count();
        int injurySeconds = periodEvents.stream()
                .filter(event -> event.type() == EventType.INJURY)
                .mapToInt(MatchEvent::durationSeconds)
                .sum();
        return (int) countedEvents * config.secondsPerEvent() + injurySeconds;
    }

    private boolean costsFixedStoppage(EventType type) {
        return type == EventType.GOAL || type == EventType.SUBSTITUTION || type.isCard();
    }

    private int maximumMinutesIn(MatchPeriod period) {
        return switch (period) {
            case FIRST_HALF -> config.maximumFirstHalfMinutes();
            case SECOND_HALF -> config.maximumSecondHalfMinutes();
            default -> throw new IllegalArgumentException("stoppage time belongs to a half, but the period is "
                    + period);
        };
    }
}
