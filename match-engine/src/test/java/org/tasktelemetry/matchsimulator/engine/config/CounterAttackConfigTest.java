package org.tasktelemetry.matchsimulator.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CounterAttackConfigTest {

    private static final DurationRange RANGE = new DurationRange(1, 3);

    @Test
    void defaultsCarryTheCounterAttackDurationOfTheSpecification() {
        assertThat(CounterAttackConfig.defaults().advanceDuration()).isEqualTo(new DurationRange(2, 6));
    }

    @Test
    void defaultsNeverSwitchTheCounterAttackOff() {
        assertThat(CounterAttackConfig.defaults().startProbability()).isStrictlyBetween(0.0, 1.0);
    }

    @Test
    void rejectsAMissingDuration() {
        assertThatThrownBy(() -> new CounterAttackConfig(0.1, null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0, -0.1, 1.1, Double.NaN})
    void rejectsAStartProbabilityOutsideTheOpenIntervalBetweenZeroAndOne(double probability) {
        assertThatThrownBy(() -> new CounterAttackConfig(probability, RANGE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("counter-attack start probability must be strictly between 0.0 and 1.0");
    }
}
