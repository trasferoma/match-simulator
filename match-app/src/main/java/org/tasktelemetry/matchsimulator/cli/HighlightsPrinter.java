package org.tasktelemetry.matchsimulator.cli;

import java.io.PrintStream;
import java.time.Duration;
import java.util.List;

import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsConfig;
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
    private final MatchPrinter matchPrinter;
    private final boolean interactiveTerminal;

    HighlightsPrinter(PrintStream out, Pause pause, HighlightsConfig config, MatchPrinter matchPrinter,
            boolean interactiveTerminal) {
        this.out = out;
        this.pause = pause;
        this.config = config;
        this.matchPrinter = matchPrinter;
        this.interactiveTerminal = interactiveTerminal;
    }

    void print(HighlightsReport report) {
        if (interactiveTerminal) {
            printAnimated(report);
            return;
        }
        printStopsOnly(report.segments());
        matchPrinter.printScoreboard(report.scoreboard());
    }

    void printAnimated(HighlightsReport report) {
        AnimatedRows rows = interactiveTerminal ? new TwoRows() : new SingleRow();
        animate(report.segments(), rows);
        matchPrinter.printScoreboard(report.scoreboard());
    }

    private void animate(List<NarratedSegment> segments, AnimatedRows rows) {
        int lastIndex = segments.size() - 1;
        for (int index = 0; index <= lastIndex; index++) {
            boolean isLast = index == lastIndex;
            animateSegment(segments.get(index), rows, isLast);
        }
        rows.finish();
    }

    private void animateSegment(NarratedSegment segment, AnimatedRows rows, boolean isLast) {
        switch (segment) {
            case NarratedSegment.Frame frame -> animateFrame(frame, rows, isLast);
            case NarratedSegment.Stop stop -> animateStop(stop, rows, isLast);
        }
    }

    private void animateFrame(NarratedSegment.Frame frame, AnimatedRows rows, boolean isLast) {
        rows.show(frame.line(), NO_TEXT);
        if (!isLast) {
            waitFor(config.framePause());
        }
    }

    private void animateStop(NarratedSegment.Stop stop, AnimatedRows rows, boolean isLast) {
        rows.show(stop.fieldLine(), NO_TEXT);
        for (NarratedLine line : stop.lines()) {
            tellLine(line, stop, rows);
        }
        if (!isLast) {
            waitFor(config.resumePause());
        }
    }

    private void tellLine(NarratedLine line, NarratedSegment.Stop stop, AnimatedRows rows) {
        Duration pauseBeforeLine = pauseBefore(line, stop.containsChance());
        waitFor(pauseBeforeLine);
        rows.show(line.fieldLine(), line.text());
        if (line.pause() == LinePause.GOAL_LINE) {
            waitFor(config.afterGoalPause());
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

    private void printStopsOnly(List<NarratedSegment> segments) {
        List<NarratedSegment.Stop> stops = segments.stream()
                .filter(NarratedSegment.Stop.class::isInstance)
                .map(NarratedSegment.Stop.class::cast)
                .toList();
        for (int index = 0; index < stops.size(); index++) {
            if (index > 0) {
                out.println();
            }
            printStop(stops.get(index));
        }
    }

    private void printStop(NarratedSegment.Stop stop) {
        out.println(stop.fieldLine());
        for (NarratedLine line : stop.lines()) {
            out.println(line.text());
        }
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
