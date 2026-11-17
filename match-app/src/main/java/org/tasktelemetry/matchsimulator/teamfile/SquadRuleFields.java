package org.tasktelemetry.matchsimulator.teamfile;

import org.tasktelemetry.matchsimulator.coach.squad.SquadRule;

final class SquadRuleFields {

    static final String SQUAD_NAME_FIELD = "name";
    static final String COACH_NAME_FIELD = "coach.name";
    static final String TACTICAL_READING_FIELD = "coach.attributes.tacticalReading";
    static final String PLAYERS_FIELD = "players";

    String fieldOf(SquadRule rule) {
        return switch (rule) {
            case SQUAD_NAME_BLANK -> SQUAD_NAME_FIELD;
            case COACH_NAME_BLANK -> COACH_NAME_FIELD;
            case TACTICAL_READING_OUT_OF_SCALE -> TACTICAL_READING_FIELD;
            case PLAYER_NAME_REPEATED, GOALKEEPER_MISSING, NO_FEASIBLE_FORMATION -> PLAYERS_FIELD;
        };
    }
}
