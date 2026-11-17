package org.tasktelemetry.matchsimulator.engine.intervention;

public enum ChangeReason {
    PROTECT_LEAD(false),
    CHASE_GAME(false),
    SEEK_WINNER(false),
    WEATHER_PRESSURE(false),
    RESPOND_TO_UNBALANCED_OPPONENT(false),
    EXPLOIT_NUMERICAL_ADVANTAGE(false),
    REINFORCE_STRUGGLING_SECTOR(false),
    REBALANCE_AFTER_DISMISSAL(false),
    AVOID_SECOND_YELLOW(false),
    REPLACE_TIRED_PLAYER(false),
    ANSWER_FRESH_ATTACKERS(false),
    INJURY(true),
    RESERVE_GOALKEEPER(true),
    EMPTIED_SECTOR(true),
    MAKESHIFT_GOALKEEPER(true);

    private final boolean forced;

    ChangeReason(boolean forced) {
        this.forced = forced;
    }

    public boolean isForced() {
        return forced;
    }
}
