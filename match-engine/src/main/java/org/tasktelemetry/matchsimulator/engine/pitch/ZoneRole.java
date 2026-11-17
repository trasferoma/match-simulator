package org.tasktelemetry.matchsimulator.engine.pitch;

public enum ZoneRole {
    OWN_BOX,
    DEFENCE,
    MIDFIELD,
    ATTACKING_THIRD,
    OPPONENT_BOX;

    public boolean isInAttackingArea() {
        return this == ATTACKING_THIRD || this == OPPONENT_BOX;
    }
}
