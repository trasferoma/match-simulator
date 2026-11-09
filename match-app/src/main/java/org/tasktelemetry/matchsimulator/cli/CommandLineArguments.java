package org.tasktelemetry.matchsimulator.cli;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.narration.ChronicleMode;

final class CommandLineArguments {

    private static final String SEED_OPTION = "--seed";
    private static final String FULL_OPTION = "--full";
    private static final String OPTION_PREFIX = "--";
    private static final int TEAM_FILE_COUNT = 2;

    private final Path homeFile;
    private final Path awayFile;
    private final ChronicleMode chronicleMode;
    private final Long seed;

    private CommandLineArguments(Path homeFile, Path awayFile, ChronicleMode chronicleMode, Long seed) {
        this.homeFile = homeFile;
        this.awayFile = awayFile;
        this.chronicleMode = chronicleMode;
        this.seed = seed;
    }

    static CommandLineArguments parse(List<String> arguments) {
        List<String> files = new ArrayList<>();
        Long seed = null;
        boolean full = false;
        Iterator<String> remaining = arguments.iterator();
        while (remaining.hasNext()) {
            String argument = remaining.next();
            if (argument.equals(SEED_OPTION)) {
                requireNotRepeated(seed != null, SEED_OPTION);
                seed = readSeed(remaining);
            } else if (argument.equals(FULL_OPTION)) {
                requireNotRepeated(full, FULL_OPTION);
                full = true;
            } else if (argument.startsWith(OPTION_PREFIX)) {
                throw new InvalidCommandLineException(CommandLineProblem.UNKNOWN_OPTION, argument);
            } else {
                files.add(argument);
            }
        }
        requireTwoTeamFiles(files);
        Path homeFile = readPath(files.get(0));
        Path awayFile = readPath(files.get(1));
        ChronicleMode chronicleMode = chronicleModeOf(full);
        return new CommandLineArguments(homeFile, awayFile, chronicleMode, seed);
    }

    Path homeFile() {
        return homeFile;
    }

    Path awayFile() {
        return awayFile;
    }

    ChronicleMode chronicleMode() {
        return chronicleMode;
    }

    Optional<Long> seed() {
        return Optional.ofNullable(seed);
    }

    private static ChronicleMode chronicleModeOf(boolean full) {
        if (full) {
            return ChronicleMode.FULL;
        }
        return ChronicleMode.DEFAULT;
    }

    private static void requireNotRepeated(boolean alreadyGiven, String option) {
        if (alreadyGiven) {
            throw new InvalidCommandLineException(CommandLineProblem.DUPLICATE_OPTION, option);
        }
    }

    private static void requireTwoTeamFiles(List<String> files) {
        if (files.size() != TEAM_FILE_COUNT) {
            throw new InvalidCommandLineException(CommandLineProblem.WRONG_FILE_COUNT, files.size());
        }
    }

    private static Path readPath(String text) {
        try {
            return Path.of(text);
        } catch (InvalidPathException exception) {
            throw new InvalidCommandLineException(CommandLineProblem.INVALID_FILE_PATH, text);
        }
    }

    private static long readSeed(Iterator<String> remaining) {
        if (!remaining.hasNext()) {
            throw new InvalidCommandLineException(CommandLineProblem.SEED_MISSING_VALUE);
        }
        String text = remaining.next();
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            throw new InvalidCommandLineException(CommandLineProblem.SEED_NOT_A_NUMBER, text);
        }
    }
}
