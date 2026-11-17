package org.tasktelemetry.matchsimulator.coach.config;

import org.tasktelemetry.matchsimulator.engine.config.LineupConfig;

public record SubstitutionConfig(int maximumSubstitutions, int reservedSubstitutions) {

    public SubstitutionConfig {
        ConfigGuards.requireAtLeastOne("maximum substitutions", maximumSubstitutions);
        ConfigGuards.requireAtLeastOne("reserved substitutions", reservedSubstitutions);
        requireReserveWithinMaximum(maximumSubstitutions, reservedSubstitutions);
    }

    public static SubstitutionConfig defaults() {
        LineupConfig engineLineup = LineupConfig.defaults();
        int maximumSubstitutions = engineLineup.maximumSubstitutions();
        int reservedSubstitutions = 1;
        return new SubstitutionConfig(maximumSubstitutions, reservedSubstitutions);
    }

    private static void requireReserveWithinMaximum(int maximum, int reserved) {
        if (reserved > maximum) {
            throw new IllegalArgumentException("reserved substitutions " + reserved
                    + " must not exceed the maximum " + maximum);
        }
    }
}
