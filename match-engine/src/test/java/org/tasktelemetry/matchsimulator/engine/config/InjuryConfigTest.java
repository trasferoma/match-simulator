package org.tasktelemetry.matchsimulator.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InjuryConfigTest {

    private static final DurationRange DURATION = new DurationRange(60, 150);

    @Test
    void defaultsGiveAnInjuryProbabilityAboveZeroAndBelowOne() {
        assertThat(InjuryConfig.defaults().probabilityPerAction()).isStrictlyBetween(0.0, 1.0);
    }

    @Test
    void defaultsTakeBetweenSixtyAndOneHundredFiftySecondsPerInjury() {
        assertThat(InjuryConfig.defaults().duration()).isEqualTo(new DurationRange(60, 150));
    }

    @Test
    void aZeroProbabilityIsAcceptedSoThatPartiesWithoutInjuriesCanBeConfigured() {
        assertThat(new InjuryConfig(0.0, DURATION).probabilityPerAction()).isZero();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.0, 1.1, Double.NaN})
    void rejectsAProbabilityOutsideZeroIncludedAndOneExcluded(double probability) {
        assertThatThrownBy(() -> new InjuryConfig(probability, DURATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("injury probability per action must be at least 0.0 and below 1.0");
    }

    @Test
    void rejectsAMissingDuration() {
        assertThatThrownBy(() -> new InjuryConfig(0.1, null)).isInstanceOf(NullPointerException.class);
    }
}
