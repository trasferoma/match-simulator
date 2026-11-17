package org.tasktelemetry.matchsimulator.coach.reading;

import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class FatigueReader {

    private final EventPlayers eventPlayers = new EventPlayers();

    public FatigueReading advance(FatigueReading before, MatchEvent event) {
        return switch (event.type()) {
            case PLAYER_TIRED, PLAYER_RECOVERED -> changeBand(before, event);
            case INJURY, SECOND_YELLOW_CARD, RED_CARD -> forget(before, event);
            case SUBSTITUTION -> substitute(before, event);
            default -> before;
        };
    }

    private FatigueReading changeBand(FatigueReading before, MatchEvent event) {
        Player player = eventPlayers.primaryOf(event);
        FatigueBand band = fatigueDetailOf(event).band();
        return before.withBand(player, band);
    }

    private FatigueReading forget(FatigueReading before, MatchEvent event) {
        Player player = eventPlayers.primaryOf(event);
        return before.without(player);
    }

    private FatigueReading substitute(FatigueReading before, MatchEvent event) {
        Player leaving = eventPlayers.primaryOf(event);
        Player entering = eventPlayers.secondaryOf(event);
        FatigueReading withoutLeaving = before.without(leaving);
        FatigueReading withoutEntering = withoutLeaving.without(entering);
        Role enteringSector = eventPlayers.lineupChangeOf(event).sector();
        if (enteringSector != Role.FORWARD) {
            return withoutEntering;
        }
        PlayedTime enteredAt = PlayedTime.at(event.time());
        return withoutEntering.withForwardEnteredAt(entering, enteredAt);
    }

    private EventDetail.Fatigue fatigueDetailOf(MatchEvent event) {
        if (event.detail() instanceof EventDetail.Fatigue fatigue) {
            return fatigue;
        }
        throw new IllegalStateException(event.type() + " event without the detail of a fatigue band");
    }
}
