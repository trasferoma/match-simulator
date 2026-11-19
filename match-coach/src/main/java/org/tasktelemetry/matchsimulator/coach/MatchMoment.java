package org.tasktelemetry.matchsimulator.coach;

import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.PreviewVerdict;

public record MatchMoment(boolean ballLive, InterventionPreview preview) {

    @FunctionalInterface
    public interface InterventionPreview {

        List<PreviewVerdict> preview(List<Intervention> requests);
    }

    public MatchMoment {
        Objects.requireNonNull(preview, "intervention preview must not be null");
    }
}
