package org.tasktelemetry.matchsimulator.engine.config;

import java.util.Objects;

public record EngineConfig(
        DurationRange buildUpAndMidfieldDuration,
        DurationRange attackingThirdDuration,
        double dribbleProbability,
        int minimumStoppageMinutes,
        ShotConfig shot,
        SetPieceConfig setPiece,
        CrossConfig cross) {

    public EngineConfig {
        Objects.requireNonNull(buildUpAndMidfieldDuration, "build-up and midfield duration must not be null");
        Objects.requireNonNull(attackingThirdDuration, "attacking third duration must not be null");
        Objects.requireNonNull(shot, "shot configuration must not be null");
        Objects.requireNonNull(setPiece, "set piece configuration must not be null");
        Objects.requireNonNull(cross, "cross configuration must not be null");
        requireProbability(dribbleProbability);
        requireAtLeastOneMinute(minimumStoppageMinutes);
    }

    public static EngineConfig defaults() {
        DurationRange buildUpAndMidfieldDuration = new DurationRange(12, 36);
        DurationRange attackingThirdDuration = new DurationRange(10, 35);
        double dribbleProbability = 0.30;
        int minimumStoppageMinutes = 1;
        ShotConfig shot = ShotConfig.defaults();
        SetPieceConfig setPiece = SetPieceConfig.defaults();
        CrossConfig cross = CrossConfig.defaults();
        return new EngineConfig(buildUpAndMidfieldDuration, attackingThirdDuration, dribbleProbability,
                minimumStoppageMinutes, shot, setPiece, cross);
    }

    private static void requireProbability(double dribbleProbability) {
        boolean isProbability = dribbleProbability >= 0.0 && dribbleProbability <= 1.0;
        if (!isProbability) {
            throw new IllegalArgumentException("dribble probability must be between 0.0 and 1.0, was "
                    + dribbleProbability);
        }
    }

    private static void requireAtLeastOneMinute(int minimumStoppageMinutes) {
        if (minimumStoppageMinutes < 1) {
            throw new IllegalArgumentException("minimum stoppage must be at least 1 minute, was "
                    + minimumStoppageMinutes);
        }
    }
}
