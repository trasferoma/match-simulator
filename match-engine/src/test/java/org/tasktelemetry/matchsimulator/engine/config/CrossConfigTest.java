package org.tasktelemetry.matchsimulator.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CrossConfigTest {

    private static final DurationRange RANGE = new DurationRange(1, 3);

    private final CrossConfig defaults = CrossConfig.defaults();

    @Test
    void defaultsCarryTheCrossDurationOfTheSpecification() {
        assertThat(defaults.crossDuration()).isEqualTo(new DurationRange(3, 6));
    }

    @Test
    void defaultsNeverSwitchTheCrossOff() {
        assertThat(defaults.crossProbability()).isStrictlyBetween(0.0, 1.0);
        assertThat(defaults.deflectedToCornerShare()).isStrictlyBetween(0.0, 1.0);
        assertThat(defaults.clearedToCornerShare()).isStrictlyBetween(0.0, 1.0);
    }

    @Test
    void rejectsMissingDurations() {
        assertThatThrownBy(() -> new CrossConfig(0.2, 0.3, 0.3, null, RANGE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CrossConfig(0.2, 0.3, 0.3, RANGE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0, -0.1, 1.1, Double.NaN})
    void rejectsACrossProbabilityOutsideTheOpenIntervalBetweenZeroAndOne(double probability) {
        assertThatThrownBy(() -> new CrossConfig(probability, 0.3, 0.3, RANGE, RANGE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cross probability must be strictly between 0.0 and 1.0");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN})
    void rejectsADeflectedToCornerShareOutsideZeroAndOne(double share) {
        assertThatThrownBy(() -> new CrossConfig(0.2, share, 0.3, RANGE, RANGE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deflected-to-corner share must be between 0.0 and 1.0");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN})
    void rejectsAClearedToCornerShareOutsideZeroAndOne(double share) {
        assertThatThrownBy(() -> new CrossConfig(0.2, 0.3, share, RANGE, RANGE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cleared-to-corner share must be between 0.0 and 1.0");
    }
}
