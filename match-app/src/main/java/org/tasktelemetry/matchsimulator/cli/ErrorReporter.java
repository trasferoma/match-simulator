package org.tasktelemetry.matchsimulator.cli;

import java.io.PrintStream;

import org.tasktelemetry.matchsimulator.message.UserMessages;
import org.tasktelemetry.matchsimulator.teamfile.InvalidTeamFileException;

final class ErrorReporter {

    private final PrintStream err;
    private final UserMessages messages;

    ErrorReporter(PrintStream err, UserMessages messages) {
        this.err = err;
        this.messages = messages;
    }

    void reportUsageError(InvalidCommandLineException exception) {
        String reason = messages.text(exception.problem(), exception.arguments());
        printError(reason);
        printUsage();
    }

    void reportInvalidTeamFile(InvalidTeamFileException exception) {
        String reason = exception.getMessage();
        printError(reason);
    }

    private void printError(String reason) {
        String line = messages.text(CliText.ERROR, reason);
        err.println(line);
    }

    private void printUsage() {
        String line = messages.text(CliText.USAGE);
        err.println(line);
    }
}
