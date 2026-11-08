package org.tasktelemetry.matchsimulator.calibration;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record CalibrationTargets(Map<BandedMetric, Band> bands, Band possessionLow, Band possessionHigh) {

    public CalibrationTargets {
        Objects.requireNonNull(bands, "bands must not be null");
        Objects.requireNonNull(possessionLow, "possession band of the 10th percentile must not be null");
        Objects.requireNonNull(possessionHigh, "possession band of the 90th percentile must not be null");
        Map<BandedMetric, Band> copy = new EnumMap<>(BandedMetric.class);
        copy.putAll(bands);
        for (BandedMetric metric : BandedMetric.values()) {
            if (copy.get(metric) == null) {
                throw new IllegalArgumentException("calibration targets need a band for " + metric);
            }
        }
        bands = Collections.unmodifiableMap(copy);
    }
}
