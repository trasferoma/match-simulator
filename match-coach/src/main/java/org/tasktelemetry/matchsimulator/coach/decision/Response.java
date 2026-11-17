package org.tasktelemetry.matchsimulator.coach.decision;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;

public record Response(Direction direction, ChangeReason reason, ModuleChange moduleChange) {

    public Response {
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(moduleChange, "module change must not be null");
    }
}
