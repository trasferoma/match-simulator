package org.tasktelemetry.matchsimulator.coach.squad;

import java.util.List;
import java.util.Objects;

public final class SquadRuleViolation extends IllegalArgumentException {

    private final SquadRule rule;
    private final transient List<Object> arguments;

    public SquadRuleViolation(SquadRule rule, String message, Object... arguments) {
        super(message);
        this.rule = Objects.requireNonNull(rule, "rule must not be null");
        this.arguments = List.of(arguments);
    }

    public SquadRule rule() {
        return rule;
    }

    public List<Object> arguments() {
        return arguments;
    }
}
