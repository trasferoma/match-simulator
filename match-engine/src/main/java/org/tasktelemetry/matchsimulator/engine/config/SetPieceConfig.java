package org.tasktelemetry.matchsimulator.engine.config;

import java.util.Objects;

public record SetPieceConfig(DurationRange goalKickDuration, DurationRange cornerDuration) {

    public SetPieceConfig {
        Objects.requireNonNull(goalKickDuration, "goal kick duration must not be null");
        Objects.requireNonNull(cornerDuration, "corner duration must not be null");
    }

    public static SetPieceConfig defaults() {
        DurationRange goalKickDuration = new DurationRange(10, 20);
        DurationRange cornerDuration = new DurationRange(25, 45);
        return new SetPieceConfig(goalKickDuration, cornerDuration);
    }
}
