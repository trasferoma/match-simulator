package org.tasktelemetry.matchsimulator.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LastStyleChangeTest {

    private static final int HOLD_SECONDS = 900;
    private static final PlayedTime CHANGE_TIME = new PlayedTime(3600);

    private final LastStyleChange offensive = LastStyleChange.at(CHANGE_TIME, 1);

    @Test
    void anOppositeChangeInsideTheHoldIsAReversal() {
        assertThat(offensive.isReversedBy(-1, HOLD_SECONDS, new PlayedTime(3600 + 899))).isTrue();
    }

    @Test
    void anOppositeChangeExactlyAtTheEndOfTheHoldIsNotAReversal() {
        assertThat(offensive.isReversedBy(-1, HOLD_SECONDS, new PlayedTime(3600 + 900))).isFalse();
    }

    @Test
    void aChangeInTheSameDirectionIsNeverAReversal() {
        assertThat(offensive.isReversedBy(1, HOLD_SECONDS, new PlayedTime(3601))).isFalse();
    }

    @Test
    void withoutAPreviousStyleChangeNothingIsAReversal() {
        assertThat(LastStyleChange.never().isReversedBy(-1, HOLD_SECONDS, new PlayedTime(10))).isFalse();
    }
}
