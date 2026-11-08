package org.tasktelemetry.matchsimulator.engine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;

class MatchTimeTest {

    @ParameterizedTest
    @CsvSource({
            "FIRST_HALF, 0, 0'",
            "FIRST_HALF, 1394, 23'",
            "FIRST_HALF, 2700, 45'",
            "FIRST_HALF, 2759, 45'",
            "FIRST_HALF, 2820, 45+2'",
            "FIRST_HALF, 3030, 45+5'",
            "SECOND_HALF, 0, 45'",
            "SECOND_HALF, 1800, 75'",
            "SECOND_HALF, 2705, 90'",
            "SECOND_HALF, 2830, 90+2'"})
    void displaysTheMinuteInTheMatchStyle(MatchPeriod period, int seconds, String expected) {
        MatchTime time = new MatchTime(period, seconds);

        assertThat(time.display()).isEqualTo(expected);
    }

    @Test
    void exposesAbsoluteMinuteAndSecond() {
        MatchTime time = new MatchTime(MatchPeriod.FIRST_HALF, 23 * 60 + 14);

        assertThat(time.minute()).isEqualTo(23);
        assertThat(time.second()).isEqualTo(14);
        assertThat(new MatchTime(MatchPeriod.SECOND_HALF, 10 * 60).minute()).isEqualTo(55);
    }

    @Test
    void rejectsAPeriodThatIsNotAHalfAndNegativeSeconds() {
        assertThatThrownBy(() -> new MatchTime(MatchPeriod.HALF_TIME, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("belongs to a half");
        assertThatThrownBy(() -> new MatchTime(MatchPeriod.FIRST_HALF, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchTime(null, 0)).isInstanceOf(NullPointerException.class);
    }
}
