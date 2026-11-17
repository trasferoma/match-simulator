package org.tasktelemetry.matchsimulator.coach.config;

public record AssessmentConfig(
        double homeFieldWeight,
        double muchWeakerBoundary,
        double weakerBoundary,
        double strongerBoundary,
        double muchStrongerBoundary) {

    private static final double BOUNDARY_TOLERANCE = 1e-9;

    public AssessmentConfig {
        ConfigGuards.requirePositiveAndFinite("home field weight", homeFieldWeight);
        ConfigGuards.requireFinite("much weaker boundary", muchWeakerBoundary);
        ConfigGuards.requireFinite("weaker boundary", weakerBoundary);
        ConfigGuards.requireFinite("stronger boundary", strongerBoundary);
        ConfigGuards.requireFinite("much stronger boundary", muchStrongerBoundary);
        requireBoundariesIncreasing(muchWeakerBoundary, weakerBoundary, strongerBoundary, muchStrongerBoundary);
    }

    public static AssessmentConfig defaults() {
        double homeFieldWeight = 2.0;
        double muchWeakerBoundary = -6.0;
        double weakerBoundary = -2.0;
        double strongerBoundary = 2.0;
        double muchStrongerBoundary = 6.0;
        return new AssessmentConfig(homeFieldWeight, muchWeakerBoundary, weakerBoundary, strongerBoundary,
                muchStrongerBoundary);
    }

    public AssessmentBand bandOf(double evaluation) {
        if (evaluation <= muchWeakerBoundary + BOUNDARY_TOLERANCE) {
            return AssessmentBand.MUCH_WEAKER;
        }
        if (evaluation <= weakerBoundary + BOUNDARY_TOLERANCE) {
            return AssessmentBand.WEAKER;
        }
        if (evaluation < strongerBoundary - BOUNDARY_TOLERANCE) {
            return AssessmentBand.EVEN;
        }
        if (evaluation < muchStrongerBoundary - BOUNDARY_TOLERANCE) {
            return AssessmentBand.STRONGER;
        }
        return AssessmentBand.MUCH_STRONGER;
    }

    private static void requireBoundariesIncreasing(double muchWeaker, double weaker, double stronger,
            double muchStronger) {
        boolean isIncreasing = muchWeaker < weaker && weaker < stronger && stronger < muchStronger;
        if (!isIncreasing) {
            throw new IllegalArgumentException("band boundaries must be in strictly increasing order: much weaker "
                    + "boundary " + muchWeaker + ", weaker boundary " + weaker + ", stronger boundary " + stronger
                    + ", much stronger boundary " + muchStronger);
        }
    }
}
