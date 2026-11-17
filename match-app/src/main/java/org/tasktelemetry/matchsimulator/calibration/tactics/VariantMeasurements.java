package org.tasktelemetry.matchsimulator.calibration.tactics;

import java.util.List;
import java.util.Objects;

public record VariantMeasurements(TacticVariant variant, List<MatchMeasurement> samples) {

    public VariantMeasurements {
        Objects.requireNonNull(variant, "variant must not be null");
        Objects.requireNonNull(samples, "samples must not be null");
        samples = List.copyOf(samples);
    }
}
