package org.tasktelemetry.matchsimulator.engine.config;

public record StyleEffect(
        double attack,
        double midfield,
        double defence,
        double counterAttackStart,
        double counterAttackAdvance,
        double crossPropensity) {

    private static final double NEUTRAL = 1.0;

    public StyleEffect {
        requirePositiveAndFinite("attack multiplier", attack);
        requirePositiveAndFinite("midfield multiplier", midfield);
        requirePositiveAndFinite("defence multiplier", defence);
        requirePositiveAndFinite("counter-attack start multiplier", counterAttackStart);
        requirePositiveAndFinite("counter-attack advance multiplier", counterAttackAdvance);
        requirePositiveAndFinite("cross propensity multiplier", crossPropensity);
    }

    public static StyleEffect neutral() {
        return new StyleEffect(NEUTRAL, NEUTRAL, NEUTRAL, NEUTRAL, NEUTRAL, NEUTRAL);
    }

    public StyleEffect withAttack(double multiplier) {
        return new StyleEffect(multiplier, midfield, defence, counterAttackStart, counterAttackAdvance,
                crossPropensity);
    }

    public StyleEffect withMidfield(double multiplier) {
        return new StyleEffect(attack, multiplier, defence, counterAttackStart, counterAttackAdvance,
                crossPropensity);
    }

    public StyleEffect withDefence(double multiplier) {
        return new StyleEffect(attack, midfield, multiplier, counterAttackStart, counterAttackAdvance,
                crossPropensity);
    }

    public StyleEffect withCounterAttackStart(double multiplier) {
        return new StyleEffect(attack, midfield, defence, multiplier, counterAttackAdvance, crossPropensity);
    }

    public StyleEffect withCounterAttackAdvance(double multiplier) {
        return new StyleEffect(attack, midfield, defence, counterAttackStart, multiplier, crossPropensity);
    }

    public StyleEffect withCrossPropensity(double multiplier) {
        return new StyleEffect(attack, midfield, defence, counterAttackStart, counterAttackAdvance, multiplier);
    }

    private static void requirePositiveAndFinite(String name, double value) {
        boolean isPositiveAndFinite = value > 0.0 && !Double.isInfinite(value);
        if (!isPositiveAndFinite) {
            throw new IllegalArgumentException(name + " must be positive and finite, was " + value);
        }
    }
}
