package org.tasktelemetry.matchsimulator.calibration.tactics;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.summary.MatchSummary;

public record MatchMeasurement(Map<TacticMetric, Double> values) {

    public MatchMeasurement {
        Objects.requireNonNull(values, "values must not be null");
        Map<TacticMetric, Double> valuesCopy = new EnumMap<>(TacticMetric.class);
        valuesCopy.putAll(values);
        values = Collections.unmodifiableMap(valuesCopy);
    }

    public static MatchMeasurement of(MatchSummary summary, MatchSide side) {
        Objects.requireNonNull(summary, "match summary must not be null");
        Objects.requireNonNull(side, "side must not be null");
        Map<TacticMetric, Double> values = new EnumMap<>(TacticMetric.class);
        for (TacticMetric metric : TacticMetric.values()) {
            double value = metric.valueIn(summary, side);
            values.put(metric, value);
        }
        return new MatchMeasurement(values);
    }

    public double valueOf(TacticMetric metric) {
        return values.get(metric);
    }
}
