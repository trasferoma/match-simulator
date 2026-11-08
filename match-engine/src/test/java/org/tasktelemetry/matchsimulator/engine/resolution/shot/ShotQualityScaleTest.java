package org.tasktelemetry.matchsimulator.engine.resolution.shot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.tasktelemetry.matchsimulator.engine.config.ShotConfig;
import org.tasktelemetry.matchsimulator.engine.event.SaveQuality;
import org.tasktelemetry.matchsimulator.engine.event.ShotQuality;

class ShotQualityScaleTest {

    private final ShotConfig config = ShotConfig.defaults();
    private final ShotQualityScale scale = new ShotQualityScale(config);

    @ParameterizedTest
    @CsvSource({
            "0.001, ATTEMPT",
            "0.0799, ATTEMPT",
            "0.08, HALF_CHANCE",
            "0.2499, HALF_CHANCE",
            "0.25, BIG_CHANCE",
            "0.79, BIG_CHANCE"})
    void theDangerOfAShotFollowsTheConfiguredThresholdsOnExpectedGoals(double expectedGoals,
            ShotQuality expected) {
        assertThat(scale.dangerOf(expectedGoals)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "0.01, ROUTINE",
            "0.1499, ROUTINE",
            "0.15, DIFFICULT",
            "0.3499, DIFFICULT",
            "0.35, SPECTACULAR",
            "0.8, SPECTACULAR"})
    void theDifficultyOfASaveFollowsTheConfiguredThresholdsOnTheProbabilityOfAGoal(double goalProbability,
            SaveQuality expected) {
        assertThat(scale.difficultyOfSaving(goalProbability)).isEqualTo(expected);
    }
}
