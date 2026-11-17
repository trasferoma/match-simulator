package org.tasktelemetry.matchsimulator.narration;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;

public record TeamSetup(Deployment deployment, PlayStyle style) {

    public TeamSetup {
        Objects.requireNonNull(deployment, "deployment must not be null");
        Objects.requireNonNull(style, "style must not be null");
    }

    public TeamSetup withDeployment(Deployment newDeployment) {
        return new TeamSetup(newDeployment, style);
    }

    public TeamSetup withStyle(PlayStyle newStyle) {
        return new TeamSetup(deployment, newStyle);
    }
}
