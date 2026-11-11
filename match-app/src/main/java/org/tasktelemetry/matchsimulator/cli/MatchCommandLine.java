package org.tasktelemetry.matchsimulator.cli;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;

import org.tasktelemetry.matchsimulator.engine.team.Team;
import org.tasktelemetry.matchsimulator.message.UserMessages;
import org.tasktelemetry.matchsimulator.teamfile.InvalidTeamFileException;
import org.tasktelemetry.matchsimulator.teamfile.TeamFileReader;

public final class MatchCommandLine {

    static final int EXIT_SUCCESS = 0;
    static final int EXIT_INVALID_TEAM_FILE = 1;
    static final int EXIT_USAGE_ERROR = 2;

    private final PrintStream err;
    private final UserMessages messages;
    private final TeamFileReader reader;
    private final MatchRunner runner;
    private final MatchPrinter printer;
    private final LongSupplier seedSource;

    MatchCommandLine(PrintStream err, UserMessages messages, TeamFileReader reader, MatchRunner runner,
            MatchPrinter printer, LongSupplier seedSource) {
        this.err = err;
        this.messages = messages;
        this.reader = reader;
        this.runner = runner;
        this.printer = printer;
        this.seedSource = seedSource;
    }

    public static void main(String[] arguments) {
        MatchCommandLine commandLine = standard(System.in, System.out, System.err);
        int exitCode = commandLine.run(arguments);
        System.exit(exitCode);
    }

    static MatchCommandLine standard(InputStream in, OutputStream out, OutputStream err) {
        PrintStream utf8Out = utf8(out);
        PrintStream utf8Err = utf8(err);
        UserMessages messages = UserMessages.italian();
        TeamFileReader reader = TeamFileReader.italian();
        MatchRunner runner = MatchRunner.standard();
        EnterKeyReader enterKeyReader = EnterKeyReader.standard(in);
        MatchPrinter printer = new MatchPrinter(messages, utf8Out, enterKeyReader);
        RandomGenerator random = RandomGenerator.getDefault();
        LongSupplier seedSource = random::nextLong;
        return new MatchCommandLine(utf8Err, messages, reader, runner, printer, seedSource);
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
        MatchReport report = runner.play(home, away, seed, arguments.chronicleMode());
        printReport(report, arguments);
        return EXIT_SUCCESS;
    }

    private void printReport(MatchReport report, CommandLineArguments arguments) {
        if (arguments.stepByStep()) {
            printer.printStepByStep(report);
            return;
        }
        printer.print(report);
    }

    private int reportUsageError(InvalidCommandLineException exception) {
        String reason = messages.text(exception.problem(), exception.arguments());
        printError(reason);
        printUsage();
        return EXIT_USAGE_ERROR;
    }

    private int reportInvalidTeamFile(InvalidTeamFileException exception) {
        String reason = exception.getMessage();
        printError(reason);
        return EXIT_INVALID_TEAM_FILE;
    }

    private void printError(String reason) {
        String line = messages.text(CliText.ERROR, reason);
        err.println(line);
    }

    private void printUsage() {
        String line = messages.text(CliText.USAGE);
        err.println(line);
    }

    private static PrintStream utf8(OutputStream stream) {
        return new PrintStream(stream, true, StandardCharsets.UTF_8);
    }
}
