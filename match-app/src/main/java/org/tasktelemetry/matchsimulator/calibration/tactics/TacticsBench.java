package org.tasktelemetry.matchsimulator.calibration.tactics;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.calibration.BenchConfig;
import org.tasktelemetry.matchsimulator.calibration.LeagueGenerator;
import org.tasktelemetry.matchsimulator.calibration.RoleProfiles;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.live.LiveMatch;
import org.tasktelemetry.matchsimulator.message.UserMessages;

public final class TacticsBench {

    private final TacticsConfig config;
    private final TacticsExperiment experiment;

    TacticsBench(TacticsConfig config, TacticsExperiment experiment) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.experiment = Objects.requireNonNull(experiment, "experiment must not be null");
    }

    public static TacticsBench standard(TacticsConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        CoachConfig coachConfig = CoachConfig.defaults();
        BenchConfig benchConfig = BenchConfig.defaults();
        RoleProfiles profiles = RoleProfiles.defaults();
        LeagueGenerator generator = new LeagueGenerator(benchConfig, profiles);
        TacticalMatchPreparation preparation = new TacticalMatchPreparation(coachConfig);
        EngineConfig engineConfig = EngineConfig.defaults();
        MatchEngine engine = new MatchEngine(engineConfig);
        LiveMatch liveMatch = new LiveMatch(engine);
        TacticVariants variants = TacticVariants.from(coachConfig.formations());
        TacticsExperiment experiment = new TacticsExperiment(config, generator, preparation, liveMatch, variants);
        return new TacticsBench(config, experiment);
    }

    public static void main(String[] arguments) {
        TacticsConfig config = readConfig(arguments);
        TacticsBench bench = standard(config);
        TacticsReport report = bench.run();
        UserMessages messages = UserMessages.italian();
        PrintStream output = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        output.print(report.render(messages));
    }

    public TacticsReport run() {
        List<VariantMeasurements> measurements = experiment.run();
        return TacticsReport.compare(config, measurements);
    }

    private static TacticsConfig readConfig(String[] arguments) {
        TacticsConfig defaults = TacticsConfig.defaults();
        int samples = arguments.length > 0 ? Integer.parseInt(arguments[0]) : defaults.samples();
        int strength = arguments.length > 1 ? Integer.parseInt(arguments[1]) : defaults.strength();
        long baseSeed = arguments.length > 2 ? Long.parseLong(arguments[2]) : defaults.baseSeed();
        return new TacticsConfig(samples, strength, baseSeed);
    }
}
