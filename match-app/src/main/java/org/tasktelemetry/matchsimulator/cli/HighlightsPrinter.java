package org.tasktelemetry.matchsimulator.cli;

import java.io.PrintStream;
import java.time.Duration;

import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsConfig;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsListener;
import org.tasktelemetry.matchsimulator.narration.highlights.LinePause;
import org.tasktelemetry.matchsimulator.narration.highlights.NarratedLine;
import org.tasktelemetry.matchsimulator.narration.highlights.NarratedSegment;

final class HighlightsPrinter {

    private static final String CARRIAGE_RETURN = "\r";
    private static final String CURSOR_UP = "\u001B[1A";
    private static final String SPACE = " ";
    private static final String NO_TEXT = "";
    private static final String MESSAGE_SEPARATOR = SPACE.repeat(2);
    private static final String LINE_SEPARATOR = System.lineSeparator();

    private final PrintStream out;
    private final Pause pause;
    private final HighlightsConfig config;
    private final boolean interactiveTerminal;

    HighlightsPrinter(PrintStream out, Pause pause, HighlightsConfig config, boolean interactiveTerminal) {
        this.out = out;
        this.pause = pause;
        this.config = config;
        this.interactiveTerminal = interactiveTerminal;
    }

    HighlightsListener openSession(boolean forceAnimation) {
        if (interactiveTerminal) {
            AnimatedRows twoRows = new TwoRows();
            return new AnimatedSession(twoRows);
        }
        if (forceAnimation) {
            AnimatedRows singleRow = new SingleRow();
            return new AnimatedSession(singleRow);
        }
        return new StopsOnlySession();
    }

    void pauseAfterLineups(boolean forceAnimation) {
        if (isAnimated(forceAnimation)) {
            waitFor(config.lineupsPause());
        }
    }

    private boolean isAnimated(boolean forceAnimation) {
        return interactiveTerminal || forceAnimation;
    }

    private final class AnimatedSession implements HighlightsListener {

        private final AnimatedRows rows;
        private Duration pendingPause;

        private AnimatedSession(AnimatedRows rows) {
            this.rows = rows;
        }

        @Override
        public void segmentNarrated(NarratedSegment segment) {
            waitForPendingPause();
            switch (segment) {
                case NarratedSegment.Frame frame -> animateFrame(frame);
                case NarratedSegment.Stop stop -> animateStop(stop);
            }
        }

        @Override
        public void chronicleFinished() {
            rows.finish();
        }

        private void waitForPendingPause() {
            if (pendingPause == null) {
                return;
            }
            waitFor(pendingPause);
            pendingPause = null;
        }

        private void animateFrame(NarratedSegment.Frame frame) {
            rows.show(frame.line(), NO_TEXT);
            pendingPause = config.framePause();
        }

        private void animateStop(NarratedSegment.Stop stop) {
            rows.show(stop.fieldLine(), NO_TEXT);
            for (NarratedLine line : stop.lines()) {
                tellLine(line, stop);
            }
            pendingPause = config.resumePause();
        }

        private void tellLine(NarratedLine line, NarratedSegment.Stop stop) {
            Duration pauseBeforeLine = pauseBefore(line, stop.containsChance());
            waitFor(pauseBeforeLine);
            rows.show(line.fieldLine(), line.text());
            if (line.pause() == LinePause.GOAL_LINE) {
                waitFor(config.afterGoalPause());
            }
        }
    }

    private final class StopsOnlySession implements HighlightsListener {

        private boolean stopPrinted;

        @Override
        public void segmentNarrated(NarratedSegment segment) {
            if (segment instanceof NarratedSegment.Stop stop) {
                printStop(stop);
            }
        }

        @Override
        public void chronicleFinished() {
        }

        private void printStop(NarratedSegment.Stop stop) {
            if (stopPrinted) {
                out.println();
            }
            out.println(stop.fieldLine());
            for (NarratedLine line : stop.lines()) {
                out.println(line.text());
            }
            stopPrinted = true;
        }
    }

    private Duration pauseBefore(NarratedLine line, boolean stopContainsChance) {
        return switch (line.pause()) {
            case EVENT_LINE -> config.eventLinePause();
            case CHANCE_LINE -> config.chanceLinePause();
            case GOAL_LINE, GOAL_CONTEXT_LINE -> stopContainsChance ? config.chanceLinePause()
                    : config.eventLinePause();
        };
    }

    private void waitFor(Duration duration) {
        out.flush();
        pause.waitFor(duration);
    }

    private interface AnimatedRows {

        void show(String field, String message);

        void finish();
    }

    private final class TwoRows implements AnimatedRows {

        private int fieldWidth;
        private int messageWidth;
        private boolean drawn;

        @Override
        public void show(String field, String message) {
            String goToFieldRow = drawn ? CARRIAGE_RETURN + CURSOR_UP : CARRIAGE_RETURN;
            String fieldRow = coverResidue(field, fieldWidth);
            String messageRow = coverResidue(message, messageWidth);
            out.print(goToFieldRow + fieldRow + LINE_SEPARATOR + messageRow);
            fieldWidth = field.length();
            messageWidth = message.length();
            drawn = true;
        }

        @Override
        public void finish() {
            if (drawn) {
                out.println();
            }
        }
    }

    private final class SingleRow implements AnimatedRows {

        private int rowWidth;
        private boolean drawn;

        @Override
        public void show(String field, String message) {
            String row = message.isEmpty() ? field : field + MESSAGE_SEPARATOR + message;
            out.print(CARRIAGE_RETURN + coverResidue(row, rowWidth));
            rowWidth = row.length();
            drawn = true;
        }

        @Override
        public void finish() {
            if (drawn) {
                out.println();
            }
        }
    }

    private static String coverResidue(String text, int previousWidth) {
        int leftover = Math.max(0, previousWidth - text.length());
        return text + SPACE.repeat(leftover);
    }
}
