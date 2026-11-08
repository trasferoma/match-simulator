package org.tasktelemetry.matchsimulator.summary;

import java.util.Objects;

public record TeamStatistics(
        int shots,
        int shotsOnTarget,
        double expectedGoals,
        int possessionSeconds,
        ContourCounters contour) {

    public TeamStatistics {
        Objects.requireNonNull(contour, "contour counters must not be null");
    }
}
