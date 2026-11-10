package org.tasktelemetry.matchsimulator.calibration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("calibration")
class ContourEventsPresenceTest {

    private static final Map<String, Double> ORIENTATION = measureOrientation();

    @ParameterizedTest
    @ValueSource(strings = {"falli a partita", "gialli a partita", "rossi diretti a partita", "secondi gialli a partita",
            "rigori a partita",
            "fuorigioco a partita", "infortuni a partita", "cambi a partita", "angoli a partita",
            "cross a partita", "cross deviati in angolo a partita"})
    void everyContourEventAppearsAtLeastOnceInTheLeagueOfTheBench(String metric) {
        assertThat(ORIENTATION).containsKey(metric);
        assertThat(ORIENTATION.get(metric))
                .as("%s in the simulated league, full orientation: %s", metric, ORIENTATION)
                .isPositive();
    }

    private static Map<String, Double> measureOrientation() {
        Path directory = CalibrationDirectory.fromSystemProperty();
        CalibrationBench bench = CalibrationBench.standard();
        CalibrationReport report = bench.run(directory);
        return report.orientation();
    }
}
