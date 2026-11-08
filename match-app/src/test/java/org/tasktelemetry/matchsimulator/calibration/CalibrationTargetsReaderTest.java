package org.tasktelemetry.matchsimulator.calibration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CalibrationTargetsReaderTest {

    private static final double TOLERANCE = 1e-9;

    private final Path directory = CalibrationDirectory.fromSystemProperty();

    @Test
    void theNineBandedMetricsAreReadFromTheFiles() {
        CalibrationTargets targets = readerWithTolerance(3.0).read(directory);

        assertBand(targets, BandedMetric.GOALS_PER_GAME, 2.5547, 3.1232);
        assertBand(targets, BandedMetric.HOME_GOALS_PER_GAME, 1.4366, 1.7644);
        assertBand(targets, BandedMetric.AWAY_GOALS_PER_GAME, 1.0863, 1.3569);
        assertBand(targets, BandedMetric.HOME_WIN_PCT, 41.7895, 48.4211);
        assertBand(targets, BandedMetric.DRAW_PCT, 22.0789, 28.9474);
        assertBand(targets, BandedMetric.AWAY_WIN_PCT, 27.3158, 33.7105);
        assertBand(targets, BandedMetric.SHOTS_PER_GAME, 23.7347, 26.3793);
        assertBand(targets, BandedMetric.HOME_SHOTS_PER_GAME, 13.0361, 14.5938);
        assertBand(targets, BandedMetric.AWAY_SHOTS_PER_GAME, 10.4297, 11.871);
    }

    @Test
    void thePossessionBandsAreThePercentilesOfTheFileMovedByTheTolerance() {
        CalibrationTargets targets = readerWithTolerance(3.0).read(directory);

        assertThat(targets.possessionLow().low()).isCloseTo(34.7134 - 3.0, within(TOLERANCE));
        assertThat(targets.possessionLow().high()).isCloseTo(34.7134 + 3.0, within(TOLERANCE));
        assertThat(targets.possessionHigh().low()).isCloseTo(65.2866 - 3.0, within(TOLERANCE));
        assertThat(targets.possessionHigh().high()).isCloseTo(65.2866 + 3.0, within(TOLERANCE));
    }

    @Test
    void thePossessionBandsChangeWhenTheToleranceChanges() {
        CalibrationTargets narrow = readerWithTolerance(1.0).read(directory);
        CalibrationTargets wide = readerWithTolerance(5.0).read(directory);

        assertThat(narrow.possessionLow().low()).isCloseTo(34.7134 - 1.0, within(TOLERANCE));
        assertThat(wide.possessionLow().low()).isCloseTo(34.7134 - 5.0, within(TOLERANCE));
        assertThat(narrow.possessionHigh().high()).isCloseTo(65.2866 + 1.0, within(TOLERANCE));
        assertThat(wide.possessionHigh().high()).isCloseTo(65.2866 + 5.0, within(TOLERANCE));
    }

    @Test
    void aMissingFileIsReportedWithItsPath() {
        Path emptyDirectory = directory.resolve("no-such-subdirectory");

        assertThatThrownBy(() -> readerWithTolerance(3.0).read(emptyDirectory))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("big5-targets.json");
    }

    @Test
    void aBandWithoutNumbersIsRejectedNamingFileAndField(@TempDir Path temporary) throws IOException {
        copyFile("big5-match-stats-targets.json", temporary);
        copyFile("statsbomb-1516-targets.json", temporary);
        String malformed = "{\"metrics\": [{\"key\": \"goals_per_game\", \"high\": 3.1}]}";
        Files.writeString(temporary.resolve("big5-targets.json"), malformed);

        assertThatThrownBy(() -> readerWithTolerance(3.0).read(temporary))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("low of goals_per_game")
                .hasMessageContaining("big5-targets.json");
    }

    @Test
    void aMissingPossessionPercentileIsRejectedNamingFileAndField(@TempDir Path temporary) throws IOException {
        copyFile("big5-targets.json", temporary);
        copyFile("big5-match-stats-targets.json", temporary);
        Files.writeString(temporary.resolve("statsbomb-1516-targets.json"), "{}");

        assertThatThrownBy(() -> readerWithTolerance(3.0).read(temporary))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("possession percentile p10")
                .hasMessageContaining("statsbomb-1516-targets.json");
    }

    private void copyFile(String name, Path target) throws IOException {
        Files.copy(directory.resolve(name), target.resolve(name), StandardCopyOption.REPLACE_EXISTING);
    }

    private CalibrationTargetsReader readerWithTolerance(double tolerance) {
        BenchConfig defaults = BenchConfig.defaults();
        BenchConfig config = new BenchConfig(defaults.teamCount(), defaults.weakestStrength(),
                defaults.strongestStrength(), defaults.seasons(), defaults.baseSeed(), defaults.individualVariation(),
                tolerance);
        return new CalibrationTargetsReader(config);
    }

    private void assertBand(CalibrationTargets targets, BandedMetric metric, double low, double high) {
        Band band = targets.bands().get(metric);
        assertThat(band.low()).as("low of %s", metric).isCloseTo(low, within(TOLERANCE));
        assertThat(band.high()).as("high of %s", metric).isCloseTo(high, within(TOLERANCE));
    }
}
