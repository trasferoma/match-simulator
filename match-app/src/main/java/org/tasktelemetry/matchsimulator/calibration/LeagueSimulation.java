package org.tasktelemetry.matchsimulator.calibration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import org.tasktelemetry.matchsimulator.engine.Match;
import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.Team;
import org.tasktelemetry.matchsimulator.summary.MatchSummary;
import org.tasktelemetry.matchsimulator.summary.MatchSummaryCalculator;

public final class LeagueSimulation {

    private static final String SEED_ALGORITHM = "L64X128MixRandom";

    private final BenchConfig config;
    private final LeagueGenerator generator;
    private final MatchEngine engine;
    private final MatchSummaryCalculator summaryCalculator;

    public LeagueSimulation(BenchConfig config, LeagueGenerator generator, EngineConfig engineConfig) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.generator = Objects.requireNonNull(generator, "generator must not be null");
        Objects.requireNonNull(engineConfig, "engine configuration must not be null");
        this.engine = new MatchEngine(engineConfig);
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
        List<Team> teams = generator.generate(leagueSeed);
        List<PlayedMatch> played = new ArrayList<>();
        for (Team home : teams) {
            for (Team away : teams) {
                if (home != away) {
                    long matchSeed = seeds.nextLong();
                    PlayedMatch playedMatch = playMatch(home, away, matchSeed);
                    played.add(playedMatch);
                }
            }
        }
        return played;
    }

    private PlayedMatch playMatch(Team home, Team away, long seed) {
        List<MatchEvent> events = runToTheEnd(home, away, seed);
        MatchSummary summary = summaryCalculator.summarize(events);
        return new PlayedMatch(home.name(), away.name(), summary);
    }

    private List<MatchEvent> runToTheEnd(Team home, Team away, long seed) {
        Match match = engine.start(home, away, seed);
        while (!match.isFinished()) {
            match.step();
        }
        return match.events();
    }
}
