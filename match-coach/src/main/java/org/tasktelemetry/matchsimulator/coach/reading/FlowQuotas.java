package org.tasktelemetry.matchsimulator.coach.reading;

import java.util.stream.DoubleStream;

public record FlowQuotas(double expectedGoals, double shots, double possession, double attackingTime) {

    public double dominanceIndex() {
        return DoubleStream.of(expectedGoals, shots, possession, attackingTime)
                .average()
                .orElseThrow();
    }
}
