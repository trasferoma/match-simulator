package org.tasktelemetry.matchsimulator.engine.resolution;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public record ActionOutcome(EventType type, Participants participants, MatchState next, int durationSeconds,
        EventDetail detail) {

    public ActionOutcome {
        Objects.requireNonNull(type, "outcome type must not be null");
        Objects.requireNonNull(participants, "outcome participants must not be null");
        Objects.requireNonNull(next, "outcome next state must not be null");
        Objects.requireNonNull(detail, "outcome detail must not be null");
        if (durationSeconds < 0) {
            throw new IllegalArgumentException("outcome duration must not be negative, was " + durationSeconds);
        }
    }

    public static ActionOutcome byPlayer(EventType type, Player player, MatchState next, int durationSeconds) {
        Participants participants = Participants.of(player);
        EventDetail detail = EventDetail.none();
        return new ActionOutcome(type, participants, next, durationSeconds, detail);
    }
}
