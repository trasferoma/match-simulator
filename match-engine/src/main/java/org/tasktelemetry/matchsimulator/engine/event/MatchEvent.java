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
        BallPosition ball,
        PlayPhase phase,
        Participants participants,
        int durationSeconds,
        EventDetail detail) {

    public MatchEvent {
        Objects.requireNonNull(time, "event time must not be null");
        Objects.requireNonNull(type, "event type must not be null");
        Objects.requireNonNull(side, "event side must not be null");
        Objects.requireNonNull(zoneRole, "event zone role must not be null");
        Objects.requireNonNull(ball, "event ball position must not be null");
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

    public boolean isVoluntaryIntervention() {
        return switch (type) {
            case STYLE_CHANGED -> true;
            case SUBSTITUTION, PLAYER_MOVED -> hasVoluntaryReason();
            default -> false;
        };
    }

    public double expectedGoals() {
        return switch (detail) {
            case EventDetail.Shot shot -> shot.profile().expectedGoals();
            case EventDetail.Save save -> save.profile().expectedGoals();
            case EventDetail.Goal goal -> goal.profile().expectedGoals();
            default -> throw new IllegalStateException("a shot event must carry a shot profile, but its detail is "
                    + detail);
        };
    }

    private boolean hasVoluntaryReason() {
        return detail instanceof EventDetail.LineupChange change && !change.reason().isForced();
    }
}
