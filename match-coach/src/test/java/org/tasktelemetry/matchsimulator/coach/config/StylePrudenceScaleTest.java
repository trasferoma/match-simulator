package org.tasktelemetry.matchsimulator.coach.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;

class StylePrudenceScaleTest {

    private final StylePrudenceScale scale = StylePrudenceScale.defaults();

    @Test
    void theGradeOfEachStyleIsTheOneOfTheScale() {
        assertThat(scale.gradeOf(PlayStyle.DEFENSIVE)).isEqualTo(-2);
        assertThat(scale.gradeOf(PlayStyle.COUNTER_ATTACK)).isEqualTo(-1);
        assertThat(scale.gradeOf(PlayStyle.BALANCED)).isZero();
        assertThat(scale.gradeOf(PlayStyle.POSSESSION)).isEqualTo(1);
        assertThat(scale.gradeOf(PlayStyle.ATTACKING)).isEqualTo(2);
    }

    @Test
    void theNextStyleOnTheScaleIsTheNearestOneInTheDirectionAsked() {
        assertThat(scale.moreOffensiveThan(PlayStyle.DEFENSIVE)).contains(PlayStyle.COUNTER_ATTACK);
        assertThat(scale.moreOffensiveThan(PlayStyle.BALANCED)).contains(PlayStyle.POSSESSION);
        assertThat(scale.morePrudentThan(PlayStyle.ATTACKING)).contains(PlayStyle.POSSESSION);
        assertThat(scale.morePrudentThan(PlayStyle.BALANCED)).contains(PlayStyle.COUNTER_ATTACK);
    }

    @Test
    void thereIsNoStyleBeyondTheExtremesOfTheScale() {
        assertThat(scale.moreOffensiveThan(PlayStyle.ATTACKING)).isEmpty();
        assertThat(scale.morePrudentThan(PlayStyle.DEFENSIVE)).isEmpty();
    }

    @Test
    void withARepeatedGradeTheStepSkipsToTheNextGradeAndTakesTheFirstStyleInTheOrderOfTheEnum() {
        Map<PlayStyle, Integer> grades = new EnumMap<>(PlayStyle.class);
        grades.put(PlayStyle.BALANCED, 0);
        grades.put(PlayStyle.ATTACKING, 2);
        grades.put(PlayStyle.DEFENSIVE, -2);
        grades.put(PlayStyle.POSSESSION, 1);
        grades.put(PlayStyle.COUNTER_ATTACK, 1);
        StylePrudenceScale repeated = new StylePrudenceScale(grades);

        assertThat(repeated.moreOffensiveThan(PlayStyle.BALANCED)).contains(PlayStyle.POSSESSION);
        assertThat(repeated.morePrudentThan(PlayStyle.ATTACKING)).contains(PlayStyle.POSSESSION);
    }
}
