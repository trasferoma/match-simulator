package org.tasktelemetry.matchsimulator.coach.decision;

public enum DecisionMoment {
    FIRST_HALF_EMERGENCY(false, false, false),
    HALF_TIME(true, true, true),
    SECOND_HALF(true, false, true);

    private final boolean decidesBeyondEmergencies;
    private final boolean ignoresThePause;
    private final boolean respondsToMildSectorStrain;

    DecisionMoment(boolean decidesBeyondEmergencies, boolean ignoresThePause, boolean respondsToMildSectorStrain) {
        this.decidesBeyondEmergencies = decidesBeyondEmergencies;
        this.ignoresThePause = ignoresThePause;
        this.respondsToMildSectorStrain = respondsToMildSectorStrain;
    }

    public boolean decidesBeyondEmergencies() {
        return decidesBeyondEmergencies;
    }

    public boolean ignoresThePause() {
        return ignoresThePause;
    }

    public boolean respondsToMildSectorStrain() {
        return respondsToMildSectorStrain;
    }
}
