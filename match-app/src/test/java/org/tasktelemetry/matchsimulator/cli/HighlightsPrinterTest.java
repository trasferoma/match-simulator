package org.tasktelemetry.matchsimulator.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.message.UserMessages;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsConfig;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsConfigReader;
import org.tasktelemetry.matchsimulator.narration.highlights.LinePause;
import org.tasktelemetry.matchsimulator.narration.highlights.NarratedLine;
import org.tasktelemetry.matchsimulator.narration.highlights.NarratedSegment;
import org.tasktelemetry.matchsimulator.summary.ContourCounters;
import org.tasktelemetry.matchsimulator.summary.MatchSummary;
import org.tasktelemetry.matchsimulator.summary.TeamStatistics;

class HighlightsPrinterTest {

    private static final String NL = System.lineSeparator();
    private static final String TITLE = "Tabellino";
    private static final String CURSOR_UP = "\u001B[1A";
    private static final String REDRAW = "\r" + CURSOR_UP;
    private static final Duration FRAME = Duration.ofMillis(11);
    private static final Duration EVENT_LINE = Duration.ofMillis(22);
    private static final Duration CHANCE_LINE = Duration.ofMillis(33);
    private static final Duration AFTER_GOAL = Duration.ofMillis(44);
    private static final Duration RESUME = Duration.ofMillis(55);
    private static final HighlightsConfig CONFIG = configWithPauses(11, 22, 33, 44, 55);
    private static final HighlightsConfig SLOWER_CONFIG = configWithPauses(1100, 2200, 3300, 4400, 5500);

    @Test
    void framesAreRedrawnInPlaceAndTheShorterOnesCoverTheLongerPreviousRow() {
        List<NarratedSegment> segments = List.of(frame("aaaaaaaaaa"), frame("bbbb"), frame("cccccccc"));

        Run run = new Run(CONFIG, true, segments);

        String rows = "\raaaaaaaaaa" + NL
                + REDRAW + "bbbb      " + NL
                + REDRAW + "cccccccc" + NL;
        assertThat(run.output()).startsWith(rows + NL + NL + TITLE);
        assertThat(run.pauses()).containsExactly(FRAME, FRAME);
    }

    @Test
    void aStopStopsTheFieldAndEachOfItsLinesReplacesThePreviousOneOnTheSecondRow() {
        List<NarratedSegment> segments = List.of(
                frame("aaaaaaaaaaaa"),
                stop("bbbb", false, line("first", LinePause.EVENT_LINE), line("second", LinePause.EVENT_LINE)),
                frame("cc"));

        Run run = new Run(CONFIG, true, segments);

        String rows = "\raaaaaaaaaaaa" + NL
                + REDRAW + "bbbb        " + NL
                + REDRAW + "bbbb" + NL + "first"
                + REDRAW + "bbbb" + NL + "second"
                + REDRAW + "cc  " + NL + "      " + NL;
        assertThat(run.output()).startsWith(rows + NL + TITLE);
        assertThat(run.pauses()).containsExactly(FRAME, EVENT_LINE, EVENT_LINE, RESUME);
    }

    @Test
    void pausesFollowTheOrderOfFramesLinesGoalContextAndResume() {
        Run run = new Run(CONFIG, true, goalAndFollowingSegments());

        assertThat(run.pauses()).containsExactly(FRAME, CHANCE_LINE, CHANCE_LINE, AFTER_GOAL, CHANCE_LINE,
                CHANCE_LINE, RESUME, FRAME, EVENT_LINE);
    }

    @Test
    void theGoalPauseFollowsTheGoalLineAndPrecedesTheContextLine() {
        Run run = new Run(CONFIG, true, goalAndFollowingSegments());

        assertThat(run.deliveredAtPause(2).stripTrailing()).endsWith("preparation");
        assertThat(run.deliveredAtPause(3).stripTrailing()).endsWith("goal");
        assertThat(run.deliveredAtPause(4).stripTrailing()).endsWith("goal");
        assertThat(run.deliveredAtPause(5).stripTrailing()).endsWith("context");
    }

    @Test
    void theSecondRowIsEmptyUntilTheFirstLineIsToldAndTheNextFrameEmptiesItAgain() {
        List<NarratedSegment> segments = List.of(frame("a"),
                stop("field", false, line("told", LinePause.EVENT_LINE)), frame("b"));

        Run run = new Run(CONFIG, true, segments);

        assertThat(run.deliveredAtPause(0)).isEqualTo("\ra" + NL);
        assertThat(run.deliveredAtPause(1)).endsWith(REDRAW + "field" + NL);
        assertThat(run.output()).contains(REDRAW + "field" + NL + "told");
        assertThat(run.output()).contains(REDRAW + "b    " + NL + "    " + NL + NL + TITLE);
    }

    @Test
    void aShorterLineCoversTheResidueOfALongerPreviousOneOnTheSecondRow() {
        List<NarratedSegment> segments = List.of(stop("f", false,
                line("long line", LinePause.EVENT_LINE), line("short", LinePause.EVENT_LINE)));

        Run run = new Run(CONFIG, true, segments);

        assertThat(run.output()).contains(REDRAW + "f" + NL + "short    " + NL + NL + TITLE);
    }

    @Test
    void onATerminalAMatchWithNoSegmentsPrintsOnlyTheScoreboard() {
        Run run = new Run(CONFIG, true, List.of());

        assertThat(run.output()).startsWith(NL + TITLE);
        assertThat(run.pauses()).isEmpty();
    }

    @Test
    void aGoalInAStopWithoutAChanceUsesTheEventLinePauseBeforeTheGoalAndTheContext() {
        List<NarratedSegment> segments = List.of(stop("field", false, line("goal", LinePause.GOAL_LINE),
                line("context", LinePause.GOAL_CONTEXT_LINE)));

        Run run = new Run(CONFIG, true, segments);

        assertThat(run.pauses()).containsExactly(EVENT_LINE, AFTER_GOAL, EVENT_LINE);
    }

    @Test
    void everythingWrittenBeforeEachPauseHasAlreadyReachedTheUnderlyingStream() {
        Run run = new Run(CONFIG, true, goalAndFollowingSegments());

        assertThat(run.pendingBytesAtEachPause()).hasSize(9).containsOnly(0);
        assertThat(run.deliveredAtPause(0)).isEqualTo("\rframe one" + NL);
    }

    @Test
    void whenTheLastSegmentIsAStopNothingFollowsItsLastLineButTheScoreboardAfterOneBlankLine() {
        List<NarratedSegment> segments = List.of(frame("f"),
                stop("g", false, line("last line", LinePause.EVENT_LINE)));

        Run run = new Run(CONFIG, true, segments);

        assertThat(run.pauses()).containsExactly(FRAME, EVENT_LINE);
        assertThat(run.output()).contains("last line" + NL + NL + TITLE).doesNotContain("last line" + NL + NL + NL);
    }

    @Test
    void whenTheLastSegmentIsAFrameThereIsNoPauseAfterItAndTheRowsAreClosedBeforeOneBlankLine() {
        List<NarratedSegment> segments = List.of(
                stop("g", false, line("x", LinePause.EVENT_LINE)),
                frame("tail"));

        Run run = new Run(CONFIG, true, segments);

        assertThat(run.pauses()).containsExactly(EVENT_LINE, RESUME);
        assertThat(run.output()).contains(REDRAW + "tail" + NL + " " + NL + NL + TITLE)
                .doesNotContain(" " + NL + NL + NL);
    }

    @Test
    void withoutATerminalOnlyTheStopsAreShownEachWithTheFieldLineOfItsFirstEventAndNothingIsWaitedFor() {
        Run run = new Run(CONFIG, false, mixedSegments());

        List<String> lines = run.output().lines().toList();
        assertThat(lines.subList(0, 7)).containsExactly(
                "field A", "A1", "A2", "", "field B", "B1", "");
        assertThat(lines.get(7)).startsWith(TITLE);
        assertThat(run.output().replace(NL, "")).doesNotContain("\r");
        assertThat(run.pauses()).isEmpty();
    }

    @Test
    void withoutATerminalAMatchWithNoStopsPrintsOnlyTheScoreboard() {
        Run run = new Run(CONFIG, false, List.of(frame("a"), frame("b")));

        assertThat(run.output()).startsWith(NL + TITLE);
        assertThat(run.pauses()).isEmpty();
    }

    @Test
    void withoutATerminalExactlyOneBlankLineSeparatesTheLastLineFromTheScoreboardWhateverTheLastSegmentIs() {
        Run endingWithAStop = new Run(CONFIG, false, List.of(frame("a"), stop("field", false,
                line("last", LinePause.EVENT_LINE))));
        Run endingWithAFrame = new Run(CONFIG, false, List.of(stop("field", false,
                line("last", LinePause.EVENT_LINE)), frame("a")));

        assertThat(endingWithAStop.output()).contains("last" + NL + NL + TITLE)
                .doesNotContain("last" + NL + NL + NL);
        assertThat(endingWithAFrame.output()).contains("last" + NL + NL + TITLE)
                .doesNotContain("last" + NL + NL + NL);
    }

    @Test
    void pausesOfDifferentLengthDoNotChangeTheBytesOnATerminalNorWithoutOne() {
        Run terminal = new Run(CONFIG, true, mixedSegments());
        Run slowerTerminal = new Run(SLOWER_CONFIG, true, mixedSegments());
        Run fallback = new Run(CONFIG, false, mixedSegments());
        Run slowerFallback = new Run(SLOWER_CONFIG, false, mixedSegments());

        assertThat(slowerTerminal.output()).isEqualTo(terminal.output());
        assertThat(slowerFallback.output()).isEqualTo(fallback.output());
        assertThat(slowerTerminal.pauses()).isNotEqualTo(terminal.pauses());
    }

    @Test
    void onASingleRowTheFramesAreRewrittenWithCarriageReturnOnlyAndCoverTheResidue() {
        List<NarratedSegment> segments = List.of(frame("aaaaaaaaaa"), frame("bbbb"), frame("cccccccc"));

        Run run = Run.forced(CONFIG, segments);

        String row = "\raaaaaaaaaa\rbbbb      \rcccccccc";
        assertThat(run.output()).startsWith(row + NL + NL + TITLE);
        assertThat(run.output()).doesNotContain(CURSOR_UP);
        assertThat(run.pauses()).containsExactly(FRAME, FRAME);
    }

    @Test
    void onASingleRowAStopShowsTheFixedFieldAndTheCurrentLineAndTheNextFrameShowsOnlyTheField() {
        List<NarratedSegment> segments = List.of(
                frame("aaaaaaaaaaaa"),
                stop("bbbb", false, line("first", LinePause.EVENT_LINE), line("second", LinePause.EVENT_LINE)),
                frame("cc"));

        Run run = Run.forced(CONFIG, segments);

        String row = "\raaaaaaaaaaaa"
                + "\rbbbb        "
                + "\rbbbb  first"
                + "\rbbbb  second"
                + "\rcc          ";
        assertThat(run.output()).startsWith(row + NL + NL + TITLE);
        assertThat(run.output()).doesNotContain(CURSOR_UP);
        assertThat(run.pauses()).containsExactly(FRAME, EVENT_LINE, EVENT_LINE, RESUME);
    }

    @Test
    void onASingleRowAShorterLineCoversTheResidueOfALongerPreviousOne() {
        List<NarratedSegment> segments = List.of(stop("f", false,
                line("long line", LinePause.EVENT_LINE), line("short", LinePause.EVENT_LINE)));

        Run run = Run.forced(CONFIG, segments);

        assertThat(run.output()).startsWith("\rf\rf  long line\rf  short    " + NL + NL + TITLE);
    }

    @Test
    void onASingleRowPausesAndFlushesAreTheSameAsOnTwoRows() {
        Run forced = Run.forced(CONFIG, goalAndFollowingSegments());
        Run terminal = new Run(CONFIG, true, goalAndFollowingSegments());

        assertThat(forced.pauses()).isEqualTo(terminal.pauses());
        assertThat(forced.pendingBytesAtEachPause()).hasSize(9).containsOnly(0);
        assertThat(forced.deliveredAtPause(0)).isEqualTo("\rframe one");
    }

    @Test
    void onTwoRowsTheFieldIsRedrawnWithTheFieldLineOfEachLineShown() {
        List<NarratedSegment> segments = List.of(stop("score 0", true,
                line("pass", LinePause.CHANCE_LINE, "score 0"),
                line("goal", LinePause.GOAL_LINE, "score 1"),
                line("context", LinePause.GOAL_CONTEXT_LINE, "score 1")));

        Run run = new Run(CONFIG, true, segments);

        String rows = "\rscore 0" + NL
                + REDRAW + "score 0" + NL + "pass"
                + REDRAW + "score 1" + NL + "goal"
                + REDRAW + "score 1" + NL + "context" + NL;
        assertThat(run.output()).startsWith(rows + NL + TITLE);
    }

    @Test
    void onASingleRowTheFieldIsRedrawnWithTheFieldLineOfEachLineShown() {
        List<NarratedSegment> segments = List.of(stop("score 0", true,
                line("pass", LinePause.CHANCE_LINE, "score 0"),
                line("goal", LinePause.GOAL_LINE, "score 1"),
                line("context", LinePause.GOAL_CONTEXT_LINE, "score 1")));

        Run run = Run.forced(CONFIG, segments);

        String row = "\rscore 0"
                + "\rscore 0  pass"
                + "\rscore 1  goal"
                + "\rscore 1  context";
        assertThat(run.output()).startsWith(row + NL + NL + TITLE);
    }

    @Test
    void onASingleRowAMatchWithNoSegmentsPrintsOnlyTheScoreboardAfterOneBlankLine() {
        Run run = Run.forced(CONFIG, List.of());

        assertThat(run.output()).startsWith(NL + TITLE);
        assertThat(run.pauses()).isEmpty();
    }

    private static List<NarratedSegment> goalAndFollowingSegments() {
        return List.of(
                frame("frame one"),
                stop("field", true, line("preparation", LinePause.CHANCE_LINE), line("goal", LinePause.GOAL_LINE),
                        line("context", LinePause.GOAL_CONTEXT_LINE), line("restart", LinePause.CHANCE_LINE)),
                frame("frame two"),
                stop("field two", false, line("end", LinePause.EVENT_LINE)));
    }

    private static List<NarratedSegment> mixedSegments() {
        return List.of(
                frame("f1"),
                stop("field A", true, line("A1", LinePause.CHANCE_LINE), line("A2", LinePause.CHANCE_LINE)),
                frame("f2"),
                stop("field B", false, line("B1", LinePause.EVENT_LINE)),
                frame("f3"));
    }

    private static NarratedSegment frame(String text) {
        return new NarratedSegment.Frame(text);
    }

    private static NarratedSegment stop(String fieldLine, boolean containsChance, NarratedLine... lines) {
        List<NarratedLine> withStopField = Arrays.stream(lines)
                .map(line -> line.fieldLine().isEmpty() ? new NarratedLine(line.text(), line.pause(), fieldLine) : line)
                .toList();
        return new NarratedSegment.Stop(fieldLine, withStopField, containsChance);
    }

    private static NarratedLine line(String text, LinePause pause) {
        return new NarratedLine(text, pause, "");
    }

    private static NarratedLine line(String text, LinePause pause, String fieldLine) {
        return new NarratedLine(text, pause, fieldLine);
    }

    private static HighlightsConfig configWithPauses(long frame, long eventLine, long chanceLine, long afterGoal,
            long resume) {
        HighlightsConfig defaults = new HighlightsConfigReader().readDefault();
        return new HighlightsConfig(defaults.treatments(), defaults.alsoStopsInZones(),
                defaults.maxPreparationLines(), defaults.consequenceWindow(), Duration.ofMillis(frame),
                Duration.ofMillis(eventLine), Duration.ofMillis(chanceLine), Duration.ofMillis(afterGoal),
                Duration.ofMillis(resume));
    }

    private static Scoreboard scoreboard() {
        TeamStatistics statistics = new TeamStatistics(0, 0, 0.0, 0, ContourCounters.none());
        MatchSummary summary = new MatchSummary(List.of(), statistics, statistics);
        return new Scoreboard("Rossi", "Blu", summary, 42L);
    }

    private static final class Run {

        private final BufferingStream stream = new BufferingStream();
        private final PauseProbe pause = new PauseProbe(stream);

        Run(HighlightsConfig config, boolean interactiveTerminal, List<NarratedSegment> segments) {
            this(config, interactiveTerminal, segments, HighlightsPrinter::print);
        }

        static Run forced(HighlightsConfig config, List<NarratedSegment> segments) {
            return new Run(config, false, segments, HighlightsPrinter::printAnimated);
        }

        private Run(HighlightsConfig config, boolean interactiveTerminal, List<NarratedSegment> segments,
                BiConsumer<HighlightsPrinter, HighlightsReport> action) {
            PrintStream out = new PrintStream(stream, false, StandardCharsets.UTF_8);
            BufferedReader noInput = new BufferedReader(new StringReader(""));
            MatchPrinter matchPrinter = new MatchPrinter(UserMessages.italian(), out, new EnterKeyReader(noInput));
            HighlightsPrinter printer = new HighlightsPrinter(out, pause, config, matchPrinter, interactiveTerminal);
            action.accept(printer, new HighlightsReport(segments, scoreboard()));
        }

        String output() {
            return stream.delivered();
        }

        List<Duration> pauses() {
            return pause.durations;
        }

        List<Integer> pendingBytesAtEachPause() {
            return pause.pendingBytesAtEachPause;
        }

        String deliveredAtPause(int index) {
            return pause.deliveredAtEachPause.get(index);
        }
    }

    private static final class BufferingStream extends OutputStream {

        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
        private final ByteArrayOutputStream delivered = new ByteArrayOutputStream();

        @Override
        public void write(int value) {
            pending.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            pending.write(bytes, offset, length);
        }

        @Override
        public void flush() {
            delivered.writeBytes(pending.toByteArray());
            pending.reset();
        }

        int pendingBytes() {
            return pending.size();
        }

        String delivered() {
            return delivered.toString(StandardCharsets.UTF_8);
        }
    }

    private static final class PauseProbe implements Pause {

        private final BufferingStream stream;
        private final List<Duration> durations = new ArrayList<>();
        private final List<Integer> pendingBytesAtEachPause = new ArrayList<>();
        private final List<String> deliveredAtEachPause = new ArrayList<>();

        PauseProbe(BufferingStream stream) {
            this.stream = stream;
        }

        @Override
        public void waitFor(Duration duration) {
            durations.add(duration);
            pendingBytesAtEachPause.add(stream.pendingBytes());
            deliveredAtEachPause.add(stream.delivered());
        }
    }
}
