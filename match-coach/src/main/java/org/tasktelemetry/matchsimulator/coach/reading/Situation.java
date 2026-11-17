package org.tasktelemetry.matchsimulator.coach.reading;

import org.tasktelemetry.matchsimulator.engine.team.Role;

public enum Situation {
    DOMINATED,
    DOMINANT,
    OPPONENT_UNBALANCED,
    DEFENCE_STRUGGLING,
    DEFENCE_IN_CRISIS,
    MIDFIELD_STRUGGLING,
    MIDFIELD_IN_CRISIS,
    ATTACK_STRUGGLING,
    ATTACK_IN_CRISIS;

    public static Situation strugglingOf(Role sector) {
        return switch (sector) {
            case DEFENDER -> DEFENCE_STRUGGLING;
            case MIDFIELDER -> MIDFIELD_STRUGGLING;
            case FORWARD -> ATTACK_STRUGGLING;
            case GOALKEEPER -> throw new IllegalArgumentException("the goalkeeper is not a sector that struggles");
        };
    }

    public static Situation crisisOf(Role sector) {
        return switch (sector) {
            case DEFENDER -> DEFENCE_IN_CRISIS;
            case MIDFIELDER -> MIDFIELD_IN_CRISIS;
            case FORWARD -> ATTACK_IN_CRISIS;
            case GOALKEEPER -> throw new IllegalArgumentException("the goalkeeper is not a sector in crisis");
        };
    }
}
