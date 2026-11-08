package org.tasktelemetry.matchsimulator.calibration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("calibration")
class CalibrationAcceptanceTest {

    @Test
    void theTenMetricsOfTheCriterionOfDoneFallInsideTheirBands() {
        Path directory = CalibrationDirectory.fromSystemProperty();
        CalibrationBench bench = CalibrationBench.standard();

        CalibrationReport report = bench.run(directory);

        List<BandedResult> outside = report.resultsOutsideTheBand();
        assertThat(outside)
                .as("metrics outside their band, full report:%n%s", report.render())
                .isEmpty();
    }
}
