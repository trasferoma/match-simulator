package org.tasktelemetry.matchsimulator.coach.human;

public enum PlayerStatus {
    ON_PITCH,
    ON_BENCH,
    SUBSTITUTED,
    SENT_OFF,
    INJURED;

    public boolean isUnavailable() {
        return this == SUBSTITUTED || this == SENT_OFF || this == INJURED;
    }
}
