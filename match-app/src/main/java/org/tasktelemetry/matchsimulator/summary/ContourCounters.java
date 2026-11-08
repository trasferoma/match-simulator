package org.tasktelemetry.matchsimulator.summary;

public record ContourCounters(
        int corners,
        int crosses,
        int fouls,
        int yellowCards,
        int redCards,
        int offsides,
        int substitutions,
        int penalties) {

    public static ContourCounters none() {
        return new ContourCounters(0, 0, 0, 0, 0, 0, 0, 0);
    }

    public ContourCounters withCorners(int corners) {
        return new ContourCounters(corners, crosses, fouls, yellowCards, redCards, offsides, substitutions, penalties);
    }

    public ContourCounters withCrosses(int crosses) {
        return new ContourCounters(corners, crosses, fouls, yellowCards, redCards, offsides, substitutions, penalties);
    }
}
