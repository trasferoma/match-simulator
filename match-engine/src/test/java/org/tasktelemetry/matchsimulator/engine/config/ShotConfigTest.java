package org.tasktelemetry.matchsimulator.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ShotConfigTest {

    private static final DurationRange RANGE = new DurationRange(1, 3);

    private final ShotConfig defaults = ShotConfig.defaults();

    @Test
    void defaultsCarryTheDurationsOfTheSpecification() {
        assertThat(defaults.shotDuration()).isEqualTo(new DurationRange(1, 3));
        assertThat(defaults.goalWithRestartDuration()).isEqualTo(new DurationRange(45, 75));
        assertThat(defaults.penaltyDuration()).isEqualTo(new DurationRange(60, 90));
    }

    @Test
    void defaultsLeaveRoomForSavesAndKeepTheThresholdsAscending() {
        assertThat(defaults.blockedShare() + defaults.offTargetShare()).isLessThan(1.0);
        assertThat(defaults.halfChanceThreshold()).isLessThan(defaults.bigChanceThreshold());
        assertThat(defaults.difficultSaveThreshold()).isLessThan(defaults.spectacularSaveThreshold());
    }

    @Test
    void rejectsMissingParameters() {
        assertThatThrownBy(() -> build(null, RANGE, RANGE, RANGE, 0.3, 0.3)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> build(defaults.expectedGoals(), null, RANGE, RANGE, 0.3, 0.3))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> build(defaults.expectedGoals(), RANGE, null, RANGE, 0.3, 0.3))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> build(defaults.expectedGoals(), RANGE, RANGE, null, 0.3, 0.3))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN})
    void rejectsASharesOutsideZeroAndOne(double share) {
        assertThatThrownBy(() -> build(defaults.expectedGoals(), RANGE, RANGE, RANGE, share, 0.3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blocked share must be between 0.0 and 1.0");
        assertThatThrownBy(() -> build(defaults.expectedGoals(), RANGE, RANGE, RANGE, 0.3, share))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("off-target share must be between 0.0 and 1.0");
    }

    @Test
    void rejectsBlockedAndOffTargetSharesThatLeaveNoRoomForSaves() {
        assertThatThrownBy(() -> build(defaults.expectedGoals(), RANGE, RANGE, RANGE, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must leave room for saves");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void rejectsANeutralGoalkeepingOutsideTheScale(int rating) {
        assertThatThrownBy(() -> withGoalkeeping(rating, 0.02))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("neutral goalkeeping must be between");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsAGoalkeepingSlopeThatIsNegativeOrNotFinite(double slope) {
        assertThatThrownBy(() -> withGoalkeeping(75, slope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("goalkeeping slope");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.5, 0.6, 1.0, Double.NaN})
    void rejectsChanceThresholdsThatAreNotAscending(double halfChance) {
        assertThatThrownBy(() -> withThresholds(halfChance, 0.5, 0.15, 0.35))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chance thresholds");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.35, 0.4, 1.0, Double.NaN})
    void rejectsSaveThresholdsThatAreNotAscending(double difficultSave) {
        assertThatThrownBy(() -> withThresholds(0.08, 0.25, difficultSave, 0.35))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("save thresholds");
    }

@Test
void defaultsCarryTheCornerSharesAndLeaveRoomForTheDefenceToRecoverABlockedShot() {
    assertThat(defaults.blockedToCornerShare()).isStrictlyBetween(0.0, 1.0);
    assertThat(defaults.savedToCornerShare()).isStrictlyBetween(0.0, 1.0);
    assertThat(defaults.blockedToCornerShare() + defaults.blockedRecoveredByAttackShare()).isLessThan(1.0);
}

@ParameterizedTest
@ValueSource(doubles = {-0.1, 1.1, Double.NaN})
void rejectsACornerShareOutsideZeroAndOne(double share) {
    assertThatThrownBy(() -> withCornerShares(share, 0.3))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blocked-to-corner share must be between 0.0 and 1.0");
    assertThatThrownBy(() -> withCornerShares(0.25, share))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("saved-to-corner share must be between 0.0 and 1.0");
}

@Test
void rejectsBlockedSharesThatLeaveNoRoomForTheDefenceToRecover() {
    assertThatThrownBy(() -> withCornerShares(0.7, 0.3))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not add up to more than 1.0");
}

private ShotConfig withCornerShares(double blockedToCornerShare, double savedToCornerShare) {
    return new ShotConfig(defaults.expectedGoals(), 75, 0.02, 0.3, 0.3, 0.4, blockedToCornerShare,
            savedToCornerShare, 0.08, 0.25, 0.15, 0.35, RANGE, RANGE, RANGE);
}

private ShotConfig build(ExpectedGoalsConfig expectedGoals, DurationRange shot, DurationRange goal,
            DurationRange penalty, double blockedShare, double offTargetShare) {
        return new ShotConfig(expectedGoals, 75, 0.02, blockedShare, offTargetShare, 0.4, 0.25, 0.3, 0.08, 0.25, 0.15,
                0.35, shot, goal, penalty);
    }

    private ShotConfig withGoalkeeping(int neutralGoalkeeping, double goalkeepingSlope) {
        return new ShotConfig(defaults.expectedGoals(), neutralGoalkeeping, goalkeepingSlope, 0.3, 0.3, 0.4, 0.25, 0.3,
                0.08, 0.25, 0.15, 0.35, RANGE, RANGE, RANGE);
    }

    private ShotConfig withThresholds(double halfChance, double bigChance, double difficultSave,
            double spectacularSave) {
        return new ShotConfig(defaults.expectedGoals(), 75, 0.02, 0.3, 0.3, 0.4, 0.25, 0.3, halfChance, bigChance,
                difficultSave, spectacularSave, RANGE, RANGE, RANGE);
    }
}
