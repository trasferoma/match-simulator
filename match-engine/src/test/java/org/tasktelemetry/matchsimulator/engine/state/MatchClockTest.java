package org.tasktelemetry.matchsimulator.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MatchClockTest {

    private static final int REGULATION = 45 * 60;

    private final MatchClock firstHalf = MatchClock.beforeKickOff().inPeriod(MatchPeriod.FIRST_HALF);

    @Test
    void startsBeforeTheKickOffAtZero() {
        MatchClock clock = MatchClock.beforeKickOff();

        assertThat(clock.period()).isEqualTo(MatchPeriod.BEFORE_KICK_OFF);
        assertThat(clock.seconds()).isZero();
        assertThat(clock.isStoppageAnnounced()).isFalse();
    }

    @Test
    void enteringAPeriodResetsSecondsAndStoppage() {
        MatchClock announced = firstHalf.advancedBy(REGULATION).withAnnouncedStoppage(2);

        MatchClock secondHalf = announced.inPeriod(MatchPeriod.SECOND_HALF);

        assertThat(secondHalf).isEqualTo(new MatchClock(MatchPeriod.SECOND_HALF, 0, 0));
    }

    @Test
    void advancingAddsTheElapsedSeconds() {
        MatchClock clock = firstHalf.advancedBy(7).advancedBy(5);

        assertThat(clock.seconds()).isEqualTo(12);
    }

    @Test
    void regulationTimeIsReachedAtExactlyFortyFiveMinutes() {
        assertThat(firstHalf.advancedBy(REGULATION - 1).reachedRegulationTime()).isFalse();
        assertThat(firstHalf.advancedBy(REGULATION).reachedRegulationTime()).isTrue();
        assertThat(firstHalf.advancedBy(REGULATION + 30).reachedRegulationTime()).isTrue();
    }

    @Test
    void theHalfExpiresOnlyAfterTheAnnouncedStoppageHasBeenExceeded() {
        MatchClock announced = firstHalf.advancedBy(REGULATION).withAnnouncedStoppage(2);

        assertThat(announced.advancedBy(120).hasExpired()).isFalse();
        assertThat(announced.advancedBy(121).hasExpired()).isTrue();
    }

    @Test
    void aHalfWithoutAnnouncedStoppageNeverExpires() {
        MatchClock late = firstHalf.advancedBy(REGULATION * 2);

        assertThat(late.hasExpired()).isFalse();
    }

    @Test
    void theClockRunsOnlyInAHalf() {
        MatchClock beforeKickOff = MatchClock.beforeKickOff();

        assertThatThrownBy(() -> beforeKickOff.advancedBy(5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only in a half");
        assertThatThrownBy(() -> beforeKickOff.withAnnouncedStoppage(1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsANegativeElapsedTimeAndAnAnnouncementBelowOneMinute() {
        assertThatThrownBy(() -> firstHalf.advancedBy(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> firstHalf.withAnnouncedStoppage(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 1 minute");
    }

    @Test
    void periodsKnowWhichOnesAreCurrentlyPlayedAndWhichOnesEndTheMatch() {
        assertThat(MatchPeriod.FIRST_HALF.isPlayed()).isTrue();
        assertThat(MatchPeriod.SECOND_HALF.isPlayed()).isTrue();
        assertThat(MatchPeriod.HALF_TIME.isPlayed()).isFalse();
        assertThat(MatchPeriod.FINISHED.isOver()).isTrue();
        assertThat(MatchPeriod.SUSPENDED.isOver()).isTrue();
        assertThat(MatchPeriod.SECOND_HALF.isOver()).isFalse();
    }
}
