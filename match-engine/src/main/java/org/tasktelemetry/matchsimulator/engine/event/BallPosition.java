package org.tasktelemetry.matchsimulator.engine.event;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;

public record BallPosition(MatchSide possession, ZoneRole zoneRole) {

    public BallPosition {
        Objects.requireNonNull(possession, "ball possession must not be null");
        Objects.requireNonNull(zoneRole, "ball zone role must not be null");
    }
}
