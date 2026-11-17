package org.tasktelemetry.matchsimulator.coach.reading;

import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.Player;

final class EventPlayers {

    Player primaryOf(MatchEvent event) {
        return event.participants().primary()
                .orElseThrow(() -> new IllegalStateException(event.type() + " event without a primary player"));
    }

    Player secondaryOf(MatchEvent event) {
        return event.participants().secondary()
                .orElseThrow(() -> new IllegalStateException(event.type() + " event without a secondary player"));
    }

    EventDetail.LineupChange lineupChangeOf(MatchEvent event) {
        if (event.detail() instanceof EventDetail.LineupChange change) {
            return change;
        }
        throw new IllegalStateException(event.type() + " event without the detail of a lineup change");
    }
}
