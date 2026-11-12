package org.tasktelemetry.matchsimulator.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LineupConfigTest {

    private static final DurationRange SUBSTITUTION_DURATION = new DurationRange(30, 45);

    @Test
    void defaultsAllowFiveSubstitutionsAndWeakenEveryPlayerOutOfPosition() {
        LineupConfig defaults = LineupConfig.defaults();

        assertThat(defaults.maximumSubstitutions()).isEqualTo(5);
        assertThat(defaults.outOfPositionMultiplier()).isStrictlyBetween(0.0, 1.0);
    }

    @Test
    void defaultsTakeBetweenThirtyAndFortyFiveSecondsPerSubstitution() {
        assertThat(LineupConfig.defaults().substitutionDuration()).isEqualTo(new DurationRange(30, 45));
    }

    @Test
    void rejectsAMissingSubstitutionDuration() {
        assertThatThrownBy(() -> new LineupConfig(5, 0.9, null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsALimitWithoutSubstitutions(int maximum) {
        assertThatThrownBy(() -> new LineupConfig(maximum, 0.9, SUBSTITUTION_DURATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum substitutions must be at least 1");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.1, 1.1, Double.NaN})
    void rejectsAnOutOfPositionMultiplierOutsideTheHalfOpenIntervalUpToOne(double multiplier) {
        assertThatThrownBy(() -> new LineupConfig(5, multiplier, SUBSTITUTION_DURATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out-of-position multiplier must be above 0.0 and at most 1.0");
    }

    @Test
    void aMultiplierOfOneIsTheNeutralValue() {
        LineupConfig neutral = new LineupConfig(5, 1.0, SUBSTITUTION_DURATION);

        assertThat(neutral.outOfPositionMultiplier()).isEqualTo(1.0);
    }
}
