package org.tasktelemetry.matchsimulator.engine.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlayerAttributesTest {

    private static final List<String> ATTRIBUTE_NAMES = List.of(
            "passing", "dribbling", "shooting", "tackling", "positioning", "pace", "aerial", "goalkeeping");

    @Test
    void acceptsValuesAtBothEndsOfTheScale() {
        PlayerAttributes lowest = new PlayerAttributes(1, 1, 1, 1, 1, 1, 1, 1);
        PlayerAttributes highest = new PlayerAttributes(100, 100, 100, 100, 100, 100, 100, 100);

        assertThat(lowest.passing()).isEqualTo(1);
        assertThat(highest.goalkeeping()).isEqualTo(100);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101, -5})
    void rejectsEveryAttributeOutsideTheScale(int value) {
        for (String attribute : ATTRIBUTE_NAMES) {
            assertThatThrownBy(() -> attributesWith(attribute, value))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("attribute " + attribute + " must be between 1 and 100");
        }
    }

    @Test
    void movementAverageIsTheMeanOfTheSevenMovementAttributes() {
        PlayerAttributes attributes = new PlayerAttributes(60, 70, 80, 50, 40, 30, 20, 99);

        assertThat(attributes.movementAverage()).isEqualTo(50.0);
    }

    @Test
    void movementAverageIgnoresGoalkeeping() {
        PlayerAttributes weakKeeper = new PlayerAttributes(60, 60, 60, 60, 60, 60, 60, 1);
        PlayerAttributes strongKeeper = new PlayerAttributes(60, 60, 60, 60, 60, 60, 60, 100);

        assertThat(weakKeeper.movementAverage()).isEqualTo(strongKeeper.movementAverage());
    }

    private PlayerAttributes attributesWith(String attribute, int value) {
        int[] values = new int[ATTRIBUTE_NAMES.size()];
        Arrays.fill(values, 50);
        values[ATTRIBUTE_NAMES.indexOf(attribute)] = value;
        return new PlayerAttributes(values[0], values[1], values[2], values[3],
                values[4], values[5], values[6], values[7]);
    }
}
