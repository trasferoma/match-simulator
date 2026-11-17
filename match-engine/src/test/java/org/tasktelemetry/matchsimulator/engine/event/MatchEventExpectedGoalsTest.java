package org.tasktelemetry.matchsimulator.engine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;

class MatchEventExpectedGoalsTest {

    private static final double TOLERANCE = 1e-9;

    private final MatchTime time = new MatchTime(MatchPeriod.FIRST_HALF, 90);
    private final BallPosition ball = new BallPosition(MatchSide.HOME, ZoneRole.ATTACKING_THIRD);
    private final ShotProfile profile = ShotProfile.ofShot(0.2, ShotKind.FOOT, ShotQuality.HALF_CHANCE);

    @Test
    void aShotASaveAndAGoalCarryTheExpectedGoalsOfTheirProfile() {
        MatchEvent shot = eventOf(EventType.SHOT_OFF_TARGET, EventDetail.shot(profile));
        MatchEvent save = eventOf(EventType.SHOT_SAVED, EventDetail.save(profile, SaveQuality.ROUTINE));
        MatchEvent goal = eventOf(EventType.GOAL, EventDetail.goal(profile, GoalContext.TAKES_THE_LEAD));

        assertThat(shot.expectedGoals()).isEqualTo(0.2, within(TOLERANCE));
        assertThat(save.expectedGoals()).isEqualTo(0.2, within(TOLERANCE));
        assertThat(goal.expectedGoals()).isEqualTo(0.2, within(TOLERANCE));
    }

    @Test
    void anEventWithoutAShotProfileHasNoExpectedGoals() {
        MatchEvent pass = eventOf(EventType.PASS_COMPLETED, EventDetail.none());

        assertThatThrownBy(pass::expectedGoals).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shot profile");
    }

    private MatchEvent eventOf(EventType type, EventDetail detail) {
        return new MatchEvent(1, time, type, MatchSide.HOME, ZoneRole.ATTACKING_THIRD, ball, PlayPhase.BUILD_UP,
                Participants.nobody(), 0, detail);
    }
}
