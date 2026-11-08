package org.tasktelemetry.matchsimulator.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OffsideConfigTest {

    @Test
    void defaultsGiveAnOffsideProbabilityStrictlyBetweenZeroAndOne() {
        assertThat(OffsideConfig.defaults().baseProbability()).isStrictlyBetween(0.0, 1.0);
    }

    @Test
    void defaultsMakeTheOffsideMoreLikelyAsThePositioningFalls() {
        assertThat(OffsideConfig.defaults().positioningSlope()).isPositive();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0, -0.1, 1.1, Double.NaN})
    void rejectsABaseProbabilityOutsideTheOpenIntervalFromZeroToOne(double probability) {
        assertThatThrownBy(() -> new OffsideConfig(probability, 70, 0.03))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base offside probability must be strictly between 0.0 and 1.0");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void rejectsANeutralPositioningOutsideTheAttributeScale(int rating) {
        assertThatThrownBy(() -> new OffsideConfig(0.1, rating, 0.03))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("neutral positioning must be between 1 and 100");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsASlopeWhichIsNegativeOrNotFinite(double slope) {
        assertThatThrownBy(() -> new OffsideConfig(0.1, 70, slope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positioning slope must not be negative and must be finite");
    }
}
