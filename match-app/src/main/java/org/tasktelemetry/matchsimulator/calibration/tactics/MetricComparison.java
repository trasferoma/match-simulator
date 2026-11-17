package org.tasktelemetry.matchsimulator.calibration.tactics;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

public record MetricComparison(TacticMetric metric, double mean, double meanDifference,
        double differenceStandardError) {

    public MetricComparison {
        Objects.requireNonNull(metric, "metric must not be null");
    }

    public static MetricComparison of(TacticMetric metric, List<MatchMeasurement> reference,
            List<MatchMeasurement> variant) {
        double[] values = valuesOf(metric, variant);
        double[] differences = pairedDifferences(metric, reference, variant);
        double mean = meanOf(values);
        double meanDifference = meanOf(differences);
        double standardError = standardErrorOf(differences, meanDifference);
        return new MetricComparison(metric, mean, meanDifference, standardError);
    }

    private static double[] valuesOf(TacticMetric metric, List<MatchMeasurement> measurements) {
        return measurements.stream()
                .mapToDouble(measurement -> measurement.valueOf(metric))
                .toArray();
    }

    private static double[] pairedDifferences(TacticMetric metric, List<MatchMeasurement> reference,
            List<MatchMeasurement> variant) {
        return IntStream.range(0, variant.size())
                .mapToDouble(index -> variant.get(index).valueOf(metric) - reference.get(index).valueOf(metric))
                .toArray();
    }

    private static double meanOf(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private static double standardErrorOf(double[] differences, double meanDifference) {
        double squaredDeviations = 0.0;
        for (double difference : differences) {
            double deviation = difference - meanDifference;
            squaredDeviations += deviation * deviation;
        }
        double variance = squaredDeviations / (differences.length - 1);
        return Math.sqrt(variance / differences.length);
    }
}
