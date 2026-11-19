package org.tasktelemetry.matchsimulator.coach;

import org.tasktelemetry.matchsimulator.engine.intervention.PreviewVerdict;

public final class ScenarioMoments {

    private ScenarioMoments() {
    }

    public static MatchMoment deadBall() {
        return new MatchMoment(false, requests -> requests.stream().map(request -> PreviewVerdict.accepted()).toList());
    }
}
