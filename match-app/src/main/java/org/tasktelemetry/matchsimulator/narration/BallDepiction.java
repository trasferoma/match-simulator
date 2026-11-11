package org.tasktelemetry.matchsimulator.narration;

import org.tasktelemetry.matchsimulator.engine.event.EventType;

public enum BallDepiction {
    IN_PLAY,
    STOPPED_IN_EVENT_ZONE;

    public static BallDepiction of(EventType type) {
        return switch (type) {
            case INJURY, STOPPAGE_ANNOUNCED, FIRST_HALF_END, MATCH_END, MATCH_SUSPENDED, SUBSTITUTION ->
                    STOPPED_IN_EVENT_ZONE;
            case FOUL, YELLOW_CARD, SECOND_YELLOW_CARD, RED_CARD, OFFSIDE,
                    KICK_OFF, SECOND_HALF_START, KICK_OFF_AFTER_GOAL, PASS_COMPLETED, DRIBBLE_COMPLETED,
                    INTERCEPTION, TACKLE, BALL_CIRCULATED, GOAL, SHOT_SAVED, SHOT_OFF_TARGET, SHOT_BLOCKED,
                    GOAL_KICK, CROSS_COMPLETED, CROSS_FAILED, CROSS_DEFLECTED_TO_CORNER, CLEARANCE, CORNER_KICK,
                    FREE_KICK, FREE_KICK_CROSS, OFFSIDE_FREE_KICK -> IN_PLAY;
        };
    }
}
