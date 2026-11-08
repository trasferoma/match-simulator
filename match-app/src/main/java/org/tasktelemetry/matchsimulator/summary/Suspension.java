package org.tasktelemetry.matchsimulator.summary;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.state.Score;

public record Suspension(MatchSide responsibleSide, Score officialScore, MatchTime time) {

    public Suspension {
        Objects.requireNonNull(responsibleSide, "responsible side must not be null");
        Objects.requireNonNull(officialScore, "official score must not be null");
        Objects.requireNonNull(time, "suspension time must not be null");
    }
}
