package org.tasktelemetry.matchsimulator.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LineupConfigTest {

    @Test
    void defaultsAllowFiveSubstitutionsAndWeakenEveryMissingMan() {
        LineupConfig defaults = LineupConfig.defaults();

        assertThat(defaults.maximumSubstitutions()).isEqualTo(5);
        assertThat(defaults.inferiorityMultiplier()).isStrictlyBetween(0.0, 1.0);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsALimitWithoutSubstitutions(int maximum) {
        assertThatThrownBy(() -> new LineupConfig(maximum, 0.9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum substitutions must be at least 1");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.1, 1.1, Double.NaN})
    void rejectsAnInferiorityMultiplierOutsideTheHalfOpenIntervalUpToOne(double multiplier) {
        assertThatThrownBy(() -> new LineupConfig(5, multiplier))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inferiority multiplier must be above 0.0 and at most 1.0");
    }

    @Test
    void aMultiplierOfOneIsTheNeutralValue() {
        assertThat(new LineupConfig(5, 1.0).inferiorityMultiplier()).isEqualTo(1.0);
    }
}
