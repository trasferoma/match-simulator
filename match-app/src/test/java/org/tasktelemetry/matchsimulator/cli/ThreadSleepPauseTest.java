package org.tasktelemetry.matchsimulator.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ThreadSleepPauseTest {

    private final ThreadSleepPause pause = new ThreadSleepPause();

    @Test
    void anInterruptedPauseKeepsTheInterruptionFlagAndStopsWithAnExplicitError() {
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> pause.waitFor(Duration.ofMillis(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("interrupted")
                .hasCauseInstanceOf(InterruptedException.class);
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void aZeroPauseReturnsAtOnce() {
        pause.waitFor(Duration.ZERO);

        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }
}
