package org.tasktelemetry.matchsimulator.cli;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;

import org.tasktelemetry.matchsimulator.engine.team.Team;
import org.tasktelemetry.matchsimulator.message.UserMessages;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsConfig;
import org.tasktelemetry.matchsimulator.narration.highlights.HighlightsConfigReader;
import org.tasktelemetry.matchsimulator.teamfile.InvalidTeamFileException;
import org.tasktelemetry.matchsimulator.teamfile.TeamFileReader;

public final class MatchCommandLine {

    static final int EXIT_SUCCESS = 0;
    static final int EXIT_INVALID_TEAM_FILE = 1;
    static final int EXIT_USAGE_ERROR = 2;

    private final ErrorReporter errorReporter;
    private final TeamFileReader reader;
    private final MatchRunner runner;
    private final MatchPrinter printer;
    private final HighlightsPrinter highlightsPrinter;
    private final LongSupplier seedSource;

    MatchCommandLine(ErrorReporter errorReporter, TeamFileReader reader, MatchRunner runner,
            MatchPrinter printer, HighlightsPrinter highlightsPrinter, LongSupplier seedSource) {
        this.errorReporter = errorReporter;
        this.reader = reader;
        this.runner = runner;
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
        HighlightsConfig highlightsConfig = new HighlightsConfigReader().readDefault();
        MatchRunner runner = MatchRunner.standard(highlightsConfig);
        EnterKeyReader enterKeyReader = EnterKeyReader.standard(in);
        MatchPrinter printer = new MatchPrinter(messages, encodedOut, enterKeyReader);
        HighlightsPrinter highlightsPrinter =
                new HighlightsPrinter(encodedOut, pause, highlightsConfig, printer, interactiveTerminal);
        RandomGenerator random = RandomGenerator.getDefault();
        LongSupplier seedSource = random::nextLong;
        ErrorReporter errorReporter = new ErrorReporter(encodedErr, messages);
        return new MatchCommandLine(errorReporter, reader, runner, printer, highlightsPrinter, seedSource);
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
        Team home = reader.read(arguments.homeFile());
        Team away = reader.read(arguments.awayFile());
        long seed = arguments.seed().orElseGet(seedSource::getAsLong);
        if (arguments.highlights()) {
            playAndPrintHighlights(home, away, seed, arguments);
        } else {
            playAndPrintChronicle(home, away, seed, arguments);
        }
        return EXIT_SUCCESS;
    }

    private void playAndPrintHighlights(Team home, Team away, long seed, CommandLineArguments arguments) {
        HighlightsReport report = runner.playHighlights(home, away, seed);
        if (arguments.animate()) {
            highlightsPrinter.printAnimated(report);
            return;
        }
        highlightsPrinter.print(report);
    }

    private void playAndPrintChronicle(Team home, Team away, long seed, CommandLineArguments arguments) {
        MatchReport report = runner.play(home, away, seed, arguments.chronicleMode());
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
