package org.tasktelemetry.matchsimulator.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

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
    void rejectsAMissingGoalKickDuration() {
        assertThatThrownBy(() -> new SetPieceConfig(null, RANGE)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsAMissingCornerDuration() {
        assertThatThrownBy(() -> new SetPieceConfig(RANGE, null)).isInstanceOf(NullPointerException.class);
    }
}
