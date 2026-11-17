package org.tasktelemetry.matchsimulator.coach.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FatigueResponseConfigTest {

    @Test
    void defaultsReplaceTheTiredFromTheSeventiethMinuteAndLookBackTenMinutesForFreshAttackers() {
        FatigueResponseConfig defaults = FatigueResponseConfig.defaults();

        assertThat(defaults).isEqualTo(new FatigueResponseConfig(70, 10));
        assertThat(CoachConfig.defaults().fatigueResponse()).isEqualTo(defaults);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 91})
    void rejectsATiredReplacementMinuteOutsideTheMatch(int minute) {
        assertThatThrownBy(() -> new FatigueResponseConfig(minute, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tired replacement minute must be between 1 and 90 minutes, was " + minute);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 91})
    void rejectsAFreshAttackersWindowOutsideTheMatch(int minutes) {
        assertThatThrownBy(() -> new FatigueResponseConfig(70, minutes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fresh attackers window must be between 1 and 90 minutes, was " + minutes);
    }
}
