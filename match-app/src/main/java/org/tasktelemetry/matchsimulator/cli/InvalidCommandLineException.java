package org.tasktelemetry.matchsimulator.cli;

import java.util.List;

final class InvalidCommandLineException extends RuntimeException {

    private final CommandLineProblem problem;
    private final transient List<Object> arguments;

    InvalidCommandLineException(CommandLineProblem problem, Object... arguments) {
        super(problem + " " + List.of(arguments));
        this.problem = problem;
        this.arguments = List.of(arguments);
    }

    CommandLineProblem problem() {
        return problem;
    }

    Object[] arguments() {
        return arguments.toArray();
    }
}
