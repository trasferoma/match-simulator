package org.tasktelemetry.matchsimulator.engine.event;

public enum EventType {
    KICK_OFF,
    STOPPAGE_ANNOUNCED,
    FIRST_HALF_END,
    SECOND_HALF_START,
    MATCH_END,
    MATCH_SUSPENDED,
    PASS_COMPLETED,
    DRIBBLE_COMPLETED,
    INTERCEPTION,
    TACKLE,
    BALL_CIRCULATED,
    GOAL,
    SHOT_SAVED,
    SHOT_OFF_TARGET,
    SHOT_BLOCKED,
    GOAL_KICK,
    KICK_OFF_AFTER_GOAL,
    CROSS_COMPLETED,
    CROSS_FAILED,
    CROSS_DEFLECTED_TO_CORNER,
    CLEARANCE,
    CORNER_KICK,
    FOUL,
    FREE_KICK,
    FREE_KICK_CROSS,
    OFFSIDE,
    OFFSIDE_FREE_KICK,
    YELLOW_CARD,
    SECOND_YELLOW_CARD,
    RED_CARD,
    INJURY,
    SUBSTITUTION;

    public boolean isBallRecovery() {
        return this == INTERCEPTION || this == TACKLE;
    }

    public boolean isCard() {
        return this == YELLOW_CARD || this == SECOND_YELLOW_CARD || this == RED_CARD;
    }

    public boolean isDismissal() {
        return this == SECOND_YELLOW_CARD || this == RED_CARD;
    }
}
