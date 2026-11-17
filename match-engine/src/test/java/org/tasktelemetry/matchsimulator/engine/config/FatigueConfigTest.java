package org.tasktelemetry.matchsimulator.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.TestConfigs;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;

class FatigueConfigTest {

    private static final double EXACT = 1e-9;

    private final FatigueConfig defaults = FatigueConfig.defaults();

    @Test
    void theDefaultsAreTheOrientationValuesOfTheSpecification() {
        assertThat(defaults.performanceThreshold()).isEqualTo(70.0);
        assertThat(defaults.exhaustionThreshold()).isEqualTo(40.0);
        assertThat(defaults.minimumFactor()).isEqualTo(0.80);
        assertThat(defaults.drainPerMinute()).isEqualTo(0.52);
        assertThat(defaults.participationCost()).isEqualTo(0.30);
        assertThat(defaults.drainStaminaSensitivity()).isEqualTo(0.25);
        assertThat(defaults.neutralStamina()).isEqualTo(50);
        assertThat(defaults.goalkeeperDrainMultiplier()).isEqualTo(0.3);
        assertThat(defaults.recoveryShare()).isEqualTo(0.5);
        assertThat(defaults.recoveryStaminaSensitivity()).isEqualTo(0.3);
    }

    @ParameterizedTest
    @CsvSource({"100, FRESH", "70.0, FRESH", "69.99, TIRED", "40.0, TIRED", "39.99, EXHAUSTED", "0, EXHAUSTED"})
    void theBandComesFromTheTwoThresholds(double energy, FatigueBand expected) {
        assertThat(defaults.bandOf(energy)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"1, 1.49", "50, 1.0", "100, 0.5"})
    void theDrainFactorFallsAsTheStaminaRises(int stamina, double expected) {
        assertThat(TestConfigs.specificationExamples().drainFactorOf(stamina)).isCloseTo(expected, within(EXACT));
    }

    @ParameterizedTest
    @CsvSource({"1, 0.353", "50, 0.5", "100, 0.65"})
    void theRecoveryShareRisesWithTheStamina(int stamina, double expected) {
        assertThat(defaults.recoveryShareOf(stamina)).isCloseTo(expected, within(EXACT));
    }

    @Test
    void aNeutralStaminaOfTwentyWithTheDefaultSensitivitiesIsRejectedOnTheWorstCase() {
        assertThatThrownBy(() -> withNeutralStamina(20, 0.5, 0.3, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("drain stamina sensitivity must keep the drain factor positive");
    }

    @Test
    void aNeutralStaminaOfTwentyWithGentlerSensitivitiesIsAccepted() {
        FatigueConfig gentle = withNeutralStamina(20, 0.5, 0.1, 0.2);

        assertThat(gentle.drainFactorOf(100)).isCloseTo(0.2, within(EXACT));
        assertThat(gentle.recoveryShareOf(100)).isCloseTo(0.7, within(EXACT));
        assertThat(gentle.recoveryShareOf(1)).isCloseTo(0.4525, within(EXACT));
    }

    @Test
    void aRecoveryShareAboveOneAtTheHighestStaminaIsRejected() {
        assertThatThrownBy(() -> withNeutralStamina(50, 0.9, 0.3, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recovery share must stay above 0.0 and at most 1.0");
    }

    @Test
    void aRecoveryShareThatIsNotPositiveAtTheLowestStaminaIsRejected() {
        assertThatThrownBy(() -> withNeutralStamina(100, 0.5, 5.0, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recovery share must stay above 0.0 and at most 1.0");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.3, 1.1})
    void aGoalkeeperMultiplierOutsideZeroExcludedAndOneIsRejected(double multiplier) {
        assertThatThrownBy(() -> new FatigueConfig(70.0, 40.0, 0.80, 0.45, 0.25, 0.5, 50, multiplier, 0.5, 0.3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("goalkeeper drain multiplier must be above 0.0 and at most 1.0");
    }

    @Test
    void theExhaustionThresholdMustBeBelowThePerformanceThreshold() {
        assertThatThrownBy(() -> new FatigueConfig(70.0, 70.0, 0.80, 0.45, 0.25, 0.5, 50, 0.3, 0.5, 0.3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exhaustion threshold must be above 0.0 and below 70.0");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, 100.5, Double.NaN})
    void thePerformanceThresholdMustBeInsideTheEnergyScale(double threshold) {
        assertThatThrownBy(() -> new FatigueConfig(threshold, 40.0, 0.80, 0.45, 0.25, 0.5, 50, 0.3, 0.5, 0.3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("performance threshold must be above 0.0 and at most");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.1, 1.1, Double.NaN})
    void theMinimumFactorMustBeAboveZeroAndAtMostOne(double minimum) {
        assertThatThrownBy(() -> new FatigueConfig(70.0, 40.0, minimum, 0.45, 0.25, 0.5, 50, 0.3, 0.5, 0.3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimum factor must be above 0.0 and at most 1.0");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.1, Double.NaN, Double.POSITIVE_INFINITY})
    void theDrainAndTheParticipationCostMustBePositiveAndFinite(double value) {
        assertThatThrownBy(() -> new FatigueConfig(70.0, 40.0, 0.80, value, 0.25, 0.5, 50, 0.3, 0.5, 0.3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("drain per minute must be positive and finite");
        assertThatThrownBy(() -> new FatigueConfig(70.0, 40.0, 0.80, 0.45, value, 0.5, 50, 0.3, 0.5, 0.3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("participation cost must be positive and finite");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY})
    void theSensitivitiesMustNotBeNegativeAndMustBeFinite(double sensitivity) {
        assertThatThrownBy(() -> new FatigueConfig(70.0, 40.0, 0.80, 0.45, 0.25, sensitivity, 50, 0.3, 0.5, 0.3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("drain stamina sensitivity must not be negative");
        assertThatThrownBy(() -> new FatigueConfig(70.0, 40.0, 0.80, 0.45, 0.25, 0.5, 50, 0.3, 0.5, sensitivity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recovery stamina sensitivity must not be negative");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.5, Double.NaN})
    void theRecoveryShareMustBePositive(double share) {
        assertThatThrownBy(() -> new FatigueConfig(70.0, 40.0, 0.80, 0.45, 0.25, 0.5, 50, 0.3, share, 0.3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recovery share must be positive and finite");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    void theNeutralStaminaMustBeInsideTheAttributeScale(int neutralStamina) {
        assertThatThrownBy(() -> withNeutralStamina(neutralStamina, 0.5, 0.3, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("neutral stamina must be between 1 and 100");
    }

    private FatigueConfig withNeutralStamina(int neutralStamina, double recoveryShare,
            double recoverySensitivity, double drainSensitivity) {
        return new FatigueConfig(70.0, 40.0, 0.80, 0.45, 0.25, drainSensitivity, neutralStamina, 0.3,
                recoveryShare, recoverySensitivity);
    }
}
