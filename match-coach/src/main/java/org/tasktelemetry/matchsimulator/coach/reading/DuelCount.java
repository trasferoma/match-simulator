package org.tasktelemetry.matchsimulator.coach.reading;

public record DuelCount(int lost, int total) {

    public DuelCount {
        if (lost < 0 || lost > total) {
            throw new IllegalArgumentException("lost duels must be between 0 and the total " + total
                    + ", were " + lost);
        }
    }

    public double lostShare() {
        if (total == 0) {
            return 0.0;
        }
        return (double) lost / total;
    }
}
