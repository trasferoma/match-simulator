package org.tasktelemetry.matchsimulator.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DurationRangeTest {

    @Test
    void containsBothEndsAndWhatIsBetween() {
        DurationRange range = new DurationRange(4, 12);

        assertThat(range.contains(4)).isTrue();
        assertThat(range.contains(8)).isTrue();
        assertThat(range.contains(12)).isTrue();
    }

    @Test
    void doesNotContainWhatIsOutside() {
        DurationRange range = new DurationRange(4, 12);

        assertThat(range.contains(3)).isFalse();
        assertThat(range.contains(13)).isFalse();
    }

    @Test
    void acceptsAFixedDuration() {
        DurationRange range = new DurationRange(5, 5);

        assertThat(range.contains(5)).isTrue();
    }

    @Test
    void rejectsAMinimumBelowOneSecond() {
        assertThatThrownBy(() -> new DurationRange(0, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimum must be at least 1 second");
    }

    @Test
    void rejectsAMaximumBelowTheMinimum() {
        assertThatThrownBy(() -> new DurationRange(8, 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum must not be below the minimum 8");
    }
}
