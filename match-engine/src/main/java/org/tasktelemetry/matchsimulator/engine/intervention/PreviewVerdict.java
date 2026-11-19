package org.tasktelemetry.matchsimulator.engine.intervention;

import java.util.Objects;

public sealed interface PreviewVerdict {

    static PreviewVerdict accepted() {
        return new Accepted();
    }

    static PreviewVerdict refused(RefusalReason reason) {
        return new Refused(reason);
    }

    default boolean isAccepted() {
        return this instanceof Accepted;
    }

    record Accepted() implements PreviewVerdict {
    }

    record Refused(RefusalReason reason) implements PreviewVerdict {

        public Refused {
            Objects.requireNonNull(reason, "refusal reason must not be null");
        }
    }
}
