package org.tasktelemetry.matchsimulator.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.ScenarioEvents;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;

class SituationDurationTest {

    private static final double FOUR_MINUTES = 4.0;

    @Test
    void aSituationThatNeverHeldHasNotLastedAnyTime() {
        SituationDuration duration = SituationDuration.notHolding();

        assertThat(duration.hasLastedAtLeast(FOUR_MINUTES, playedAt(90, 0))).isFalse();
    }

    @Test
    void theDurationCountsFromTheFirstUpdateInWhichTheSituationHolds() {
        SituationDuration first = SituationDuration.notHolding().after(true, playedAt(60, 0));
        SituationDuration second = first.after(true, playedAt(62, 0));

        assertThat(second.since()).isEqualTo(playedAt(60, 0));
        assertThat(second.hasLastedAtLeast(FOUR_MINUTES, playedAt(63, 59))).isFalse();
    }

    @Test
    void theSituationHasLastedWhenTheDurationIsAtLeastTheDelayExactlyIncluded() {
        SituationDuration duration = SituationDuration.notHolding().after(true, playedAt(60, 0));

        assertThat(duration.hasLastedAtLeast(FOUR_MINUTES, playedAt(64, 0))).isTrue();
        assertThat(duration.hasLastedAtLeast(FOUR_MINUTES, playedAt(63, 59))).isFalse();
    }

    @Test
    void whenTheSituationStopsHoldingTheDurationRestartsFromZeroWhenItHoldsAgain() {
        SituationDuration holding = SituationDuration.notHolding().after(true, playedAt(60, 0));
        SituationDuration interrupted = holding.after(false, playedAt(62, 0));
        SituationDuration again = interrupted.after(true, playedAt(66, 0));

        assertThat(interrupted.holding()).isFalse();
        assertThat(again.since()).isEqualTo(playedAt(66, 0));
        assertThat(again.hasLastedAtLeast(FOUR_MINUTES, playedAt(69, 0))).isFalse();
        assertThat(again.hasLastedAtLeast(FOUR_MINUTES, playedAt(70, 0))).isTrue();
    }

    @Test
    void theMinutesOfTheDelayMayBeFractional() {
        SituationDuration duration = SituationDuration.notHolding().after(true, playedAt(60, 0));

        assertThat(duration.hasLastedAtLeast(2.5, playedAt(62, 30))).isTrue();
        assertThat(duration.hasLastedAtLeast(2.5, playedAt(62, 29))).isFalse();
    }

    @Test
    void playedTimeAddsTheFirstHalfToTheSecondAndIgnoresTheFirstHalfStoppage() {
        PlayedTime atFifty = PlayedTime.at(new MatchTime(MatchPeriod.SECOND_HALF, 5 * 60));
        PlayedTime inStoppage = PlayedTime.at(new MatchTime(MatchPeriod.FIRST_HALF, 46 * 60));

        assertThat(atFifty.seconds()).isEqualTo(50 * 60);
        assertThat(inStoppage.seconds()).isEqualTo(45 * 60);
        assertThat(atFifty.secondsSince(inStoppage)).isEqualTo(5 * 60);
    }

    @Test
    void playedTimeRejectsANegativeValue() {
        assertThatThrownBy(() -> new PlayedTime(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anInterventionIsWithinThePauseUntilTheSecondsOfThePauseHavePassed() {
        LastIntervention last = LastIntervention.at(playedAt(70, 0));

        assertThat(last.isWithin(180, playedAt(72, 59))).isTrue();
        assertThat(last.isWithin(180, playedAt(73, 0))).isFalse();
        assertThat(LastIntervention.never().isWithin(180, playedAt(70, 0))).isFalse();
    }

    private PlayedTime playedAt(int minute, int second) {
        return PlayedTime.at(ScenarioEvents.timeAt(minute, second));
    }
}
