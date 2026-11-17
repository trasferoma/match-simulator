package org.tasktelemetry.matchsimulator.engine.event;

import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public record Deployment(int defenders, int midfielders, int forwards) {

    private static final String LABEL_SEPARATOR = "-";

    public Deployment {
        requireNotNegative("defenders", defenders);
        requireNotNegative("midfielders", midfielders);
        requireNotNegative("forwards", forwards);
    }

    public static Deployment of(TeamOnPitch team) {
        int defenders = team.playersIn(Role.DEFENDER).size();
        int midfielders = team.playersIn(Role.MIDFIELDER).size();
        int forwards = team.playersIn(Role.FORWARD).size();
        return new Deployment(defenders, midfielders, forwards);
    }

    public String label() {
        return defenders + LABEL_SEPARATOR + midfielders + LABEL_SEPARATOR + forwards;
    }

    private static void requireNotNegative(String sector, int players) {
        if (players < 0) {
            throw new IllegalArgumentException("deployment " + sector + " must not be negative, was " + players);
        }
    }
}
