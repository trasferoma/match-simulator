package org.tasktelemetry.matchsimulator.cli;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.live.MatchPreparation;
import org.tasktelemetry.matchsimulator.live.PreparedMatch;
import org.tasktelemetry.matchsimulator.message.UserMessages;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsConfig;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsConfigReader;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsListener;
import org.tasktelemetry.matchsimulator.teamfile.InvalidTeamFileException;
import org.tasktelemetry.matchsimulator.teamfile.TeamFileReader;

public final class MatchCommandLine {

    static final int EXIT_SUCCESS = 0;
    static final int EXIT_INVALID_TEAM_FILE = 1;
    static final int EXIT_USAGE_ERROR = 2;

    private final ErrorReporter errorReporter;
    private final MatchSetup setup;
    private final MatchRunner runner;
    private final ControlledMatchRunner controlledRunner;
    private final MatchPrinter printer;
    private final HighlightsPrinter highlightsPrinter;
    private final LongSupplier seedSource;

    MatchCommandLine(ErrorReporter errorReporter, MatchSetup setup, MatchRunner runner,
            ControlledMatchRunner controlledRunner, MatchPrinter printer, HighlightsPrinter highlightsPrinter,
            LongSupplier seedSource) {
        this.errorReporter = errorReporter;
        this.setup = setup;
        this.runner = runner;
        this.controlledRunner = controlledRunner;
        this.printer = printer;
        this.highlightsPrinter = highlightsPrinter;
        this.seedSource = seedSource;
    }

    public static void main(String[] arguments) {
        MatchCommandLine commandLine = standard(System.in, System.out, System.err);
        int exitCode = commandLine.run(arguments);
        System.exit(exitCode);
    }

    static MatchCommandLine standard(InputStream in, OutputStream out, OutputStream err) {
        Pause pause = new ThreadSleepPause();
        TerminalDetector terminalDetector = new TerminalDetector();
        boolean interactiveTerminal = terminalDetector.isInteractiveTerminal();
        Charset consoleCharset = terminalDetector.consoleCharset();
        return standard(in, out, err, pause, interactiveTerminal, consoleCharset);
    }

    static MatchCommandLine standard(InputStream in, OutputStream out, OutputStream err, Pause pause,
            boolean interactiveTerminal, Charset consoleCharset) {
        Charset outputCharset = interactiveTerminal ? consoleCharset : StandardCharsets.UTF_8;
        PrintStream encodedOut = encoded(out, outputCharset);
        PrintStream encodedErr = encoded(err, outputCharset);
        UserMessages messages = UserMessages.italian();
        TeamFileReader reader = TeamFileReader.italian();
        MatchPreparation preparation = MatchPreparation.standard();
        MatchSetup setup = new MatchSetup(reader, preparation);
        HighlightsConfig highlightsConfig = new HighlightsConfigReader().readDefault();
        MatchRunner runner = MatchRunner.standard(highlightsConfig);
        ControlledMatchRunner controlledRunner =
                new ControlledMatchRunner(runner, preparation, messages, encodedOut, in);
        EnterKeyReader enterKeyReader = EnterKeyReader.standard(in);
        MatchPrinter printer = new MatchPrinter(messages, encodedOut, enterKeyReader);
        HighlightsPrinter highlightsPrinter =
                new HighlightsPrinter(encodedOut, pause, highlightsConfig, interactiveTerminal);
        RandomGenerator random = RandomGenerator.getDefault();
        LongSupplier seedSource = random::nextLong;
        ErrorReporter errorReporter = new ErrorReporter(encodedErr, messages);
        return new MatchCommandLine(errorReporter, setup, runner, controlledRunner, printer, highlightsPrinter,
                seedSource);
    }

    int run(String... arguments) {
        try {
            List<String> argumentList = List.of(arguments);
            CommandLineArguments parsed = CommandLineArguments.parse(argumentList);
            return playAndPrint(parsed);
        } catch (InvalidCommandLineException exception) {
            return reportUsageError(exception);
        } catch (InvalidTeamFileException exception) {
            return reportInvalidTeamFile(exception);
        }
    }

    private int playAndPrint(CommandLineArguments arguments) {
        requireAnimatedSessionForControl(arguments);
        PreparedMatch match = setup.prepare(arguments.homeFile(), arguments.awayFile());
        long seed = arguments.seed().orElseGet(seedSource::getAsLong);
        printer.printLineups(match);
        if (arguments.highlights()) {
            playAndPrintHighlights(match, seed, arguments);
        } else {
            playAndPrintChronicle(match, seed, arguments);
        }
        return EXIT_SUCCESS;
    }

    private void playAndPrintHighlights(PreparedMatch match, long seed, CommandLineArguments arguments) {
        highlightsPrinter.pauseAfterLineups(arguments.animate());
        HighlightsListener session = highlightsPrinter.openSession(arguments.animate());
        Optional<MatchSide> controlledSide = arguments.controlledSide();
        if (controlledSide.isPresent()) {
            playAndPrintControlledHighlights(match, controlledSide.get(), seed, session);
            return;
        }
        Scoreboard scoreboard = runner.playLiveHighlights(match, seed, session);
        printer.printScoreboard(scoreboard);
    }

    private void playAndPrintControlledHighlights(PreparedMatch match, MatchSide controlledSide, long seed,
            HighlightsListener session) {
        Scoreboard scoreboard = controlledRunner.play(match, controlledSide, seed, session);
        printer.printScoreboardOfControlledMatch(scoreboard);
    }

    private void requireAnimatedSessionForControl(CommandLineArguments arguments) {
        boolean controlled = arguments.controlledSide().isPresent();
        boolean animated = highlightsPrinter.isAnimated(arguments.animate());
        if (controlled && !animated) {
            throw new InvalidCommandLineException(CommandLineProblem.CONTROL_WITHOUT_ANIMATION);
        }
    }

    private void playAndPrintChronicle(PreparedMatch match, long seed, CommandLineArguments arguments) {
        MatchReport report = runner.play(match, seed, arguments.chronicleMode());
        printChronicle(report, arguments);
    }

    private void printChronicle(MatchReport report, CommandLineArguments arguments) {
        if (arguments.stepByStep()) {
            printer.printStepByStep(report);
            return;
        }
        printer.print(report);
    }

    private int reportUsageError(InvalidCommandLineException exception) {
        errorReporter.reportUsageError(exception);
        return EXIT_USAGE_ERROR;
    }

    private int reportInvalidTeamFile(InvalidTeamFileException exception) {
        errorReporter.reportInvalidTeamFile(exception);
        return EXIT_INVALID_TEAM_FILE;
    }

    private static PrintStream encoded(OutputStream stream, Charset charset) {
        return new PrintStream(stream, true, charset);
    }
}
