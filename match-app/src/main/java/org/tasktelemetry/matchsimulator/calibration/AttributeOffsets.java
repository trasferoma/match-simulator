package org.tasktelemetry.matchsimulator.calibration;

import java.util.stream.IntStream;

public record AttributeOffsets(
        int passing,
        int dribbling,
        int shooting,
        int tackling,
        int positioning,
        int pace,
        int aerial,
        int goalkeeping) {

    public AttributeOffsets {
        int sum = IntStream.of(passing, dribbling, shooting, tackling, positioning, pace, aerial, goalkeeping).sum();
        if (sum != 0) {
            throw new IllegalArgumentException("attribute offsets of a profile must add up to zero, added up to "
                    + sum);
        }
    }
}
