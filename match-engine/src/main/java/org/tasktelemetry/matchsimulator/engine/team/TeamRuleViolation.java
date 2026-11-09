package org.tasktelemetry.matchsimulator.engine.team;

import java.util.List;
import java.util.Objects;

public final class TeamRuleViolation extends IllegalArgumentException {

    private final TeamRule rule;
    private final transient List<Object> arguments;

    TeamRuleViolation(TeamRule rule, String message, Object... arguments) {
        super(message);
        this.rule = Objects.requireNonNull(rule, "rule must not be null");
        this.arguments = List.of(arguments);
    }

    public TeamRule rule() {
        return rule;
    }

    public List<Object> arguments() {
        return arguments;
    }
}
