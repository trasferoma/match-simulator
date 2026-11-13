package org.tasktelemetry.matchsimulator.cli;

import java.util.List;

import org.tasktelemetry.matchsimulator.engine.Match;
import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.Team;
import org.tasktelemetry.matchsimulator.narration.ChronicleMode;
import org.tasktelemetry.matchsimulator.narration.NarratedEventFilter;
import org.tasktelemetry.matchsimulator.narration.Narrator;
import org.tasktelemetry.matchsimulator.narration.TeamNames;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightSegment;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsConfig;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsNarrator;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsSelector;
import org.tasktelemetry.matchsimulator.narration.highlights.NarratedSegment;
import org.tasktelemetry.matchsimulator.summary.MatchSummary;
import org.tasktelemetry.matchsimulator.summary.MatchSummaryCalculator;

final class MatchRunner {

    private final MatchEngine engine;
    private final Narrator narrator;
    private final MatchSummaryCalculator summaryCalculator;
    private final HighlightsSelector highlightsSelector;
    private final HighlightsNarrator highlightsNarrator;

    MatchRunner(MatchEngine engine, Narrator narrator, MatchSummaryCalculator summaryCalculator,
            HighlightsSelector highlightsSelector, HighlightsNarrator highlightsNarrator) {
        this.engine = engine;
        this.narrator = narrator;
        this.summaryCalculator = summaryCalculator;
        this.highlightsSelector = highlightsSelector;
        this.highlightsNarrator = highlightsNarrator;
    }

    static MatchRunner standard(HighlightsConfig highlightsConfig) {
        MatchEngine engine = new MatchEngine(EngineConfig.defaults());
        Narrator narrator = Narrator.italian();
        MatchSummaryCalculator summaryCalculator = new MatchSummaryCalculator();
        NarratedEventFilter eventFilter = new NarratedEventFilter();
        HighlightsSelector highlightsSelector = new HighlightsSelector(highlightsConfig, eventFilter);
        HighlightsNarrator highlightsNarrator = HighlightsNarrator.italian();
        return new MatchRunner(engine, narrator, summaryCalculator, highlightsSelector, highlightsNarrator);
    }

    MatchReport play(Team home, Team away, long seed, ChronicleMode mode) {
        List<MatchEvent> events = simulateToTheEnd(home, away, seed);
        TeamNames teamNames = new TeamNames(home.name(), away.name());
        List<List<String>> chronicle = narrator.narrate(events, teamNames, mode);
        MatchSummary summary = summaryCalculator.summarize(events);
        return new MatchReport(home.name(), away.name(), chronicle, summary, seed);
    }

    HighlightsReport playHighlights(Team home, Team away, long seed) {
        List<MatchEvent> events = simulateToTheEnd(home, away, seed);
        TeamNames teamNames = new TeamNames(home.name(), away.name());
        List<HighlightSegment> selected = highlightsSelector.select(events);
        List<NarratedSegment> segments = highlightsNarrator.narrate(selected, events, teamNames);
        MatchSummary summary = summaryCalculator.summarize(events);
        Scoreboard scoreboard = new Scoreboard(home.name(), away.name(), summary, seed);
        return new HighlightsReport(segments, scoreboard);
    }

    private List<MatchEvent> simulateToTheEnd(Team home, Team away, long seed) {
        Match match = engine.start(home, away, seed);
        while (!match.isFinished()) {
            match.step();
        }
        return match.events();
    }
}
