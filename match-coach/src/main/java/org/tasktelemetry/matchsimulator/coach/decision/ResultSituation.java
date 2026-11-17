package org.tasktelemetry.matchsimulator.coach.decision;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.coach.reading.GameFlow;

public record ResultSituation(
        int lead,
        int minute,
        GameFlow flow,
        boolean opponentUnbalanced,
        boolean opponentShortHanded,
        boolean ownSetupAboveInitial,
        AssessmentBand band) {

    public ResultSituation {
        Objects.requireNonNull(flow, "game flow must not be null");
        Objects.requireNonNull(band, "assessment band must not be null");
    }

    public boolean isDominated() {
        return flow == GameFlow.DOMINATED;
    }

    public boolean isDominant() {
        return flow == GameFlow.DOMINANT;
    }

    public boolean isFavoured() {
        return band == AssessmentBand.STRONGER || band == AssessmentBand.MUCH_STRONGER;
    }
}
