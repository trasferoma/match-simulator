package org.tasktelemetry.matchsimulator.coach.config;

import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public record Formation(int defenders, int midfielders, int forwards, int grade) {

    private static final int OUTFIELD_PLAYERS = Team.STARTERS_COUNT - 1;

    public Formation {
        ConfigGuards.requireAtLeastOne("formation defenders", defenders);
        ConfigGuards.requireAtLeastOne("formation midfielders", midfielders);
        ConfigGuards.requireAtLeastOne("formation forwards", forwards);
        requireOutfieldPlayers(defenders, midfielders, forwards);
    }

    public String label() {
        return defenders + "-" + midfielders + "-" + forwards;
    }

    public int playersIn(Role role) {
        return switch (role) {
            case GOALKEEPER -> 1;
            case DEFENDER -> defenders;
            case MIDFIELDER -> midfielders;
            case FORWARD -> forwards;
        };
    }

    private static void requireOutfieldPlayers(int defenders, int midfielders, int forwards) {
        int outfieldPlayers = defenders + midfielders + forwards;
        if (outfieldPlayers != OUTFIELD_PLAYERS) {
            throw new IllegalArgumentException("a formation must have " + OUTFIELD_PLAYERS
                    + " outfield players, " + defenders + "-" + midfielders + "-" + forwards + " has "
                    + outfieldPlayers);
        }
    }
}
