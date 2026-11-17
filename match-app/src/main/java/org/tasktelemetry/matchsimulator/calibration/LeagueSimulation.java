package org.tasktelemetry.matchsimulator.calibration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import org.tasktelemetry.matchsimulator.coach.squad.Squad;
import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.live.LiveMatch;
import org.tasktelemetry.matchsimulator.live.MatchObserver;
import org.tasktelemetry.matchsimulator.live.MatchPreparation;
import org.tasktelemetry.matchsimulator.live.PreparedMatch;
import org.tasktelemetry.matchsimulator.live.SilentObserver;
import org.tasktelemetry.matchsimulator.summary.MatchSummary;
import org.tasktelemetry.matchsimulator.summary.MatchSummaryCalculator;

public final class LeagueSimulation {

    private static final String SEED_ALGORITHM = "L64X128MixRandom";

    private final BenchConfig config;
    private final LeagueGenerator generator;
    private final MatchPreparation preparation;
    private final LiveMatch liveMatch;
    private final MatchObserver silentObserver;
    private final MatchSummaryCalculator summaryCalculator;

    public LeagueSimulation(BenchConfig config, LeagueGenerator generator, MatchPreparation preparation,
            EngineConfig engineConfig) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.generator = Objects.requireNonNull(generator, "generator must not be null");
        this.preparation = Objects.requireNonNull(preparation, "match preparation must not be null");
        Objects.requireNonNull(engineConfig, "engine configuration must not be null");
        MatchEngine engine = new MatchEngine(engineConfig);
        this.liveMatch = new LiveMatch(engine);
        this.silentObserver = new SilentObserver();
        this.summaryCalculator = new MatchSummaryCalculator();
    }

    public List<PlayedMatch> simulate() {
        RandomGenerator seeds = newSeedSequence();
        List<PlayedMatch> played = new ArrayList<>();
        for (int season = 0; season < config.seasons(); season++) {
            List<PlayedMatch> seasonMatches = simulateSeason(seeds);
            played.addAll(seasonMatches);
        }
        return played;
    }

    private RandomGenerator newSeedSequence() {
        RandomGeneratorFactory<RandomGenerator> factory = RandomGeneratorFactory.of(SEED_ALGORITHM);
        return factory.create(config.baseSeed());
    }

    private List<PlayedMatch> simulateSeason(RandomGenerator seeds) {
        long leagueSeed = seeds.nextLong();
        List<Squad> squads = generator.generate(leagueSeed);
        List<PlayedMatch> played = new ArrayList<>();
        for (Squad home : squads) {
            for (Squad away : squads) {
                if (home != away) {
                    long matchSeed = seeds.nextLong();
                    PlayedMatch playedMatch = playMatch(home, away, matchSeed);
                    played.add(playedMatch);
                }
            }
        }
        return played;
    }

    private PlayedMatch playMatch(Squad home, Squad away, long seed) {
        PreparedMatch prepared = preparation.prepare(home, away);
        List<MatchEvent> events = liveMatch.play(prepared, seed, silentObserver);
        MatchSummary summary = summaryCalculator.summarize(events);
        VoluntaryInterventions interventions = VoluntaryInterventions.from(events);
        return new PlayedMatch(home.name(), away.name(), summary, interventions);
    }
}
