package org.tasktelemetry.matchsimulator.narration;

import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;

public enum StyleAbbreviation {

    BALANCED,
    ATTACKING,
    DEFENSIVE,
    POSSESSION,
    COUNTER_ATTACK;

    public static StyleAbbreviation of(PlayStyle style) {
        return switch (style) {
            case BALANCED -> BALANCED;
            case ATTACKING -> ATTACKING;
            case DEFENSIVE -> DEFENSIVE;
            case POSSESSION -> POSSESSION;
            case COUNTER_ATTACK -> COUNTER_ATTACK;
        };
    }
}
