package org.tasktelemetry.matchsimulator.narration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.event.EventType;

class BallDepictionTest {

    @Test
    void theEventsPlayedWithTheBallStoppedAreTheInjuryTheMatchFlowAndTheSubstitutions() {
        Set<EventType> stoppedInZone = typesWith(BallDepiction.STOPPED_IN_EVENT_ZONE);

        assertThat(stoppedInZone).containsExactlyInAnyOrder(EventType.INJURY, EventType.STOPPAGE_ANNOUNCED,
                EventType.FIRST_HALF_END, EventType.MATCH_END, EventType.MATCH_SUSPENDED, EventType.SUBSTITUTION);
    }

    @Test
    void everyOtherEventIsPlayedWithTheBallInPlay() {
        Set<EventType> inPlay = typesWith(BallDepiction.IN_PLAY);

        assertThat(inPlay).contains(EventType.KICK_OFF, EventType.SECOND_HALF_START, EventType.KICK_OFF_AFTER_GOAL,
                EventType.PASS_COMPLETED, EventType.GOAL, EventType.SHOT_SAVED, EventType.CORNER_KICK,
                EventType.FREE_KICK, EventType.OFFSIDE_FREE_KICK, EventType.CROSS_FAILED, EventType.FOUL,
                EventType.YELLOW_CARD, EventType.SECOND_YELLOW_CARD, EventType.RED_CARD, EventType.OFFSIDE);
        assertThat(inPlay).hasSize(EventType.values().length - 6);
    }

    private Set<EventType> typesWith(BallDepiction depiction) {
        Set<EventType> types = EnumSet.noneOf(EventType.class);
        for (EventType type : EventType.values()) {
            if (BallDepiction.of(type) == depiction) {
                types.add(type);
            }
        }
        return types;
    }
}
