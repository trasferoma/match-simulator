package org.tasktelemetry.matchsimulator.engine.config;

import java.util.Objects;

public record SetPieceConfig(
        DurationRange goalKickDuration,
        DurationRange cornerDuration,
        DurationRange freeKickDuration,
        double directFreeKickShare) {

    public SetPieceConfig {
        Objects.requireNonNull(goalKickDuration, "goal kick duration must not be null");
        Objects.requireNonNull(cornerDuration, "corner duration must not be null");
        Objects.requireNonNull(freeKickDuration, "free kick duration must not be null");
        requireShare(directFreeKickShare);
    }

    public static SetPieceConfig defaults() {
        DurationRange goalKickDuration = new DurationRange(10, 20);
        DurationRange cornerDuration = new DurationRange(25, 45);
        DurationRange freeKickDuration = new DurationRange(20, 40);
        double directFreeKickShare = 0.50;
        return new SetPieceConfig(goalKickDuration, cornerDuration, freeKickDuration, directFreeKickShare);
    }

    private static void requireShare(double share) {
        boolean isShare = share >= 0.0 && share <= 1.0;
        if (!isShare) {
            throw new IllegalArgumentException("direct free kick share must be between 0.0 and 1.0, was " + share);
        }
    }
}
