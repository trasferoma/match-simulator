package org.tasktelemetry.matchsimulator.cli;

import java.util.List;

import org.tasktelemetry.matchsimulator.engine.Match;
import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.Team;
import org.tasktelemetry.matchsimulator.live.LiveMatch;
import org.tasktelemetry.matchsimulator.narration.ChronicleMode;
import org.tasktelemetry.matchsimulator.narration.NarratedEventFilter;
import org.tasktelemetry.matchsimulator.narration.Narrator;
import org.tasktelemetry.matchsimulator.narration.TeamNames;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsConfig;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsListener;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsNarrator;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsSelector;
import org.tasktelemetry.matchsimulator.narration.highlights.LiveHighlights;
import org.tasktelemetry.matchsimulator.summary.MatchSummary;
import org.tasktelemetry.matchsimulator.summary.MatchSummaryCalculator;

final class MatchRunner {

    private final MatchEngine engine;
    private final Narrator narrator;
    private final MatchSummaryCalculator summaryCalculator;
    private final HighlightsSelector highlightsSelector;
    private final HighlightsNarrator highlightsNarrator;
    private final LiveMatch liveMatch;

    MatchRunner(MatchEngine engine, Narrator narrator, MatchSummaryCalculator summaryCalculator,
            HighlightsSelector highlightsSelector, HighlightsNarrator highlightsNarrator, LiveMatch liveMatch) {
        this.engine = engine;
        this.narrator = narrator;
        this.summaryCalculator = summaryCalculator;
        this.highlightsSelector = highlightsSelector;
        this.highlightsNarrator = highlightsNarrator;
        this.liveMatch = liveMatch;
    }

    static MatchRunner standard(HighlightsConfig highlightsConfig) {
        MatchEngine engine = new MatchEngine(EngineConfig.defaults());
        Narrator narrator = Narrator.italian();
        MatchSummaryCalculator summaryCalculator = new MatchSummaryCalculator();
        NarratedEventFilter eventFilter = new NarratedEventFilter();
        HighlightsSelector highlightsSelector = new HighlightsSelector(highlightsConfig, eventFilter);
        HighlightsNarrator highlightsNarrator = HighlightsNarrator.italian();
        LiveMatch liveMatch = new LiveMatch(engine);
        return new MatchRunner(engine, narrator, summaryCalculator, highlightsSelector, highlightsNarrator,
                liveMatch);
    }

    MatchReport play(Team home, Team away, long seed, ChronicleMode mode) {
        List<MatchEvent> events = simulateToTheEnd(home, away, seed);
        TeamNames teamNames = new TeamNames(home.name(), away.name());
        List<List<String>> chronicle = narrator.narrate(events, teamNames, mode);
        MatchSummary summary = summaryCalculator.summarize(events);
        return new MatchReport(home.name(), away.name(), chronicle, summary, seed);
    }

    Scoreboard playLiveHighlights(Team home, Team away, long seed, HighlightsListener listener) {
        TeamNames teamNames = new TeamNames(home.name(), away.name());
        LiveHighlights liveHighlights = new LiveHighlights(highlightsSelector, highlightsNarrator, teamNames,
                listener);
        List<MatchEvent> events = liveMatch.play(home, away, seed, liveHighlights);
        MatchSummary summary = summaryCalculator.summarize(events);
        return new Scoreboard(home.name(), away.name(), summary, seed);
    }

    private List<MatchEvent> simulateToTheEnd(Team home, Team away, long seed) {
        Match match = engine.start(home, away, seed);
        while (!match.isFinished()) {
            match.step();
        }
        return match.events();
    }
}
