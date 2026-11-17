package org.tasktelemetry.matchsimulator.coach.squad;

import java.util.Objects;

public record CoachProfile(String name, CoachAttributes attributes) {

    public CoachProfile {
        Objects.requireNonNull(name, "coach name must not be null");
        Objects.requireNonNull(attributes, "coach attributes must not be null");
        if (name.isBlank()) {
            throw new SquadRuleViolation(SquadRule.COACH_NAME_BLANK, "coach name must not be blank");
        }
    }
}
