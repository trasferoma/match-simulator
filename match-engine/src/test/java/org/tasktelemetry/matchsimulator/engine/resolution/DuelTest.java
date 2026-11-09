package org.tasktelemetry.matchsimulator.engine.resolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DuelTest {

    private static final double EXACT = 1e-9;

    @Test
    void theAttackerWinsWithAttackOverAttackPlusDefence() {
        Duel duel = Duel.between(78, 65);

        assertThat(duel.attackerWinProbability()).isCloseTo(78.0 / 143.0, within(EXACT));
    }

    @Test
    void equalValuesGiveEvenOdds() {
        assertThat(Duel.between(50, 50).attackerWinProbability()).isCloseTo(0.5, within(EXACT));
    }

    @ParameterizedTest
    @CsvSource({"1, 100", "100, 1", "30, 70", "70, 30"})
    void probabilityIsStrictlyBetweenZeroAndOne(int attack, int defence) {
        double probability = Duel.between(attack, defence).attackerWinProbability();

        assertThat(probability).isStrictlyBetween(0.0, 1.0);
    }

    @Test
    void neutralModifiersDoNotChangeTheProbability() {
        Duel duel = Duel.between(78, 65);

        Duel modified = duel.withAttackModifier(1.0).withDefenceModifier(1.0);

        assertThat(modified.attackerWinProbability()).isCloseTo(duel.attackerWinProbability(), within(EXACT));
    }

    @Test
    void anAttackModifierMultipliesTheAttackAndADefenceModifierMultipliesTheDefence() {
        Duel duel = Duel.between(60, 40);

        assertThat(duel.withAttackModifier(1.5).attackerWinProbability()).isCloseTo(90.0 / 130.0, within(EXACT));
        assertThat(duel.withDefenceModifier(0.5).attackerWinProbability()).isCloseTo(60.0 / 80.0, within(EXACT));
    }

    @Test
    void modifiersGoInTheExpectedDirection() {
        Duel duel = Duel.between(60, 40);

        assertThat(duel.withAttackModifier(1.1).attackerWinProbability()).isGreaterThan(duel.attackerWinProbability());
        assertThat(duel.withDefenceModifier(1.1).attackerWinProbability()).isLessThan(duel.attackerWinProbability());
    }

    @Test
    void rejectsNonPositiveValuesAndModifiers() {
        assertThatThrownBy(() -> new Duel(0.0, 10.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Duel(10.0, -1.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Duel(Double.NaN, 10.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Duel.between(50, 50).withAttackModifier(0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attack modifier must be positive");
        assertThatThrownBy(() -> Duel.between(50, 50).withDefenceModifier(-1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defence modifier must be positive");
    }
}
