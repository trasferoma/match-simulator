package org.tasktelemetry.matchsimulator.engine.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;

public record ExpectedGoalsConfig(
        Map<ZoneRole, Double> baseByZoneRole,
        Map<PlayPhase, Double> multiplierByPhase,
        Map<ShotKind, Double> multiplierByKind,
        double minimumExpectedGoals,
        double maximumExpectedGoals,
        int neutralShooting,
        double shootingSlope,
        double penaltyBase,
        int penaltyNeutralShooting,
        double penaltyShootingSlope) {

    public ExpectedGoalsConfig {
        baseByZoneRole = positiveValuesForEvery("base expected goals", ZoneRole.class, baseByZoneRole);
        multiplierByPhase = positiveValuesForEvery("phase multiplier", PlayPhase.class, multiplierByPhase);
        multiplierByKind = positiveValuesForEvery("shot kind multiplier", ShotKind.class, multiplierByKind);
        requireOpenProbability("minimum expected goals", minimumExpectedGoals);
        requireOpenProbability("maximum expected goals", maximumExpectedGoals);
        requireMinimumBelowMaximum(minimumExpectedGoals, maximumExpectedGoals);
        requireInScale("neutral shooting", neutralShooting);
        requireNotNegative("shooting slope", shootingSlope);
        requireOpenProbability("penalty base", penaltyBase);
        requireInScale("penalty neutral shooting", penaltyNeutralShooting);
        requireNotNegative("penalty shooting slope", penaltyShootingSlope);
    }

    public static ExpectedGoalsConfig defaults() {
        Map<ZoneRole, Double> baseByZoneRole = new EnumMap<>(ZoneRole.class);
        baseByZoneRole.put(ZoneRole.OWN_BOX, 0.002);
        baseByZoneRole.put(ZoneRole.DEFENCE, 0.005);
        baseByZoneRole.put(ZoneRole.MIDFIELD, 0.015);
        baseByZoneRole.put(ZoneRole.ATTACKING_THIRD, 0.045);
        baseByZoneRole.put(ZoneRole.OPPONENT_BOX, 0.085);
        Map<PlayPhase, Double> multiplierByPhase = new EnumMap<>(PlayPhase.class);
        multiplierByPhase.put(PlayPhase.BUILD_UP, 1.0);
        multiplierByPhase.put(PlayPhase.SET_PIECE, 1.0);
        Map<ShotKind, Double> multiplierByKind = new EnumMap<>(ShotKind.class);
        multiplierByKind.put(ShotKind.FOOT, 1.0);
        multiplierByKind.put(ShotKind.HEADER, 0.8);
        multiplierByKind.put(ShotKind.DIRECT_FREE_KICK, 1.3);
        double minimumExpectedGoals = 0.001;
        double maximumExpectedGoals = 0.99;
        int neutralShooting = 70;
        double shootingSlope = 0.03;
        double penaltyBase = 0.79;
        int penaltyNeutralShooting = 70;
        double penaltyShootingSlope = 0.02;
        return new ExpectedGoalsConfig(baseByZoneRole, multiplierByPhase, multiplierByKind, minimumExpectedGoals,
                maximumExpectedGoals, neutralShooting, shootingSlope, penaltyBase, penaltyNeutralShooting,
                penaltyShootingSlope);
    }

    private static <K extends Enum<K>> Map<K, Double> positiveValuesForEvery(String name, Class<K> type,
            Map<K, Double> values) {
        if (values == null) {
            throw new NullPointerException(name + " must not be null");
        }
        Map<K, Double> copy = new EnumMap<>(type);
        copy.putAll(values);
        for (K key : type.getEnumConstants()) {
            Double value = copy.get(key);
            if (value == null) {
                throw new IllegalArgumentException(name + " needs a value for " + key);
            }
            boolean isPositive = value > 0.0 && !value.isInfinite();
            if (!isPositive) {
                throw new IllegalArgumentException(name + " for " + key + " must be positive and finite, was "
                        + value);
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static void requireOpenProbability(String name, double value) {
        boolean isOpenInterval = value > 0.0 && value < 1.0;
        if (!isOpenInterval) {
            throw new IllegalArgumentException(name + " must be strictly between 0.0 and 1.0, was " + value);
        }
    }

    private static void requireMinimumBelowMaximum(double minimum, double maximum) {
        if (minimum >= maximum) {
            throw new IllegalArgumentException("minimum expected goals " + minimum
                    + " must be below the maximum " + maximum);
        }
    }

    private static void requireInScale(String name, int rating) {
        boolean isInScale = rating >= PlayerAttributes.MIN_VALUE && rating <= PlayerAttributes.MAX_VALUE;
        if (!isInScale) {
            throw new IllegalArgumentException(name + " must be between " + PlayerAttributes.MIN_VALUE + " and "
                    + PlayerAttributes.MAX_VALUE + ", was " + rating);
        }
    }

    private static void requireNotNegative(String name, double value) {
        boolean isNotNegativeAndFinite = value >= 0.0 && !Double.isInfinite(value);
        if (!isNotNegativeAndFinite) {
            throw new IllegalArgumentException(name + " must not be negative and must be finite, was " + value);
        }
    }
}
