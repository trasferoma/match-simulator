package org.tasktelemetry.matchsimulator.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;

class ExpectedGoalsConfigTest {

    private final ExpectedGoalsConfig defaults = ExpectedGoalsConfig.defaults();

    @Test
    void defaultsCoverEveryZoneRolePhaseAndShotKind() {
        assertThat(defaults.baseByZoneRole()).containsOnlyKeys(ZoneRole.values());
        assertThat(defaults.multiplierByPhase()).containsOnlyKeys(PlayPhase.values());
        assertThat(defaults.multiplierByKind()).containsOnlyKeys(ShotKind.values());
    }

    @Test
    void defaultsCarryThePenaltyBaseOfTheSpecification() {
        assertThat(defaults.penaltyBase()).isEqualTo(0.79);
    }

    @Test
    void defaultsAreTheSameEveryTime() {
        assertThat(ExpectedGoalsConfig.defaults()).isEqualTo(ExpectedGoalsConfig.defaults());
    }

    @Test
    void theMapsAreCopiedAndNotModifiable() {
        Map<ZoneRole, Double> base = new EnumMap<>(defaults.baseByZoneRole());
        ExpectedGoalsConfig config = withBase(base);
        base.put(ZoneRole.MIDFIELD, 0.9);

        assertThat(config.baseByZoneRole()).containsEntry(ZoneRole.MIDFIELD, 0.015);
        assertThatThrownBy(() -> config.baseByZoneRole().put(ZoneRole.MIDFIELD, 0.5))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsAMissingMap() {
        assertThatThrownBy(() -> new ExpectedGoalsConfig(null, defaults.multiplierByPhase(),
                defaults.multiplierByKind(), 0.001, 0.99, 70, 0.03, 0.79, 70, 0.02))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsABaseMissingForAZoneRole() {
        Map<ZoneRole, Double> base = new EnumMap<>(defaults.baseByZoneRole());
        base.remove(ZoneRole.DEFENCE);

        assertThatThrownBy(() -> withBase(base))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base expected goals needs a value for DEFENCE");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.5, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsAMultiplierThatIsNotPositiveAndFinite(double multiplier) {
        Map<PlayPhase, Double> phases = new EnumMap<>(defaults.multiplierByPhase());
        phases.put(PlayPhase.SET_PIECE, multiplier);

        assertThatThrownBy(() -> new ExpectedGoalsConfig(defaults.baseByZoneRole(), phases,
                defaults.multiplierByKind(), 0.001, 0.99, 70, 0.03, 0.79, 70, 0.02))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("phase multiplier for SET_PIECE must be positive and finite");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0, -0.1, 1.5, Double.NaN})
    void rejectsLimitsAndPenaltyBaseOutsideTheOpenUnitInterval(double value) {
        assertThatThrownBy(() -> withLimits(value, 0.99)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withLimits(0.001, value)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withPenaltyBase(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAMinimumNotBelowTheMaximum() {
        assertThatThrownBy(() -> withLimits(0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be below the maximum");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void rejectsNeutralRatingsOutsideTheScale(int rating) {
        assertThatThrownBy(() -> withNeutralRatings(rating, 70)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withNeutralRatings(70, rating)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsSlopesThatAreNegativeOrNotFinite(double slope) {
        assertThatThrownBy(() -> withSlopes(slope, 0.02)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withSlopes(0.03, slope)).isInstanceOf(IllegalArgumentException.class);
    }

    private ExpectedGoalsConfig withBase(Map<ZoneRole, Double> base) {
        return new ExpectedGoalsConfig(base, defaults.multiplierByPhase(), defaults.multiplierByKind(), 0.001, 0.99,
                70, 0.03, 0.79, 70, 0.02);
    }

    private ExpectedGoalsConfig withLimits(double minimum, double maximum) {
        return new ExpectedGoalsConfig(defaults.baseByZoneRole(), defaults.multiplierByPhase(),
                defaults.multiplierByKind(), minimum, maximum, 70, 0.03, 0.79, 70, 0.02);
    }

    private ExpectedGoalsConfig withPenaltyBase(double penaltyBase) {
        return new ExpectedGoalsConfig(defaults.baseByZoneRole(), defaults.multiplierByPhase(),
                defaults.multiplierByKind(), 0.001, 0.99, 70, 0.03, penaltyBase, 70, 0.02);
    }

    private ExpectedGoalsConfig withNeutralRatings(int neutralShooting, int penaltyNeutralShooting) {
        return new ExpectedGoalsConfig(defaults.baseByZoneRole(), defaults.multiplierByPhase(),
                defaults.multiplierByKind(), 0.001, 0.99, neutralShooting, 0.03, 0.79, penaltyNeutralShooting, 0.02);
    }

    private ExpectedGoalsConfig withSlopes(double shootingSlope, double penaltyShootingSlope) {
        return new ExpectedGoalsConfig(defaults.baseByZoneRole(), defaults.multiplierByPhase(),
                defaults.multiplierByKind(), 0.001, 0.99, 70, shootingSlope, 0.79, 70, penaltyShootingSlope);
    }
}
