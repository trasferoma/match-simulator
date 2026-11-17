package org.tasktelemetry.matchsimulator.coach.config;

import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;

final class ConfigGuards {

    static final int MATCH_MINUTES = 2 * MatchPeriod.MINUTES_PER_HALF;

    private ConfigGuards() {
    }

    static void requireMinutesInMatch(String name, int minutes) {
        boolean isInMatch = minutes >= 1 && minutes <= MATCH_MINUTES;
        if (!isInMatch) {
            throw new IllegalArgumentException(name + " must be between 1 and " + MATCH_MINUTES
                    + " minutes, was " + minutes);
        }
    }

    static void requireAtLeastOne(String name, int value) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be at least 1, was " + value);
        }
    }

    static void requirePositiveAndFinite(String name, double value) {
        boolean isPositiveAndFinite = value > 0.0 && !Double.isInfinite(value);
        if (!isPositiveAndFinite) {
            throw new IllegalArgumentException(name + " must be positive and finite, was " + value);
        }
    }

    static void requireFinite(String name, double value) {
        boolean isFinite = !Double.isNaN(value) && !Double.isInfinite(value);
        if (!isFinite) {
            throw new IllegalArgumentException(name + " must be finite, was " + value);
        }
    }

    static void requireOpenProbability(String name, double value) {
        boolean isOpenInterval = value > 0.0 && value < 1.0;
        if (!isOpenInterval) {
            throw new IllegalArgumentException(name + " must be strictly between 0.0 and 1.0, was " + value);
        }
    }

    static void requireInScale(String name, int rating) {
        boolean isInScale = rating >= PlayerAttributes.MIN_VALUE && rating <= PlayerAttributes.MAX_VALUE;
        if (!isInScale) {
            throw new IllegalArgumentException(name + " must be between " + PlayerAttributes.MIN_VALUE + " and "
                    + PlayerAttributes.MAX_VALUE + ", was " + rating);
        }
    }
}
