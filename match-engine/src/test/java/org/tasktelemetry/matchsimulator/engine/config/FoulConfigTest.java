package org.tasktelemetry.matchsimulator.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.event.FoulSeverity;

class FoulConfigTest {

    private static final DurationRange RANGE = new DurationRange(1, 3);

    private final FoulConfig defaults = FoulConfig.defaults();

    @Test
    void defaultsNeverSwitchTheFoulChainOff() {
        assertThat(defaults.baseFoulProbability()).isStrictlyBetween(0.0, 1.0);
        assertThat(defaults.penaltyAreaFoulFactor()).isStrictlyBetween(0.0, 1.0);
        assertThat(defaults.lightShare()).isPositive();
        assertThat(defaults.hardShare()).isPositive();
        assertThat(defaults.recklessShare()).isPositive();
        assertThat(defaults.redProbability().get(FoulSeverity.RECKLESS)).isPositive();
        assertThat(defaults.redProbability().get(FoulSeverity.HARD)).isPositive();
    }

    @Test
    void theSharesOfTheSeveritiesAddUpToOne() {
        double total = defaults.lightShare() + defaults.hardShare() + defaults.recklessShare();

        assertThat(total).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void aGraverFoulIsMoreLikelyToBeBookedThanALighterOne() {
        Map<FoulSeverity, Double> yellow = defaults.yellowProbability();

        assertThat(yellow.get(FoulSeverity.HARD)).isGreaterThan(yellow.get(FoulSeverity.LIGHT));
        assertThat(yellow.get(FoulSeverity.RECKLESS)).isGreaterThan(yellow.get(FoulSeverity.HARD));
    }

    @Test
    void defaultsCarryTheFoulDuration() {
        assertThat(defaults.foulDuration()).isEqualTo(new DurationRange(3, 8));
    }

    @Test
    void rejectsAMissingFoulDuration() {
        assertThatThrownBy(() -> configWith(0.2, 0.5, 0.7, 0.2, defaults.yellowProbability(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.0, 1.5, Double.NaN})
    void rejectsABaseFoulProbabilityOutsideZeroInclusiveAndOneExclusive(double probability) {
        assertThatThrownBy(() -> configWith(probability, 0.5, 0.7, 0.2, defaults.yellowProbability(), RANGE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base foul probability must be at least 0.0 and below 1.0");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.5, 1.5, Double.NaN})
    void rejectsAPenaltyAreaFactorThatDoesNotWeakenTheFoul(double factor) {
        assertThatThrownBy(() -> configWith(0.2, factor, 0.7, 0.2, defaults.yellowProbability(), RANGE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("penalty area foul factor must be above 0.0 and at most 1.0");
    }

    @Test
    void rejectsSeveritySharesThatExceedOne() {
        assertThatThrownBy(() -> configWith(0.2, 0.5, 0.7, 0.4, defaults.yellowProbability(), RANGE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("light and hard shares must not add up to more than 1.0");
    }

    @Test
    void rejectsAYellowProbabilityMissingForASeverity() {
        Map<FoulSeverity, Double> incomplete = new EnumMap<>(FoulSeverity.class);
        incomplete.put(FoulSeverity.LIGHT, 0.1);

        assertThatThrownBy(() -> configWith(0.2, 0.5, 0.7, 0.2, incomplete, RANGE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yellow card probability needs a value for HARD");
    }

    @Test
    void rejectsYellowAndRedThatCannotShareOneDraw() {
        Map<FoulSeverity, Double> alwaysYellow = new EnumMap<>(FoulSeverity.class);
        alwaysYellow.put(FoulSeverity.LIGHT, 1.0);
        alwaysYellow.put(FoulSeverity.HARD, 1.0);
        alwaysYellow.put(FoulSeverity.RECKLESS, 1.0);

        assertThatThrownBy(() -> configWith(0.2, 0.5, 0.7, 0.2, alwaysYellow, RANGE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not add up to more than 1.0");
    }

    @Test
    void theProbabilityMapsAreNotModifiableAndNotLinkedToTheirSource() {
        Map<FoulSeverity, Double> source = new EnumMap<>(defaults.yellowProbability());
        FoulConfig config = configWith(0.2, 0.5, 0.7, 0.2, source, RANGE);
        double lightYellow = defaults.yellowProbability().get(FoulSeverity.LIGHT);
        source.put(FoulSeverity.LIGHT, 0.99);

        assertThat(config.yellowProbability().get(FoulSeverity.LIGHT)).isEqualTo(lightYellow);
        assertThatThrownBy(() -> config.yellowProbability().put(FoulSeverity.LIGHT, 0.5))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private FoulConfig configWith(double baseFoulProbability, double penaltyAreaFactor, double lightShare,
            double hardShare, Map<FoulSeverity, Double> yellow, DurationRange foulDuration) {
        return new FoulConfig(baseFoulProbability, 70, 0.03, penaltyAreaFactor, lightShare, hardShare, yellow,
                defaults.redProbability(), foulDuration);
    }
}
