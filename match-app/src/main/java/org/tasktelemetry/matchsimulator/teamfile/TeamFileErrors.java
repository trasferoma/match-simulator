package org.tasktelemetry.matchsimulator.teamfile;

import java.nio.file.Path;
import java.util.List;

import org.tasktelemetry.matchsimulator.coach.squad.SquadRuleViolation;
import org.tasktelemetry.matchsimulator.engine.team.TeamRuleViolation;
import org.tasktelemetry.matchsimulator.message.UserMessages;

final class TeamFileErrors {

    private static final String LIST_SEPARATOR = ", ";

    private final UserMessages messages;

    TeamFileErrors(UserMessages messages) {
        this.messages = messages;
    }

    InvalidTeamFileException inFile(Path file, TeamFileProblem problem, Object... arguments) {
        return inFileCausedBy(file, null, problem, arguments);
    }

    InvalidTeamFileException inFileCausedBy(Path file, Throwable cause, TeamFileProblem problem,
            Object... arguments) {
        String reason = messages.text(problem, arguments);
        String message = messages.text(TeamFileErrorFrame.WHOLE_FILE, file, reason);
        return InvalidTeamFileException.forFile(file, message, cause);
    }

    InvalidTeamFileException inField(Path file, String field, TeamFileProblem problem, Object... arguments) {
        String reason = messages.text(problem, arguments);
        return inField(file, field, reason);
    }

    InvalidTeamFileException inField(Path file, String field, TeamRuleViolation violation) {
        Object[] reasonArguments = readableArguments(violation.arguments());
        String reason = messages.text(violation.rule(), reasonArguments);
        return inField(file, field, reason);
    }

    InvalidTeamFileException inField(Path file, String field, SquadRuleViolation violation) {
        Object[] reasonArguments = readableArguments(violation.arguments());
        String reason = messages.text(violation.rule(), reasonArguments);
        return inField(file, field, reason);
    }

    private InvalidTeamFileException inField(Path file, String field, String reason) {
        String message = messages.text(TeamFileErrorFrame.SINGLE_FIELD, file, field, reason);
        return InvalidTeamFileException.forField(file, field, message);
    }

    private Object[] readableArguments(List<Object> arguments) {
        return arguments.stream()
                .map(this::readableArgument)
                .toArray();
    }

    private Object readableArgument(Object argument) {
        if (argument instanceof List<?> items) {
            List<String> texts = items.stream()
                    .map(String::valueOf)
                    .toList();
            return String.join(LIST_SEPARATOR, texts);
        }
        return argument;
    }
}
