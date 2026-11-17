package org.tasktelemetry.matchsimulator.engine.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class EventTypeQuestionsTest {

    private static final Set<EventType> SHOTS = EnumSet.of(EventType.GOAL, EventType.SHOT_SAVED,
            EventType.SHOT_OFF_TARGET, EventType.SHOT_BLOCKED);
    private static final Set<EventType> WITHOUT_POSSESSION = EnumSet.of(EventType.INJURY, EventType.SUBSTITUTION,
            EventType.STYLE_CHANGED, EventType.PLAYER_MOVED);

    @Test
    void onlyTheFourOutcomesOfAShotAreShots() {
        for (EventType type : EventType.values()) {
            assertThat(type.isShot()).as(type.name()).isEqualTo(SHOTS.contains(type));
        }
    }

    @Test
    void injuriesAndChangesOfTheLineupAreTheOnlyEventsThatDoNotCountForPossession() {
        for (EventType type : EventType.values()) {
            assertThat(type.countsForPossession()).as(type.name()).isEqualTo(!WITHOUT_POSSESSION.contains(type));
        }
    }
}
