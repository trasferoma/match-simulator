package org.tasktelemetry.matchsimulator.cli;

import java.io.InputStream;
import java.io.PrintStream;

import org.tasktelemetry.matchsimulator.cli.dugout.BackgroundLineReader;
import org.tasktelemetry.matchsimulator.cli.dugout.ConsoleDugout;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.live.MatchPreparation;
import org.tasktelemetry.matchsimulator.live.PreparedMatch;
import org.tasktelemetry.matchsimulator.message.UserMessages;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsListener;
import org.tasktelemetry.matchsimulator.narration.highlights.LiveHighlights;

final class ControlledMatchRunner {

    private final MatchRunner runner;
    private final MatchPreparation preparation;
    private final UserMessages messages;
    private final PrintStream out;
    private final InputStream in;

    ControlledMatchRunner(MatchRunner runner, MatchPreparation preparation, UserMessages messages, PrintStream out,
            InputStream in) {
        this.runner = runner;
        this.preparation = preparation;
        this.messages = messages;
        this.out = out;
        this.in = in;
    }

    Scoreboard play(PreparedMatch match, MatchSide controlledSide, long seed, HighlightsListener listener) {
        LiveHighlights liveHighlights = runner.createLiveHighlights(match, listener);
        try (BackgroundLineReader lines = BackgroundLineReader.startedOn(in)) {
            PreparedMatch controlledMatch = handControlToTheUser(match, controlledSide, lines, liveHighlights);
            return runner.playLive(controlledMatch, seed, liveHighlights);
        }
    }

    private PreparedMatch handControlToTheUser(PreparedMatch match, MatchSide controlledSide,
            BackgroundLineReader lines, LiveHighlights liveHighlights) {
        String coachName = match.names().coachOf(controlledSide);
        Runnable chronicleAlignment = liveHighlights::catchUpForPause;
        ConsoleDugout dugout = ConsoleDugout.standard(lines, out, messages, coachName, chronicleAlignment);
        return preparation.handControlTo(match, controlledSide, dugout);
    }
}
