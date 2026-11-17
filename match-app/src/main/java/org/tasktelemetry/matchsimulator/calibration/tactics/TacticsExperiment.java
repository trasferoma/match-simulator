package org.tasktelemetry.matchsimulator.calibration.tactics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import org.tasktelemetry.matchsimulator.calibration.LeagueGenerator;
import org.tasktelemetry.matchsimulator.coach.squad.Squad;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.live.LiveMatch;
import org.tasktelemetry.matchsimulator.live.MatchObserver;
import org.tasktelemetry.matchsimulator.live.PreparedMatch;
import org.tasktelemetry.matchsimulator.live.SilentObserver;
import org.tasktelemetry.matchsimulator.summary.MatchSummary;
import org.tasktelemetry.matchsimulator.summary.MatchSummaryCalculator;

final class TacticsExperiment {

    private static final String SEED_ALGORITHM = "L64X128MixRandom";
    private static final int TESTED_TEAM_INDEX = 0;
    private static final int OPPONENT_TEAM_INDEX = 1;

    private final TacticsConfig config;
    private final LeagueGenerator generator;
    private final TacticalMatchPreparation preparation;
    private final LiveMatch liveMatch;
    private final TacticVariants variants;
    private final MatchObserver silentObserver;
    private final MatchSummaryCalculator summaryCalculator;

    TacticsExperiment(TacticsConfig config, LeagueGenerator generator, TacticalMatchPreparation preparation,
            LiveMatch liveMatch, TacticVariants variants) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.generator = Objects.requireNonNull(generator, "generator must not be null");
        this.preparation = Objects.requireNonNull(preparation, "match preparation must not be null");
        this.liveMatch = Objects.requireNonNull(liveMatch, "live match must not be null");
        this.variants = Objects.requireNonNull(variants, "variants must not be null");
        this.silentObserver = new SilentObserver();
        this.summaryCalculator = new MatchSummaryCalculator();
    }

    List<VariantMeasurements> run() {
        List<TacticSample> samples = drawSamples();
        return variants.all().stream()
                .map(variant -> measure(variant, samples))
                .toList();
    }

    private List<TacticSample> drawSamples() {
        RandomGenerator seeds = newSeedSequence();
        List<TacticSample> samples = new ArrayList<>();
        for (int index = 0; index < config.samples(); index++) {
            TacticSample sample = drawSample(index, seeds);
            samples.add(sample);
        }
        return samples;
    }

    private RandomGenerator newSeedSequence() {
        RandomGeneratorFactory<RandomGenerator> factory = RandomGeneratorFactory.of(SEED_ALGORITHM);
        return factory.create(config.baseSeed());
    }

    private TacticSample drawSample(int index, RandomGenerator seeds) {
        long squadSeed = seeds.nextLong();
        long matchSeed = seeds.nextLong();
        MatchRandom squadRandom = new MatchRandom(squadSeed);
        Squad tested = generator.generateSquad(TESTED_TEAM_INDEX, config.strength(), squadRandom);
        Squad opponent = generator.generateSquad(OPPONENT_TEAM_INDEX, config.strength(), squadRandom);
        MatchSide testedSide = testedSideOf(index);
        return new TacticSample(tested, opponent, testedSide, matchSeed);
    }

    private MatchSide testedSideOf(int index) {
        boolean playsAtHome = index % 2 == 0;
        return playsAtHome ? MatchSide.HOME : MatchSide.AWAY;
    }

    private VariantMeasurements measure(TacticVariant variant, List<TacticSample> samples) {
        List<MatchMeasurement> measurements = samples.stream()
                .map(sample -> playAndMeasure(sample, variant))
                .toList();
        return new VariantMeasurements(variant, measurements);
    }

    private MatchMeasurement playAndMeasure(TacticSample sample, TacticVariant variant) {
        PreparedMatch prepared = prepareMatch(sample, variant);
        List<MatchEvent> events = liveMatch.play(prepared, sample.matchSeed(), silentObserver);
        MatchSummary summary = summaryCalculator.summarize(events);
        return MatchMeasurement.of(summary, sample.testedSide());
    }

    private PreparedMatch prepareMatch(TacticSample sample, TacticVariant variant) {
        TacticVariant opponentTactic = variants.reference();
        return switch (sample.testedSide()) {
            case HOME -> preparation.prepare(sample.tested(), variant, sample.opponent(), opponentTactic);
            case AWAY -> preparation.prepare(sample.opponent(), opponentTactic, sample.tested(), variant);
        };
    }
}
