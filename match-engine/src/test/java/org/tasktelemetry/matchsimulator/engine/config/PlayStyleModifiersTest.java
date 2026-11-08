package org.tasktelemetry.matchsimulator.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;

class PlayStyleModifiersTest {

    private static final double EXACT = 1e-9;
    private static final double NEUTRAL = 1.0;

    private final PlayStyleModifiers defaults = PlayStyleModifiers.defaults();

    @ParameterizedTest
    @EnumSource(PlayStyle.class)
    void everyStyleHasAnEffect(PlayStyle style) {
        assertThat(defaults.effectOf(style)).isNotNull();
    }

    @Test
    void theBalancedStyleMultipliesEveryPointByOne() {
        StyleEffect balanced = defaults.effectOf(PlayStyle.BALANCED);

        assertThat(balanced).isEqualTo(StyleEffect.neutral());
        assertThat(balanced.attack()).isEqualTo(NEUTRAL);
        assertThat(balanced.midfield()).isEqualTo(NEUTRAL);
        assertThat(balanced.defence()).isEqualTo(NEUTRAL);
        assertThat(balanced.counterAttackStart()).isEqualTo(NEUTRAL);
        assertThat(balanced.counterAttackAdvance()).isEqualTo(NEUTRAL);
        assertThat(balanced.crossPropensity()).isEqualTo(NEUTRAL);
    }

    @ParameterizedTest
    @CsvSource({
            "BALANCED,       1.00, 1.00, 1.00, 1.00, 1.00, 1.00",
            "ATTACKING,      1.10, 1.00, 0.92, 1.00, 1.00, 1.15",
            "DEFENSIVE,      0.90, 1.00, 1.10, 1.00, 1.00, 1.00",
            "POSSESSION,     1.00, 1.10, 1.00, 0.70, 1.00, 0.85",
            "COUNTER_ATTACK, 1.00, 0.92, 1.00, 1.40, 1.10, 1.00"})
    void everyStyleModifiesOnlyThePointsDeclaredInTheSpecification(PlayStyle style, double attack, double midfield,
            double defence, double counterAttackStart, double counterAttackAdvance, double crossPropensity) {
        StyleEffect effect = defaults.effectOf(style);

        assertThat(effect.attack()).isCloseTo(attack, within(EXACT));
        assertThat(effect.midfield()).isCloseTo(midfield, within(EXACT));
        assertThat(effect.defence()).isCloseTo(defence, within(EXACT));
        assertThat(effect.counterAttackStart()).isCloseTo(counterAttackStart, within(EXACT));
        assertThat(effect.counterAttackAdvance()).isCloseTo(counterAttackAdvance, within(EXACT));
        assertThat(effect.crossPropensity()).isCloseTo(crossPropensity, within(EXACT));
    }

    @ParameterizedTest
    @EnumSource(value = PlayStyle.class, names = "BALANCED", mode = EnumSource.Mode.EXCLUDE)
    void everyStyleOtherThanBalancedDiffersFromNeutral(PlayStyle style) {
        assertThat(defaults.effectOf(style)).isNotEqualTo(StyleEffect.neutral());
    }

    @Test
    void theHighestMultipliersAreThoseOfTheStylesThatRaiseThem() {
        assertThat(defaults.highestCrossPropensity()).isCloseTo(1.15, within(EXACT));
        assertThat(defaults.highestCounterAttackStart()).isCloseTo(1.40, within(EXACT));
    }

    @Test
    void rejectsMissingEffects() {
        assertThatThrownBy(() -> new PlayStyleModifiers(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsAStyleWithoutAnEffect() {
        Map<PlayStyle, StyleEffect> effects = new EnumMap<>(defaults.effectByStyle());
        effects.remove(PlayStyle.POSSESSION);

        assertThatThrownBy(() -> new PlayStyleModifiers(effects))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("style effects need a value for POSSESSION");
    }

    @Test
    void theEffectsAreACopyThatCannotBeChanged() {
        Map<PlayStyle, StyleEffect> effects = new EnumMap<>(defaults.effectByStyle());
        PlayStyleModifiers modifiers = new PlayStyleModifiers(effects);
        effects.put(PlayStyle.ATTACKING, StyleEffect.neutral());

        assertThat(modifiers.effectOf(PlayStyle.ATTACKING)).isEqualTo(defaults.effectOf(PlayStyle.ATTACKING));
        assertThatThrownBy(() -> modifiers.effectByStyle().put(PlayStyle.ATTACKING, StyleEffect.neutral()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY})
    void aMultiplierMustBePositiveAndFinite(double multiplier) {
        StyleEffect neutral = StyleEffect.neutral();

        assertThatThrownBy(() -> neutral.withAttack(multiplier)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attack multiplier must be positive and finite");
        assertThatThrownBy(() -> neutral.withMidfield(multiplier)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("midfield multiplier must be positive and finite");
        assertThatThrownBy(() -> neutral.withDefence(multiplier)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defence multiplier must be positive and finite");
        assertThatThrownBy(() -> neutral.withCounterAttackStart(multiplier))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("counter-attack start multiplier must be positive and finite");
        assertThatThrownBy(() -> neutral.withCounterAttackAdvance(multiplier))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("counter-attack advance multiplier must be positive and finite");
        assertThatThrownBy(() -> neutral.withCrossPropensity(multiplier))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cross propensity multiplier must be positive and finite");
    }

    @Test
    void eachWitherChangesOnlyItsOwnPoint() {
        StyleEffect neutral = StyleEffect.neutral();

        assertThat(neutral.withAttack(2.0)).isEqualTo(new StyleEffect(2.0, 1.0, 1.0, 1.0, 1.0, 1.0));
        assertThat(neutral.withMidfield(2.0)).isEqualTo(new StyleEffect(1.0, 2.0, 1.0, 1.0, 1.0, 1.0));
        assertThat(neutral.withDefence(2.0)).isEqualTo(new StyleEffect(1.0, 1.0, 2.0, 1.0, 1.0, 1.0));
        assertThat(neutral.withCounterAttackStart(2.0)).isEqualTo(new StyleEffect(1.0, 1.0, 1.0, 2.0, 1.0, 1.0));
        assertThat(neutral.withCounterAttackAdvance(2.0)).isEqualTo(new StyleEffect(1.0, 1.0, 1.0, 1.0, 2.0, 1.0));
        assertThat(neutral.withCrossPropensity(2.0)).isEqualTo(new StyleEffect(1.0, 1.0, 1.0, 1.0, 1.0, 2.0));
    }
}
