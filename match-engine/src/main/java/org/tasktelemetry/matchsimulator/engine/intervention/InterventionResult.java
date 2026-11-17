package org.tasktelemetry.matchsimulator.engine.intervention;

import java.util.Objects;

public sealed interface InterventionResult {

    static InterventionResult executed() {
        return new Executed();
    }

    static InterventionResult pending() {
        return new Pending();
    }

    static InterventionResult refused(RefusalReason reason) {
        return new Refused(reason);
    }

    default boolean isRefused() {
        return this instanceof Refused;
    }

    record Executed() implements InterventionResult {
    }

    record Pending() implements InterventionResult {
    }

    record Refused(RefusalReason reason) implements InterventionResult {

        public Refused {
            Objects.requireNonNull(reason, "refusal reason must not be null");
        }
    }
}
