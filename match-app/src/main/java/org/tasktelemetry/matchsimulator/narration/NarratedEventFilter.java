package org.tasktelemetry.matchsimulator.narration;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;

public final class NarratedEventFilter {

    private static final Set<EventType> MATCH_FLOW_TYPES = EnumSet.of(EventType.KICK_OFF,
            EventType.STOPPAGE_ANNOUNCED, EventType.FIRST_HALF_END, EventType.SECOND_HALF_START,
            EventType.MATCH_END, EventType.MATCH_SUSPENDED);
    private static final Set<EventType> SHOT_TYPES = EnumSet.of(EventType.GOAL, EventType.SHOT_SAVED,
            EventType.SHOT_OFF_TARGET, EventType.SHOT_BLOCKED);
    private static final Set<EventType> INCIDENT_TYPES = EnumSet.of(EventType.OFFSIDE, EventType.INJURY,
            EventType.SUBSTITUTION);
    private static final Set<EventType> FREE_KICK_TYPES = EnumSet.of(EventType.FREE_KICK,
            EventType.FREE_KICK_CROSS);
    private static final Set<EventType> PLAYED_BY_THE_OPPOSING_SIDE_TYPES = EnumSet.of(EventType.INTERCEPTION,
            EventType.TACKLE, EventType.FOUL, EventType.CLEARANCE);
    private static final int MAX_EVENTS_PREPARING_A_SHOT = 2;

    public List<MatchEvent> select(List<MatchEvent> events, ChronicleMode mode) {
        return switch (mode) {
            case FULL -> List.copyOf(events);
            case DEFAULT -> selectDefaultChronicle(events);
        };
    }

    private List<MatchEvent> selectDefaultChronicle(List<MatchEvent> events) {
        Set<Integer> preparingShots = findEventsPreparingShots(events);
        return IntStream.range(0, events.size())
                .filter(index -> isNarratedOnItsOwn(events, index) || preparingShots.contains(index))
                .mapToObj(events::get)
                .toList();
    }

    private Set<Integer> findEventsPreparingShots(List<MatchEvent> events) {
        return IntStream.range(0, events.size())
                .filter(index -> isShot(events, index))
                .boxed()
                .flatMap(shotIndex -> findEventsPreparing(events, shotIndex).stream())
                .collect(Collectors.toSet());
    }

    private List<Integer> findEventsPreparing(List<MatchEvent> events, int shotIndex) {
        MatchSide shootingSide = actingSideAt(events, shotIndex);
        return IntStream.rangeClosed(1, MAX_EVENTS_PREPARING_A_SHOT)
                .map(distance -> shotIndex - distance)
                .takeWhile(index -> index >= 0 && actingSideAt(events, index) == shootingSide)
                .boxed()
                .toList();
    }

    private boolean isShot(List<MatchEvent> events, int index) {
        MatchEvent event = events.get(index);
        return SHOT_TYPES.contains(event.type());
    }

    private MatchSide actingSideAt(List<MatchEvent> events, int index) {
        MatchEvent event = events.get(index);
        return actingSideOf(event);
    }

    private MatchSide actingSideOf(MatchEvent event) {
        if (PLAYED_BY_THE_OPPOSING_SIDE_TYPES.contains(event.type())) {
            return event.side().opposite();
        }
        return event.side();
    }

    private boolean isNarratedOnItsOwn(List<MatchEvent> events, int index) {
        MatchEvent event = events.get(index);
        EventType type = event.type();
        boolean isMatchFlow = MATCH_FLOW_TYPES.contains(type);
        boolean isShot = SHOT_TYPES.contains(type);
        boolean isIncident = INCIDENT_TYPES.contains(type) || type.isCard();
        return isMatchFlow || isShot || isIncident || isDangerousSetPiece(event)
                || isFoulWithConsequences(events, index);
    }

    private boolean isDangerousSetPiece(MatchEvent event) {
        if (event.type() == EventType.CORNER_KICK) {
            return true;
        }
        boolean isFreeKick = FREE_KICK_TYPES.contains(event.type());
        return isFreeKick && event.zoneRole() == ZoneRole.ATTACKING_THIRD;
    }

    private boolean isFoulWithConsequences(List<MatchEvent> events, int index) {
        MatchEvent event = events.get(index);
        if (event.type() != EventType.FOUL) {
            return false;
        }
        return awardsPenalty(event) || isFollowedByCardForTheSamePlayer(events, index);
    }

    private boolean awardsPenalty(MatchEvent foul) {
        return foul.detail() instanceof EventDetail.Foul detail && detail.penalty();
    }

    private boolean isFollowedByCardForTheSamePlayer(List<MatchEvent> events, int foulIndex) {
        int nextIndex = foulIndex + 1;
        if (nextIndex >= events.size()) {
            return false;
        }
        MatchEvent foul = events.get(foulIndex);
        MatchEvent next = events.get(nextIndex);
        boolean isCard = next.type().isCard();
        boolean isForTheFouler = next.participants().primary().equals(foul.participants().primary());
        return isCard && isForTheFouler;
    }
}
