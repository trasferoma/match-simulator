package org.tasktelemetry.matchsimulator.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StoppageConfigTest {

    @Test
    void defaultsAreOneMinuteMinimumThirtySecondsPerEventAndFiveAndEightMinutesAtMost() {
        StoppageConfig defaults = StoppageConfig.defaults();

        assertThat(defaults.minimumMinutes()).isEqualTo(1);
        assertThat(defaults.secondsPerEvent()).isEqualTo(30);
        assertThat(defaults.maximumFirstHalfMinutes()).isEqualTo(5);
        assertThat(defaults.maximumSecondHalfMinutes()).isEqualTo(8);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsAMinimumBelowOneMinute(int minimum) {
        assertThatThrownBy(() -> new StoppageConfig(minimum, 30, 5, 8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimum stoppage must be at least 1 minute");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -30})
    void rejectsAnEventThatCostsNoTime(int seconds) {
        assertThatThrownBy(() -> new StoppageConfig(1, seconds, 5, 8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("seconds per stoppage event must be at least 1");
    }

    @Test
    void rejectsAMaximumBelowTheMinimum() {
        assertThatThrownBy(() -> new StoppageConfig(3, 30, 2, 8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum first-half stoppage minutes must be at least 3");
        assertThatThrownBy(() -> new StoppageConfig(3, 30, 5, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum second-half stoppage minutes must be at least 3");
    }
}
