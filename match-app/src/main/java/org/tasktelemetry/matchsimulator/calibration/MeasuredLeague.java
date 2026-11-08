package org.tasktelemetry.matchsimulator.calibration;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record MeasuredLeague(
        Map<BandedMetric, Double> values,
        double possessionP10,
        double possessionP90,
        Map<String, Double> orientation) {

    public MeasuredLeague {
        Objects.requireNonNull(values, "values must not be null");
        Objects.requireNonNull(orientation, "orientation must not be null");
        Map<BandedMetric, Double> valuesCopy = new EnumMap<>(BandedMetric.class);
        valuesCopy.putAll(values);
        for (BandedMetric metric : BandedMetric.values()) {
            if (valuesCopy.get(metric) == null) {
                throw new IllegalArgumentException("measured league needs a value for " + metric);
            }
        }
        values = Collections.unmodifiableMap(valuesCopy);
        Map<String, Double> orientationCopy = new LinkedHashMap<>(orientation);
        orientation = Collections.unmodifiableMap(orientationCopy);
    }
}
