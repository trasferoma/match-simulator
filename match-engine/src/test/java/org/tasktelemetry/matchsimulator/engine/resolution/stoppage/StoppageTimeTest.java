package org.tasktelemetry.matchsimulator.engine.resolution.stoppage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.config.StoppageConfig;
import org.tasktelemetry.matchsimulator.engine.event.BallPosition;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;

class StoppageTimeTest {

    private static final int FIRST_HALF_MAXIMUM = 5;
    private static final int SECOND_HALF_MAXIMUM = 8;

    private final StoppageConfig config = StoppageConfig.defaults();
    private final StoppageTime stoppage = new StoppageTime(config);
    private final List<MatchEvent> events = new ArrayList<>();

    @Test
    void withoutEventsTheStoppageIsTheMinimumOfOneMinute() {
        assertThat(stoppage.announcedMinutes(events, MatchPeriod.FIRST_HALF)).isEqualTo(1);
        assertThat(stoppage.announcedMinutes(events, MatchPeriod.SECOND_HALF)).isEqualTo(1);
    }

    @Test
    void twoGoalsCostSixtySecondsAndStayAtOneMinute() {
        add(EventType.GOAL, 0);
        add(EventType.GOAL, 0);

        assertThat(stoppage.announcedMinutes(events, MatchPeriod.FIRST_HALF)).isEqualTo(1);
    }

    @Test
    void twoGoalsAndAYellowCardCostNinetySecondsAndRoundUpToTwoMinutes() {
        add(EventType.GOAL, 0);
        add(EventType.GOAL, 0);
        add(EventType.YELLOW_CARD, 0);

        assertThat(stoppage.announcedMinutes(events, MatchPeriod.FIRST_HALF)).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {61, 90, 119, 120})
    void aSingleInjuryBetweenSixtyOneAndOneHundredTwentySecondsRoundsUpToTwoMinutes(int seconds) {
        add(EventType.INJURY, seconds);

        assertThat(stoppage.announcedMinutes(events, MatchPeriod.FIRST_HALF)).isEqualTo(2);
    }

    @Test
    void anInjuryOfExactlyOneMinuteStaysAtOneMinute() {
        add(EventType.INJURY, 60);

        assertThat(stoppage.announcedMinutes(events, MatchPeriod.FIRST_HALF)).isEqualTo(1);
    }

    @Test
    void oneSecondOverTwoMinutesRoundsUpToThree() {
        add(EventType.INJURY, 121);

        assertThat(stoppage.announcedMinutes(events, MatchPeriod.FIRST_HALF)).isEqualTo(3);
    }

    @Test
    void anInjuryAndTheSubstitutionItCausesAddTheirTimes() {
        add(EventType.INJURY, 100);
        add(EventType.SUBSTITUTION, 40);

        assertThat(stoppage.announcedMinutes(events, MatchPeriod.FIRST_HALF)).isEqualTo(3);
    }

    @Test
    void theDurationOfASubstitutionIsNotAddedToTheFixedCostOfTheChange() {
        add(EventType.SUBSTITUTION, 45);
        add(EventType.SUBSTITUTION, 45);

        assertThat(stoppage.announcedMinutes(events, MatchPeriod.FIRST_HALF)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = EventType.class,
            names = {"GOAL", "SUBSTITUTION", "YELLOW_CARD", "SECOND_YELLOW_CARD", "RED_CARD"})
    void everyGoalSubstitutionAndCardCostsThirtySeconds(EventType type) {
        for (int count = 0; count < 3; count++) {
            add(type, 0);
        }

        assertThat(stoppage.announcedMinutes(events, MatchPeriod.FIRST_HALF)).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(value = EventType.class, names = {"FOUL", "OFFSIDE", "CORNER_KICK", "SHOT_SAVED", "KICK_OFF_AFTER_GOAL",
            "STOPPAGE_ANNOUNCED", "PASS_COMPLETED", "MATCH_SUSPENDED"})
    void otherEventsCostNothing(EventType type) {
        for (int count = 0; count < 10; count++) {
            add(type, 0);
        }

        assertThat(stoppage.announcedMinutes(events, MatchPeriod.FIRST_HALF)).isEqualTo(1);
    }

    @Test
    void theFirstHalfIsCappedAtFiveMinutesAndTheSecondAtEight() {
        for (int count = 0; count < 40; count++) {
            add(EventType.GOAL, MatchPeriod.FIRST_HALF);
            add(EventType.GOAL, MatchPeriod.SECOND_HALF);
        }

        assertThat(stoppage.announcedMinutes(events, MatchPeriod.FIRST_HALF)).isEqualTo(FIRST_HALF_MAXIMUM);
        assertThat(stoppage.announcedMinutes(events, MatchPeriod.SECOND_HALF)).isEqualTo(SECOND_HALF_MAXIMUM);
    }

    @Test
    void justBelowTheMaximumTheMinutesAreNotCapped() {
        for (int count = 0; count < 14; count++) {
            add(EventType.GOAL, MatchPeriod.SECOND_HALF);
        }

        assertThat(stoppage.announcedMinutes(events, MatchPeriod.SECOND_HALF)).isEqualTo(7);
    }

    @Test
    void theSecondHalfExceedsTheCapOfTheFirstHalf() {
        for (int count = 0; count < 20; count++) {
            add(EventType.GOAL, MatchPeriod.SECOND_HALF);
        }

        assertThat(stoppage.announcedMinutes(events, MatchPeriod.SECOND_HALF)).isGreaterThan(FIRST_HALF_MAXIMUM);
    }

    @Test
    void onlyTheEventsOfTheHalfBeingAnnouncedCount() {
        for (int count = 0; count < 6; count++) {
            add(EventType.GOAL, MatchPeriod.FIRST_HALF);
        }
        add(EventType.GOAL, MatchPeriod.SECOND_HALF);

        assertThat(stoppage.announcedMinutes(events, MatchPeriod.FIRST_HALF)).isEqualTo(3);
        assertThat(stoppage.announcedMinutes(events, MatchPeriod.SECOND_HALF)).isEqualTo(1);
    }

    @Test
    void theMinutesFollowTheConfiguration() {
        StoppageConfig custom = new StoppageConfig(2, 60, 3, 4);
        StoppageTime customStoppage = new StoppageTime(custom);

        assertThat(customStoppage.announcedMinutes(events, MatchPeriod.FIRST_HALF)).isEqualTo(2);
        add(EventType.GOAL, 0);
        add(EventType.GOAL, 0);
        add(EventType.GOAL, 0);
        add(EventType.GOAL, 0);
        assertThat(customStoppage.announcedMinutes(events, MatchPeriod.FIRST_HALF)).isEqualTo(3);
        assertThat(customStoppage.announcedMinutes(events, MatchPeriod.SECOND_HALF)).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(value = MatchPeriod.class, names = {"BEFORE_KICK_OFF", "HALF_TIME", "FINISHED", "SUSPENDED"})
    void aPeriodThatIsNotAHalfHasNoStoppage(MatchPeriod period) {
        assertThatThrownBy(() -> stoppage.announcedMinutes(events, period))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stoppage time belongs to a half");
    }

    private void add(EventType type, int durationSeconds) {
        add(type, MatchPeriod.FIRST_HALF, durationSeconds);
    }

    private void add(EventType type, MatchPeriod period) {
        add(type, period, 0);
    }

    private void add(EventType type, MatchPeriod period, int durationSeconds) {
        MatchTime time = new MatchTime(period, 100);
        BallPosition ball = new BallPosition(MatchSide.HOME, ZoneRole.MIDFIELD);
        MatchEvent event = new MatchEvent(events.size() + 1, time, type, MatchSide.HOME, ZoneRole.MIDFIELD, ball,
                PlayPhase.BUILD_UP, Participants.nobody(), durationSeconds, EventDetail.none());
        events.add(event);
    }
}
