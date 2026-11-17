package org.tasktelemetry.matchsimulator.coach.squad;

import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;

public record CoachAttributes(int tacticalReading) {

    public CoachAttributes {
        requireInScale(tacticalReading);
    }

    private static void requireInScale(int tacticalReading) {
        boolean isInScale = tacticalReading >= PlayerAttributes.MIN_VALUE
                && tacticalReading <= PlayerAttributes.MAX_VALUE;
        if (!isInScale) {
            String message = "tactical reading must be between " + PlayerAttributes.MIN_VALUE + " and "
                    + PlayerAttributes.MAX_VALUE + ", was " + tacticalReading;
            throw new SquadRuleViolation(SquadRule.TACTICAL_READING_OUT_OF_SCALE, message,
                    PlayerAttributes.MIN_VALUE, PlayerAttributes.MAX_VALUE, tacticalReading);
        }
    }
}
