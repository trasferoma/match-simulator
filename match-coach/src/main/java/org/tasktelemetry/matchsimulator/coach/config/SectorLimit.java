package org.tasktelemetry.matchsimulator.coach.config;

public record SectorLimit(int minimum, int maximum) {

    public SectorLimit {
        ConfigGuards.requireAtLeastOne("sector minimum", minimum);
        requireMinimumNotAboveMaximum(minimum, maximum);
    }

    public boolean allowsAnother(int current) {
        return current < maximum;
    }

    public boolean canSpareOne(int current) {
        return current > minimum;
    }

    private static void requireMinimumNotAboveMaximum(int minimum, int maximum) {
        if (minimum > maximum) {
            throw new IllegalArgumentException("sector minimum " + minimum + " must not exceed the maximum "
                    + maximum);
        }
    }
}
