package org.tasktelemetry.matchsimulator.cli.dugout;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tasktelemetry.matchsimulator.engine.event.Deployment;

final class DugoutCommandParser {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern FORMATION = Pattern.compile("(\\d+)-(\\d+)-(\\d+)");
    private static final int SUBSTITUTION_ARGUMENTS = 2;
    private static final int SINGLE_ARGUMENT = 1;
    private static final int NO_ARGUMENTS = 0;

    DugoutCommand parse(String line) {
        List<String> words = splitIntoWords(line);
        if (words.isEmpty()) {
            return new DugoutCommand.Redraw();
        }
        String commandWord = words.get(0).toLowerCase(Locale.ROOT);
        List<String> arguments = words.subList(1, words.size());
        DugoutKeyword keyword = DugoutKeyword.of(commandWord)
                .orElseThrow(() -> new InvalidDugoutCommandException(DugoutProblem.UNKNOWN_COMMAND, commandWord,
                        DugoutKeyword.HELP.word()));
        return readCommand(keyword, arguments);
    }

    private DugoutCommand readCommand(DugoutKeyword keyword, List<String> arguments) {
        return switch (keyword) {
            case SUBSTITUTION -> readSubstitution(arguments);
            case FORMATION -> readFormation(arguments);
            case STYLE -> readStyle(arguments);
            case UNDO -> readUndo(arguments);
            case HELP -> readHelp(arguments);
            case RESUME -> readResume(arguments);
        };
    }

    private DugoutCommand readSubstitution(List<String> arguments) {
        requireArgumentCount(DugoutKeyword.SUBSTITUTION, arguments, SUBSTITUTION_ARGUMENTS);
        int leavingNumber = readNumber(arguments.get(0));
        int enteringNumber = readNumber(arguments.get(1));
        return new DugoutCommand.Substitute(leavingNumber, enteringNumber);
    }

    private DugoutCommand readFormation(List<String> arguments) {
        requireArgumentCount(DugoutKeyword.FORMATION, arguments, SINGLE_ARGUMENT);
        Deployment target = readFormationLabel(arguments.get(0));
        return new DugoutCommand.ChangeFormation(target);
    }

    private DugoutCommand readStyle(List<String> arguments) {
        requireArgumentCount(DugoutKeyword.STYLE, arguments, SINGLE_ARGUMENT);
        String styleWord = arguments.get(0).toLowerCase(Locale.ROOT);
        StyleWord style = StyleWord.of(styleWord)
                .orElseThrow(() -> unknownStyle(styleWord));
        return new DugoutCommand.ChangeStyle(style.style());
    }

    private InvalidDugoutCommandException unknownStyle(String styleWord) {
        String allowedStyles = StyleWord.allWords();
        return new InvalidDugoutCommandException(DugoutProblem.UNKNOWN_STYLE, styleWord, allowedStyles);
    }

    private DugoutCommand readUndo(List<String> arguments) {
        requireArgumentCount(DugoutKeyword.UNDO, arguments, NO_ARGUMENTS);
        return new DugoutCommand.Undo();
    }

    private DugoutCommand readHelp(List<String> arguments) {
        requireArgumentCount(DugoutKeyword.HELP, arguments, NO_ARGUMENTS);
        return new DugoutCommand.Help();
    }

    private DugoutCommand readResume(List<String> arguments) {
        requireArgumentCount(DugoutKeyword.RESUME, arguments, NO_ARGUMENTS);
        return new DugoutCommand.Resume();
    }

    private void requireArgumentCount(DugoutKeyword keyword, List<String> arguments, int expected) {
        if (arguments.size() != expected) {
            throw new InvalidDugoutCommandException(DugoutProblem.WRONG_ARGUMENTS, keyword.word(),
                    DugoutKeyword.HELP.word());
        }
    }

    private int readNumber(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            throw new InvalidDugoutCommandException(DugoutProblem.NOT_A_NUMBER, text);
        }
    }

    private Deployment readFormationLabel(String text) {
        Matcher matcher = FORMATION.matcher(text);
        if (!matcher.matches()) {
            throw new InvalidDugoutCommandException(DugoutProblem.MALFORMED_FORMATION, text);
        }
        try {
            int defenders = Integer.parseInt(matcher.group(1));
            int midfielders = Integer.parseInt(matcher.group(2));
            int forwards = Integer.parseInt(matcher.group(3));
            return new Deployment(defenders, midfielders, forwards);
        } catch (NumberFormatException exception) {
            throw new InvalidDugoutCommandException(DugoutProblem.MALFORMED_FORMATION, text);
        }
    }

    private List<String> splitIntoWords(String line) {
        String stripped = line.strip();
        if (stripped.isEmpty()) {
            return List.of();
        }
        String[] words = WHITESPACE.split(stripped);
        return List.of(words);
    }
}
