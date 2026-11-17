package org.tasktelemetry.matchsimulator.coach.config;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;

public record BandResponse(int formationGrade, PlayStyle style) {

    public BandResponse {
        Objects.requireNonNull(style, "band response style must not be null");
    }
}
