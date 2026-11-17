package org.tasktelemetry.matchsimulator.engine.config;

import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.state.TeamEnergy;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;

public record FatigueConfig(
        double performanceThreshold,
        double exhaustionThreshold,
        double minimumFactor,
        double drainPerMinute,
        double participationCost,
        double drainStaminaSensitivity,
        int neutralStamina,
        double goalkeeperDrainMultiplier,
        double recoveryShare,
        double recoveryStaminaSensitivity) {

    public FatigueConfig {
        requireAbovePositiveUpTo("performance threshold", performanceThreshold, TeamEnergy.FULL);
        requireBelow("exhaustion threshold", exhaustionThreshold, performanceThreshold);
        requireAbovePositiveUpTo("minimum factor", minimumFactor, 1.0);
        requirePositiveAndFinite("drain per minute", drainPerMinute);
        requirePositiveAndFinite("participation cost", participationCost);
        requireNotNegativeAndFinite("drain stamina sensitivity", drainStaminaSensitivity);
        requireNeutralStaminaInScale(neutralStamina);
        requireAbovePositiveUpTo("goalkeeper drain multiplier", goalkeeperDrainMultiplier, 1.0);
        requirePositiveAndFinite("recovery share", recoveryShare);
        requireNotNegativeAndFinite("recovery stamina sensitivity", recoveryStaminaSensitivity);
        requireDrainFactorPositiveOnTheWholeScale(drainStaminaSensitivity, neutralStamina);
        requireRecoveryShareInRangeOnTheWholeScale(recoveryShare, recoveryStaminaSensitivity, neutralStamina);
    }

    public static FatigueConfig defaults() {
        double performanceThreshold = 70.0;
        double exhaustionThreshold = 40.0;
        double minimumFactor = 0.80;
        double drainPerMinute = 0.52;
        double participationCost = 0.30;
        double drainStaminaSensitivity = 0.25;
        int neutralStamina = 50;
        double goalkeeperDrainMultiplier = 0.3;
        double recoveryShare = 0.5;
        double recoveryStaminaSensitivity = 0.3;
        return new FatigueConfig(performanceThreshold, exhaustionThreshold, minimumFactor, drainPerMinute,
                participationCost, drainStaminaSensitivity, neutralStamina, goalkeeperDrainMultiplier,
                recoveryShare, recoveryStaminaSensitivity);
    }

    public double drainFactorOf(int stamina) {
        return drainFactor(drainStaminaSensitivity, neutralStamina, stamina);
    }

    public double recoveryShareOf(int stamina) {
        return recoveryShareAt(recoveryShare, recoveryStaminaSensitivity, neutralStamina, stamina);
    }

    public FatigueBand bandOf(double energy) {
        if (energy >= performanceThreshold) {
            return FatigueBand.FRESH;
        }
        if (energy >= exhaustionThreshold) {
            return FatigueBand.TIRED;
        }
        return FatigueBand.EXHAUSTED;
    }

    private static double drainFactor(double sensitivity, int neutralStamina, int stamina) {
        double staminaBelowNeutral = neutralStamina - stamina;
        return 1.0 + sensitivity * staminaBelowNeutral / neutralStamina;
    }

    private static double recoveryShareAt(double baseShare, double sensitivity, int neutralStamina, int stamina) {
        double staminaAboveNeutral = stamina - neutralStamina;
        return baseShare * (1.0 + sensitivity * staminaAboveNeutral / neutralStamina);
    }

    private static void requireDrainFactorPositiveOnTheWholeScale(double sensitivity, int neutralStamina) {
        double atLowestStamina = drainFactor(sensitivity, neutralStamina, PlayerAttributes.MIN_VALUE);
        double atHighestStamina = drainFactor(sensitivity, neutralStamina, PlayerAttributes.MAX_VALUE);
        if (atLowestStamina <= 0.0 || atHighestStamina <= 0.0) {
            throw new IllegalArgumentException("drain stamina sensitivity must keep the drain factor positive"
                    + " for every stamina, but it is " + atLowestStamina + " at stamina "
                    + PlayerAttributes.MIN_VALUE + " and " + atHighestStamina + " at stamina "
                    + PlayerAttributes.MAX_VALUE);
        }
    }

    private static void requireRecoveryShareInRangeOnTheWholeScale(double baseShare, double sensitivity,
            int neutralStamina) {
        double atLowestStamina = recoveryShareAt(baseShare, sensitivity, neutralStamina, PlayerAttributes.MIN_VALUE);
        double atHighestStamina = recoveryShareAt(baseShare, sensitivity, neutralStamina,
                PlayerAttributes.MAX_VALUE);
        if (atLowestStamina <= 0.0 || atHighestStamina > 1.0) {
            throw new IllegalArgumentException("recovery share must stay above 0.0 and at most 1.0 for every"
                    + " stamina, but it is " + atLowestStamina + " at stamina " + PlayerAttributes.MIN_VALUE
                    + " and " + atHighestStamina + " at stamina " + PlayerAttributes.MAX_VALUE);
        }
    }

    private static void requireNeutralStaminaInScale(int neutralStamina) {
        boolean isAboveMinimum = neutralStamina >= PlayerAttributes.MIN_VALUE;
        boolean isInScale = isAboveMinimum && neutralStamina <= PlayerAttributes.MAX_VALUE;
        if (!isInScale) {
            throw new IllegalArgumentException("neutral stamina must be between " + PlayerAttributes.MIN_VALUE
                    + " and " + PlayerAttributes.MAX_VALUE + ", was " + neutralStamina);
        }
    }

    private static void requireAbovePositiveUpTo(String name, double value, double upperLimit) {
        boolean isInRange = value > 0.0 && value <= upperLimit;
        if (!isInRange) {
            throw new IllegalArgumentException(name + " must be above 0.0 and at most " + upperLimit + ", was "
                    + value);
        }
    }

    private static void requireBelow(String name, double value, double limit) {
        boolean isBelow = value > 0.0 && value < limit;
        if (!isBelow) {
            throw new IllegalArgumentException(name + " must be above 0.0 and below " + limit + ", was " + value);
        }
    }

    private static void requirePositiveAndFinite(String name, double value) {
        boolean isPositiveAndFinite = value > 0.0 && !Double.isInfinite(value);
        if (!isPositiveAndFinite) {
            throw new IllegalArgumentException(name + " must be positive and finite, was " + value);
        }
    }

    private static void requireNotNegativeAndFinite(String name, double value) {
        boolean isNotNegativeAndFinite = value >= 0.0 && !Double.isInfinite(value);
        if (!isNotNegativeAndFinite) {
            throw new IllegalArgumentException(name + " must not be negative and must be finite, was " + value);
        }
    }
}
