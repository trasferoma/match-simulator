package org.tasktelemetry.matchsimulator.calibration;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;

public final class CalibrationBench {

    private static final String DEFAULT_DIRECTORY = "data/calibration";

    private final CalibrationTargetsReader reader;
    private final LeagueSimulation simulation;
    private final LeagueMetrics metrics;

    public CalibrationBench(CalibrationTargetsReader reader, LeagueSimulation simulation, LeagueMetrics metrics) {
        this.reader = Objects.requireNonNull(reader, "reader must not be null");
        this.simulation = Objects.requireNonNull(simulation, "simulation must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    public static CalibrationBench standard() {
        BenchConfig config = BenchConfig.defaults();
        LeagueGenerator generator = new LeagueGenerator(config, RoleProfiles.defaults());
        EngineConfig engineConfig = EngineConfig.defaults();
        CalibrationTargetsReader reader = new CalibrationTargetsReader(config);
        LeagueSimulation simulation = new LeagueSimulation(config, generator, engineConfig);
        LeagueMetrics metrics = new LeagueMetrics();
        return new CalibrationBench(reader, simulation, metrics);
    }

    public static void main(String[] arguments) {
        String directoryName = arguments.length > 0 ? arguments[0] : DEFAULT_DIRECTORY;
        CalibrationBench bench = standard();
        CalibrationReport report = bench.run(Path.of(directoryName));
        PrintStream output = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        output.print(report.render());
    }

    public CalibrationReport run(Path directory) {
        CalibrationTargets targets = readTargets(directory);
        List<PlayedMatch> matches = simulateLeague();
        MeasuredLeague league = measureLeague(matches);
        return compareWithTargets(league, targets);
    }

    private CalibrationTargets readTargets(Path directory) {
        return reader.read(directory);
    }

    private List<PlayedMatch> simulateLeague() {
        return simulation.simulate();
    }

    private MeasuredLeague measureLeague(List<PlayedMatch> matches) {
        return metrics.measure(matches);
    }

    private CalibrationReport compareWithTargets(MeasuredLeague league, CalibrationTargets targets) {
        return CalibrationReport.compare(league, targets);
    }
}
