package org.tasktelemetry.matchsimulator.cli.dugout;

import java.util.List;

final class InvalidDugoutCommandException extends RuntimeException {

    private final DugoutProblem problem;
    private final transient List<Object> arguments;

    InvalidDugoutCommandException(DugoutProblem problem, Object... arguments) {
        super(problem + " " + List.of(arguments));
        this.problem = problem;
        this.arguments = List.of(arguments);
    }

    DugoutProblem problem() {
        return problem;
    }

    Object[] arguments() {
        return arguments.toArray();
    }
}
