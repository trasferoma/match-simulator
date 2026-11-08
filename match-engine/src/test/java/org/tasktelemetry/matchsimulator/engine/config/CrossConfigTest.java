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
    void defaultsCarryTheCrossDurationOfTheSpecificationAndANeutralStyleMultiplier() {
        assertThat(defaults.crossDuration()).isEqualTo(new DurationRange(3, 6));
        assertThat(defaults.styleMultiplier()).isEqualTo(1.0);
    }

    @Test
    void defaultsNeverSwitchTheCrossOff() {
        assertThat(defaults.crossProbability()).isStrictlyBetween(0.0, 1.0);
        assertThat(defaults.crossDifficulty()).isPositive();
        assertThat(defaults.clearedToCornerShare()).isStrictlyBetween(0.0, 1.0);
    }

    @Test
    void rejectsMissingDurations() {
        assertThatThrownBy(() -> new CrossConfig(0.2, 1.0, 150.0, 0.3, null, RANGE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CrossConfig(0.2, 1.0, 150.0, 0.3, RANGE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0, -0.1, 1.1, Double.NaN})
    void rejectsACrossProbabilityOutsideTheOpenIntervalBetweenZeroAndOne(double probability) {
        assertThatThrownBy(() -> new CrossConfig(probability, 1.0, 150.0, 0.3, RANGE, RANGE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cross probability must be strictly between 0.0 and 1.0");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsAStyleMultiplierThatIsNotPositiveAndFinite(double multiplier) {
        assertThatThrownBy(() -> new CrossConfig(0.2, multiplier, 150.0, 0.3, RANGE, RANGE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cross style multiplier must be positive and finite");
    }

    @Test
    void rejectsAStyleMultiplierThatPushesTheProbabilityAboveOne() {
        assertThatThrownBy(() -> new CrossConfig(0.5, 2.5, 150.0, 0.3, RANGE, RANGE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed 1.0");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsACrossDifficultyThatIsNotPositiveAndFinite(double difficulty) {
        assertThatThrownBy(() -> new CrossConfig(0.2, 1.0, difficulty, 0.3, RANGE, RANGE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cross difficulty must be positive and finite");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN})
    void rejectsAClearedToCornerShareOutsideZeroAndOne(double share) {
        assertThatThrownBy(() -> new CrossConfig(0.2, 1.0, 150.0, share, RANGE, RANGE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cleared-to-corner share must be between 0.0 and 1.0");
    }
}
