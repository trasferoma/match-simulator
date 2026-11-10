package org.tasktelemetry.matchsimulator.teamfile;

import org.tasktelemetry.matchsimulator.engine.team.TeamRule;

final class TeamRuleFields {

    private static final String TEAM_NAME_FIELD = "name";
    private static final String STARTERS_FIELD = "starters";
    private static final String PLAYERS_FIELDS = "starters, bench";

    String fieldOf(TeamRule rule) {
        return switch (rule) {
            case TEAM_NAME_BLANK -> TEAM_NAME_FIELD;
            case STARTERS_COUNT, ONE_GOALKEEPER, ROLE_MISSING -> STARTERS_FIELD;
            case PLAYER_NAME_REPEATED, PLAYER_NAME_BLANK, ATTRIBUTE_OUT_OF_SCALE -> PLAYERS_FIELDS;
        };
    }
}
