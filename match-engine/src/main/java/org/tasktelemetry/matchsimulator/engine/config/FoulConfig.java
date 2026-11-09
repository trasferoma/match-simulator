package org.tasktelemetry.matchsimulator.engine.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.event.FoulSeverity;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;

public record FoulConfig(
        double baseFoulProbability,
        int neutralTackling,
        double tacklingSlope,
        double penaltyAreaFoulFactor,
        double lightShare,
        double hardShare,
        Map<FoulSeverity, Double> yellowProbability,
        Map<FoulSeverity, Double> redProbability,
        DurationRange foulDuration) {

    public FoulConfig {
        Objects.requireNonNull(foulDuration, "foul duration must not be null");
        requireProbabilityBelowOne(baseFoulProbability);
        requireInScale(neutralTackling);
        requireNotNegativeAndFinite("tackling slope", tacklingSlope);
        requireWeakeningFactor(penaltyAreaFoulFactor);
        requireShare("light share", lightShare);
        requireShare("hard share", hardShare);
        requireRecklessShareLeft(lightShare, hardShare);
        yellowProbability = probabilityForEverySeverity("yellow card probability", yellowProbability);
        redProbability = probabilityForEverySeverity("red card probability", redProbability);
        requireCardsFitInOneDraw(yellowProbability, redProbability);
    }

    public static FoulConfig defaults() {
        double baseFoulProbability = 0.85;
        int neutralTackling = 70;
        double tacklingSlope = 0.03;
        double penaltyAreaFoulFactor = 0.08;
        double lightShare = 0.70;
        double hardShare = 0.22;
        Map<FoulSeverity, Double> yellowProbability = new EnumMap<>(FoulSeverity.class);
        yellowProbability.put(FoulSeverity.LIGHT, 0.04);
        yellowProbability.put(FoulSeverity.HARD, 0.30);
        yellowProbability.put(FoulSeverity.RECKLESS, 0.65);
        Map<FoulSeverity, Double> redProbability = new EnumMap<>(FoulSeverity.class);
        redProbability.put(FoulSeverity.LIGHT, 0.0);
        redProbability.put(FoulSeverity.HARD, 0.002);
        redProbability.put(FoulSeverity.RECKLESS, 0.02);
        DurationRange foulDuration = new DurationRange(3, 8);
        return new FoulConfig(baseFoulProbability, neutralTackling, tacklingSlope, penaltyAreaFoulFactor, lightShare,
                hardShare, yellowProbability, redProbability, foulDuration);
    }

    public double recklessShare() {
        return 1.0 - lightShare - hardShare;
    }

    private static Map<FoulSeverity, Double> probabilityForEverySeverity(String name,
            Map<FoulSeverity, Double> values) {
        Objects.requireNonNull(values, name + " must not be null");
        Map<FoulSeverity, Double> copy = new EnumMap<>(FoulSeverity.class);
        copy.putAll(values);
        for (FoulSeverity severity : FoulSeverity.values()) {
            Double value = copy.get(severity);
            if (value == null) {
                throw new IllegalArgumentException(name + " needs a value for " + severity);
            }
            requireShare(name + " for " + severity, value);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static void requireCardsFitInOneDraw(Map<FoulSeverity, Double> yellow, Map<FoulSeverity, Double> red) {
        for (FoulSeverity severity : FoulSeverity.values()) {
            double cards = yellow.get(severity) + red.get(severity);
            if (cards > 1.0) {
                throw new IllegalArgumentException("yellow and red probability for " + severity
                        + " must not add up to more than 1.0, but they add up to " + cards);
            }
        }
    }

    private static void requireRecklessShareLeft(double lightShare, double hardShare) {
        double lightAndHard = lightShare + hardShare;
        if (lightAndHard > 1.0) {
            throw new IllegalArgumentException("light and hard shares must not add up to more than 1.0, but they"
                    + " add up to " + lightAndHard);
        }
    }

    private static void requireProbabilityBelowOne(double value) {
        boolean isBelowOne = value >= 0.0 && value < 1.0;
        if (!isBelowOne) {
            throw new IllegalArgumentException("base foul probability must be at least 0.0 and below 1.0, was "
                    + value);
        }
    }

    private static void requireWeakeningFactor(double factor) {
        boolean isWeakening = factor > 0.0 && factor <= 1.0;
        if (!isWeakening) {
            throw new IllegalArgumentException("penalty area foul factor must be above 0.0 and at most 1.0, was "
                    + factor);
        }
    }

    private static void requireShare(String name, double share) {
        boolean isShare = share >= 0.0 && share <= 1.0;
        if (!isShare) {
            throw new IllegalArgumentException(name + " must be between 0.0 and 1.0, was " + share);
        }
    }

    private static void requireNotNegativeAndFinite(String name, double value) {
        boolean isNotNegativeAndFinite = value >= 0.0 && !Double.isInfinite(value);
        if (!isNotNegativeAndFinite) {
            throw new IllegalArgumentException(name + " must not be negative and must be finite, was " + value);
        }
    }

    private static void requireInScale(int rating) {
        boolean isInScale = rating >= PlayerAttributes.MIN_VALUE && rating <= PlayerAttributes.MAX_VALUE;
        if (!isInScale) {
            throw new IllegalArgumentException("neutral tackling must be between " + PlayerAttributes.MIN_VALUE
                    + " and " + PlayerAttributes.MAX_VALUE + ", was " + rating);
        }
    }
}
