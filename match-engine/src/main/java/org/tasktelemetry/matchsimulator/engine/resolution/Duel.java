package org.tasktelemetry.matchsimulator.engine.resolution;

public record Duel(double attack, double defence) {

    public Duel {
        requirePositive("attack", attack);
        requirePositive("defence", defence);
    }

    public static Duel between(int attack, int defence) {
        return new Duel(attack, defence);
    }

    public Duel withAttackModifier(double modifier) {
        requirePositive("attack modifier", modifier);
        return new Duel(attack * modifier, defence);
    }

    public Duel withDefenceModifier(double modifier) {
        requirePositive("defence modifier", modifier);
        return new Duel(attack, defence * modifier);
    }

    public double attackerWinProbability() {
        return attack / (attack + defence);
    }

    private static void requirePositive(String name, double value) {
        if (!(value > 0.0)) {
            throw new IllegalArgumentException(name + " must be positive, was " + value);
        }
    }
}
