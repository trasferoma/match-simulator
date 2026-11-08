package org.tasktelemetry.matchsimulator.calibration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CalibrationReportTest {

    private final Map<BandedMetric, Double> values = new EnumMap<>(BandedMetric.class);
    private final Map<BandedMetric, Band> bands = new EnumMap<>(BandedMetric.class);
    private final Map<String, Double> orientation = new LinkedHashMap<>();

    @Test
    void everyMetricWithABandIsPrintedWithValueBandAndVerdict() {
        CalibrationReport report = reportWithGoalsAboveTheBand();

        String text = report.render();

        assertThat(report.results()).hasSize(11);
        assertThat(lineOf(text, "gol a partita")).contains("4,50").contains("2,00").contains("3,00")
                .contains("letta").endsWith("FUORI");
        assertThat(lineOf(text, "gol in casa")).contains("1,50").endsWith("DENTRO");
    }

    @Test
    void thePossessionRowsShowAComputedBand() {
        CalibrationReport report = reportWithGoalsAboveTheBand();

        String text = report.render();

        assertThat(lineOf(text, "possesso, 10 percentile")).contains("calcolata").contains("34,00")
                .contains("32,00").contains("38,00").endsWith("DENTRO");
        assertThat(lineOf(text, "possesso, 90 percentile")).contains("calcolata").endsWith("FUORI");
    }

    @Test
    void theMetricsWithoutABandArePrintedAsOrientation() {
        CalibrationReport report = reportWithGoalsAboveTheBand();

        String text = report.render();

        assertThat(text).contains("Orientamento");
        assertThat(lineOf(text, "angoli a partita")).contains("9,25");
    }

    @Test
    void theStatsBombAttributionIsPrinted() {
        CalibrationReport report = reportWithGoalsAboveTheBand();

        assertThat(report.render()).contains("Data: StatsBomb");
    }

    @Test
    void theResultsOutsideTheBandAreListed() {
        CalibrationReport report = reportWithGoalsAboveTheBand();

        assertThat(report.resultsOutsideTheBand())
                .extracting(BandedResult::label)
                .containsExactly("gol a partita", "possesso, 90 percentile");
    }

    private CalibrationReport reportWithGoalsAboveTheBand() {
        for (BandedMetric metric : BandedMetric.values()) {
            values.put(metric, 1.5);
            bands.put(metric, new Band(1.0, 2.0));
        }
        values.put(BandedMetric.GOALS_PER_GAME, 4.5);
        bands.put(BandedMetric.GOALS_PER_GAME, new Band(2.0, 3.0));
        orientation.put("angoli a partita", 9.25);
        Band possessionLow = new Band(32.0, 38.0);
        Band possessionHigh = new Band(62.0, 68.0);
        MeasuredLeague league = new MeasuredLeague(values, 34.0, 55.0, orientation);
        CalibrationTargets targets = new CalibrationTargets(bands, possessionLow, possessionHigh);
        return CalibrationReport.compare(league, targets);
    }

    private String lineOf(String text, String label) {
        return text.lines()
                .filter(line -> line.startsWith(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no line starting with '" + label + "' in:\n" + text));
    }
}
