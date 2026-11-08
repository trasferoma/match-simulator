package org.tasktelemetry.matchsimulator.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SetPieceConfigTest {

    private static final DurationRange RANGE = new DurationRange(1, 2);

    @Test
    void defaultsCarryTheGoalKickDurationOfTheSpecification() {
        assertThat(SetPieceConfig.defaults().goalKickDuration()).isEqualTo(new DurationRange(10, 20));
    }

    @Test
    void defaultsCarryTheCornerDurationOfTheSpecification() {
        assertThat(SetPieceConfig.defaults().cornerDuration()).isEqualTo(new DurationRange(25, 45));
    }

    @Test
    void defaultsCarryTheFreeKickDurationOfTheSpecification() {
        assertThat(SetPieceConfig.defaults().freeKickDuration()).isEqualTo(new DurationRange(20, 40));
    }

    @Test
    void defaultsSendSomeFreeKicksToAShotAndSomeToACross() {
        assertThat(SetPieceConfig.defaults().directFreeKickShare()).isStrictlyBetween(0.0, 1.0);
    }

    @Test
    void rejectsAMissingGoalKickDuration() {
        assertThatThrownBy(() -> new SetPieceConfig(null, RANGE, RANGE, 0.5))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsAMissingCornerDuration() {
        assertThatThrownBy(() -> new SetPieceConfig(RANGE, null, RANGE, 0.5))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsAMissingFreeKickDuration() {
        assertThatThrownBy(() -> new SetPieceConfig(RANGE, RANGE, null, 0.5))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN})
    void rejectsADirectFreeKickShareOutsideZeroAndOne(double share) {
        assertThatThrownBy(() -> new SetPieceConfig(RANGE, RANGE, RANGE, share))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("direct free kick share must be between 0.0 and 1.0");
    }
}
