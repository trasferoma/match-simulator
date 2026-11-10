package org.tasktelemetry.matchsimulator.calibration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.calibration.BandedResult.BandOrigin;
import org.tasktelemetry.matchsimulator.calibration.BandedResult.CriterionStanding;

public record CalibrationReport(List<BandedResult> results, Map<String, Double> orientation) {

    private static final String ATTRIBUTION = "Data: StatsBomb";

    public CalibrationReport {
        Objects.requireNonNull(results, "results must not be null");
        Objects.requireNonNull(orientation, "orientation must not be null");
        results = List.copyOf(results);
        Map<String, Double> orientationCopy = new LinkedHashMap<>(orientation);
        orientation = Collections.unmodifiableMap(orientationCopy);
    }

    public static CalibrationReport compare(MeasuredLeague league, CalibrationTargets targets) {
        Objects.requireNonNull(league, "measured league must not be null");
        Objects.requireNonNull(targets, "calibration targets must not be null");
        List<BandedResult> results = new ArrayList<>();
        for (BandedMetric metric : BandedMetric.values()) {
            double value = league.values().get(metric);
            Band band = targets.bands().get(metric);
            BandedResult result = new BandedResult(metric.label(), value, band, BandOrigin.READ,
                    CriterionStanding.COUNTS);
            results.add(result);
        }
        BandedResult lowPossession = new BandedResult("possesso, 10 percentile", league.possessionP10(),
                targets.possessionLow(), BandOrigin.COMPUTED, CriterionStanding.GUIDANCE);
        BandedResult highPossession = new BandedResult("possesso, 90 percentile", league.possessionP90(),
                targets.possessionHigh(), BandOrigin.COMPUTED, CriterionStanding.GUIDANCE);
        results.add(lowPossession);
        results.add(highPossession);
        return new CalibrationReport(results, league.orientation());
    }

    public List<BandedResult> criterionResultsOutsideTheBand() {
        return results.stream().filter(BandedResult::isOutsideTheBandAndCounts).toList();
    }

    public String render() {
        StringBuilder text = new StringBuilder();
        appendLine(text, "Metriche con forbice");
        for (BandedResult result : results) {
            String line = result.render();
            appendLine(text, line);
        }
        appendLine(text, "");
        appendLine(text, "Orientamento (senza forbice)");
        for (Map.Entry<String, Double> entry : orientation.entrySet()) {
            String line = String.format(Locale.ITALY, "%-34s %9.2f", entry.getKey(), entry.getValue());
            appendLine(text, line);
        }
        appendLine(text, "");
        appendLine(text, ATTRIBUTION);
        return text.toString();
    }

    private void appendLine(StringBuilder text, String line) {
        text.append(line).append(System.lineSeparator());
    }
}
