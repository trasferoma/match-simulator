package org.tasktelemetry.matchsimulator.narration;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;

public record MatchSetups(TeamSetup home, TeamSetup away) {

    public MatchSetups {
        Objects.requireNonNull(home, "home setup must not be null");
        Objects.requireNonNull(away, "away setup must not be null");
    }

    public TeamSetup of(MatchSide side) {
        return switch (side) {
            case HOME -> home;
            case AWAY -> away;
        };
    }

    public MatchSetups withDeployment(MatchSide side, Deployment deployment) {
        TeamSetup changed = of(side).withDeployment(deployment);
        return replace(side, changed);
    }

    public MatchSetups withStyle(MatchSide side, PlayStyle style) {
        TeamSetup changed = of(side).withStyle(style);
        return replace(side, changed);
    }

    private MatchSetups replace(MatchSide side, TeamSetup setup) {
        return switch (side) {
            case HOME -> new MatchSetups(setup, away);
            case AWAY -> new MatchSetups(home, setup);
        };
    }
}
