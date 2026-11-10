package org.tasktelemetry.matchsimulator.cli;

import java.util.List;

import org.tasktelemetry.matchsimulator.engine.Match;
import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.Team;
import org.tasktelemetry.matchsimulator.narration.ChronicleMode;
import org.tasktelemetry.matchsimulator.narration.Narrator;
import org.tasktelemetry.matchsimulator.narration.TeamNames;
import org.tasktelemetry.matchsimulator.summary.MatchSummary;
import org.tasktelemetry.matchsimulator.summary.MatchSummaryCalculator;

final class MatchRunner {

    private final MatchEngine engine;
    private final Narrator narrator;
    private final MatchSummaryCalculator summaryCalculator;

    MatchRunner(MatchEngine engine, Narrator narrator, MatchSummaryCalculator summaryCalculator) {
        this.engine = engine;
        this.narrator = narrator;
        this.summaryCalculator = summaryCalculator;
    }

    static MatchRunner standard() {
        MatchEngine engine = new MatchEngine(EngineConfig.defaults());
        Narrator narrator = Narrator.italian();
        MatchSummaryCalculator summaryCalculator = new MatchSummaryCalculator();
        return new MatchRunner(engine, narrator, summaryCalculator);
    }

    MatchReport play(Team home, Team away, long seed, ChronicleMode mode) {
        List<MatchEvent> events = simulateToTheEnd(home, away, seed);
        TeamNames teamNames = new TeamNames(home.name(), away.name());
        List<String> chronicle = narrator.narrate(events, teamNames, mode);
        MatchSummary summary = summaryCalculator.summarize(events);
        return new MatchReport(home.name(), away.name(), chronicle, summary, seed);
    }

    private List<MatchEvent> simulateToTheEnd(Team home, Team away, long seed) {
        Match match = engine.start(home, away, seed);
        while (!match.isFinished()) {
            match.step();
        }
        return match.events();
    }
}
