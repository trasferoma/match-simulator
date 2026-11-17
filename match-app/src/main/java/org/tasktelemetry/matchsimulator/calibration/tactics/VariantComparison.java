package org.tasktelemetry.matchsimulator.calibration.tactics;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record VariantComparison(TacticVariant variant, Map<TacticMetric, MetricComparison> metrics) {

    public VariantComparison {
        Objects.requireNonNull(variant, "variant must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
        Map<TacticMetric, MetricComparison> metricsCopy = new EnumMap<>(TacticMetric.class);
        metricsCopy.putAll(metrics);
        metrics = Collections.unmodifiableMap(metricsCopy);
    }

    public static VariantComparison against(VariantMeasurements reference, VariantMeasurements measured) {
        Objects.requireNonNull(reference, "reference measurements must not be null");
        Objects.requireNonNull(measured, "measurements must not be null");
        Map<TacticMetric, MetricComparison> metrics = new EnumMap<>(TacticMetric.class);
        for (TacticMetric metric : TacticMetric.values()) {
            MetricComparison comparison = MetricComparison.of(metric, reference.samples(), measured.samples());
            metrics.put(metric, comparison);
        }
        return new VariantComparison(measured.variant(), metrics);
    }

    public MetricComparison of(TacticMetric metric) {
        return metrics.get(metric);
    }
}
