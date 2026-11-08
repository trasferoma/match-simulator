package org.tasktelemetry.matchsimulator.engine.resolution.shot;

import org.tasktelemetry.matchsimulator.engine.config.ShotConfig;
import org.tasktelemetry.matchsimulator.engine.event.SaveQuality;
import org.tasktelemetry.matchsimulator.engine.event.ShotQuality;

public final class ShotQualityScale {

    private final ShotConfig config;

    public ShotQualityScale(ShotConfig config) {
        this.config = config;
    }

    public ShotQuality dangerOf(double expectedGoals) {
        if (expectedGoals >= config.bigChanceThreshold()) {
            return ShotQuality.BIG_CHANCE;
        }
        if (expectedGoals >= config.halfChanceThreshold()) {
            return ShotQuality.HALF_CHANCE;
        }
        return ShotQuality.ATTEMPT;
    }

    public SaveQuality difficultyOfSaving(double goalProbability) {
        if (goalProbability >= config.spectacularSaveThreshold()) {
            return SaveQuality.SPECTACULAR;
        }
        if (goalProbability >= config.difficultSaveThreshold()) {
            return SaveQuality.DIFFICULT;
        }
        return SaveQuality.ROUTINE;
    }
}
