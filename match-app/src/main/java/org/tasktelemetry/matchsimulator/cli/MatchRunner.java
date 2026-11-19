package org.tasktelemetry.matchsimulator.cli;

import java.util.List;

import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.live.LiveMatch;
import org.tasktelemetry.matchsimulator.live.MatchObserver;
import org.tasktelemetry.matchsimulator.live.PreparedMatch;
import org.tasktelemetry.matchsimulator.live.SilentObserver;
import org.tasktelemetry.matchsimulator.narration.ChronicleMode;
import org.tasktelemetry.matchsimulator.narration.NarratedEventFilter;
import org.tasktelemetry.matchsimulator.narration.Narrator;
import org.tasktelemetry.matchsimulator.narration.MatchSetups;
import org.tasktelemetry.matchsimulator.narration.TeamNames;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsConfig;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsListener;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsNarrator;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsSelector;
import org.tasktelemetry.matchsimulator.narration.highlights.LiveHighlights;
import org.tasktelemetry.matchsimulator.summary.MatchSummary;
import org.tasktelemetry.matchsimulator.summary.MatchSummaryCalculator;

final class MatchRunner {

    private final Narrator narrator;
    private final MatchSummaryCalculator summaryCalculator;
    private final HighlightsSelector highlightsSelector;
    private final HighlightsNarrator highlightsNarrator;
    private final LiveMatch liveMatch;
    private final MatchObserver silentObserver;

    MatchRunner(Narrator narrator, MatchSummaryCalculator summaryCalculator, HighlightsSelector highlightsSelector,
            HighlightsNarrator highlightsNarrator, LiveMatch liveMatch, MatchObserver silentObserver) {
        this.narrator = narrator;
        this.summaryCalculator = summaryCalculator;
        this.highlightsSelector = highlightsSelector;
        this.highlightsNarrator = highlightsNarrator;
        this.liveMatch = liveMatch;
        this.silentObserver = silentObserver;
    }

    static MatchRunner standard(HighlightsConfig highlightsConfig) {
        MatchEngine engine = new MatchEngine(EngineConfig.defaults());
        Narrator narrator = Narrator.italian();
        MatchSummaryCalculator summaryCalculator = new MatchSummaryCalculator();
        NarratedEventFilter eventFilter = new NarratedEventFilter();
        HighlightsSelector highlightsSelector = new HighlightsSelector(highlightsConfig, eventFilter);
        HighlightsNarrator highlightsNarrator = HighlightsNarrator.italian();
        LiveMatch liveMatch = new LiveMatch(engine);
        MatchObserver silentObserver = new SilentObserver();
        return new MatchRunner(narrator, summaryCalculator, highlightsSelector, highlightsNarrator, liveMatch,
                silentObserver);
    }

    MatchReport play(PreparedMatch match, long seed, ChronicleMode mode) {
        List<MatchEvent> events = liveMatch.play(match, seed, silentObserver);
        TeamNames teamNames = match.names();
        MatchSetups openingSetups = match.openingSetups();
        List<List<String>> chronicle = narrator.narrate(events, teamNames, openingSetups, mode);
        MatchSummary summary = summaryCalculator.summarize(events);
        return new MatchReport(teamNames.home(), teamNames.away(), chronicle, summary, seed);
    }

    Scoreboard playLiveHighlights(PreparedMatch match, long seed, HighlightsListener listener) {
        LiveHighlights liveHighlights = createLiveHighlights(match, listener);
        return playLive(match, seed, liveHighlights);
    }

    LiveHighlights createLiveHighlights(PreparedMatch match, HighlightsListener listener) {
        TeamNames teamNames = match.names();
        MatchSetups openingSetups = match.openingSetups();
        return new LiveHighlights(highlightsSelector, highlightsNarrator, teamNames, openingSetups, listener);
    }

    Scoreboard playLive(PreparedMatch match, long seed, LiveHighlights liveHighlights) {
        TeamNames teamNames = match.names();
        List<MatchEvent> events = liveMatch.play(match, seed, liveHighlights);
        MatchSummary summary = summaryCalculator.summarize(events);
        return new Scoreboard(teamNames.home(), teamNames.away(), summary, seed);
    }
}
