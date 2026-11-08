package org.tasktelemetry.matchsimulator.engine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ShotProfileTest {

    @Test
    void aShotKeepsItsKindAndIsNotAPenalty() {
        ShotProfile profile = ShotProfile.ofShot(0.12, ShotKind.HEADER, ShotQuality.HALF_CHANCE);

        assertThat(profile.kind()).isEqualTo(ShotKind.HEADER);
        assertThat(profile.penalty()).isFalse();
    }

    @Test
    void aPenaltyIsAFootShotMarkedAsPenalty() {
        ShotProfile profile = ShotProfile.ofPenalty(0.79, ShotQuality.BIG_CHANCE);

        assertThat(profile.kind()).isEqualTo(ShotKind.FOOT);
        assertThat(profile.penalty()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0, -0.1, 1.2, Double.NaN})
    void rejectsExpectedGoalsOutsideTheOpenUnitInterval(double expectedGoals) {
        assertThatThrownBy(() -> ShotProfile.ofShot(expectedGoals, ShotKind.FOOT, ShotQuality.ATTEMPT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected goals must be strictly between 0.0 and 1.0");
    }

    @Test
    void rejectsMissingValues() {
        assertThatThrownBy(() -> ShotProfile.ofShot(0.1, null, ShotQuality.ATTEMPT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ShotProfile.ofShot(0.1, ShotKind.FOOT, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void theShotDetailsRejectMissingValues() {
        ShotProfile profile = ShotProfile.ofShot(0.1, ShotKind.FOOT, ShotQuality.ATTEMPT);

        assertThatThrownBy(() -> EventDetail.shot(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> EventDetail.save(null, SaveQuality.ROUTINE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> EventDetail.save(profile, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> EventDetail.goal(null, GoalContext.EQUALISER))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> EventDetail.goal(profile, null)).isInstanceOf(NullPointerException.class);
    }
}
