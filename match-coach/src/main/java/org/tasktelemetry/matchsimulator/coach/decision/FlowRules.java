package org.tasktelemetry.matchsimulator.coach.decision;

import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;

public final class FlowRules {

    public Optional<Response> responseTo(ResultSituation situation) {
        if (situation.lead() == 0 && situation.isDominated()) {
            Response prudence = new Response(Direction.MORE_PRUDENT, ChangeReason.WEATHER_PRESSURE,
                    ModuleChange.NONE);
            return Optional.of(prudence);
        }
        return Optional.empty();
    }
}
