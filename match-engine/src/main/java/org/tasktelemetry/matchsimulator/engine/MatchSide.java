package org.tasktelemetry.matchsimulator.engine;

public enum MatchSide {
    HOME,
    AWAY;

    public MatchSide opposite() {
        return switch (this) {
            case HOME -> AWAY;
            case AWAY -> HOME;
        };
    }
}
