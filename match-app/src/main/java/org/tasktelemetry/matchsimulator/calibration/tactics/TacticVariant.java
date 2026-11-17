package org.tasktelemetry.matchsimulator.calibration.tactics;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.coach.config.Formation;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;

public record TacticVariant(PlayStyle style, Formation formation) {

    public TacticVariant {
        Objects.requireNonNull(style, "style must not be null");
        Objects.requireNonNull(formation, "formation must not be null");
    }
}
