package org.tasktelemetry.matchsimulator.teamfile;

import java.nio.file.Path;
import java.util.List;

import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.TeamRuleViolation;
import org.tasktelemetry.matchsimulator.message.UserMessages;

final class TeamFileErrors {

    private final UserMessages messages;
    private final TeamFileTerms terms;

    TeamFileErrors(UserMessages messages, TeamFileTerms terms) {
        this.messages = messages;
        this.terms = terms;
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
        List<Object> arguments = translateArguments(violation.arguments());
        Object[] reasonArguments = arguments.toArray();
        String reason = messages.text(violation.rule(), reasonArguments);
        return inField(file, field, reason);
    }

    private InvalidTeamFileException inField(Path file, String field, String reason) {
        String message = messages.text(TeamFileErrorFrame.SINGLE_FIELD, file, field, reason);
        return InvalidTeamFileException.forField(file, field, message);
    }

    private List<Object> translateArguments(List<Object> arguments) {
        return arguments.stream()
                .map(this::translateArgument)
                .toList();
    }

    private Object translateArgument(Object argument) {
        if (argument instanceof Role role) {
            return terms.termOf(role);
        }
        return argument;
    }
}
