package org.tasktelemetry.matchsimulator.calibration.tactics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.message.UserMessages;

class TacticsBenchTest {

    private static final int VARIANT_COUNT = 9;

    private final TacticsConfig fewSamples = new TacticsConfig(4, 68, 42L);

    @Test
    void everyVariantHasOneRowInTheBlockOfEveryMetric() {
        TacticsReport report = TacticsBench.standard(fewSamples).run();

        String text = report.render(UserMessages.italian());

        assertThat(report.comparisons()).hasSize(VARIANT_COUNT);
        long referenceRows = text.lines().filter(line -> line.contains("(riferimento)")).count();
        assertThat(referenceRows).isEqualTo(TacticMetric.values().length);
        for (TacticMetric metric : TacticMetric.values()) {
            assertThat(text).contains(metric.label());
        }
        long variantRows = text.lines().filter(line -> line.matches("  \\S.* \\d+-\\d+-\\d+.*")).count();
        assertThat(variantRows).isEqualTo((long) VARIANT_COUNT * TacticMetric.values().length);
    }

    @Test
    void theReferenceDiffersFromItselfByZeroWithZeroError() {
        TacticsReport report = TacticsBench.standard(fewSamples).run();

        VariantComparison reference = report.comparisons().get(0);

        for (TacticMetric metric : TacticMetric.values()) {
            MetricComparison comparison = reference.of(metric);
            assertThat(comparison.meanDifference()).isZero();
            assertThat(comparison.differenceStandardError()).isZero();
        }
    }

    @Test
    void theSameSeedGivesTheSameReport() {
        UserMessages messages = UserMessages.italian();

        String first = TacticsBench.standard(fewSamples).run().render(messages);
        String second = TacticsBench.standard(fewSamples).run().render(messages);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void anotherSeedGivesAnotherReport() {
        UserMessages messages = UserMessages.italian();
        TacticsConfig otherSeed = new TacticsConfig(4, 68, 43L);

        String first = TacticsBench.standard(fewSamples).run().render(messages);
        String second = TacticsBench.standard(otherSeed).run().render(messages);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void theDifferenceIsTheMeanOfThePairedDifferencesWithItsStandardError() {
        MatchMeasurement lowerReference = measurementWithPoints(0.0);
        MatchMeasurement upperReference = measurementWithPoints(1.0);
        MatchMeasurement lowerVariant = measurementWithPoints(1.0);
        MatchMeasurement upperVariant = measurementWithPoints(4.0);

        MetricComparison comparison = MetricComparison.of(TacticMetric.POINTS,
                List.of(lowerReference, upperReference), List.of(lowerVariant, upperVariant));

        assertThat(comparison.mean()).isEqualTo(2.5);
        assertThat(comparison.meanDifference()).isEqualTo(2.0);
        assertThat(comparison.differenceStandardError()).isEqualTo(1.0);
    }

    private MatchMeasurement measurementWithPoints(double points) {
        Map<TacticMetric, Double> values = new EnumMap<>(TacticMetric.class);
        for (TacticMetric metric : TacticMetric.values()) {
            values.put(metric, metric == TacticMetric.POINTS ? points : 0.0);
        }
        return new MatchMeasurement(values);
    }
}
