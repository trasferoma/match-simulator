package org.tasktelemetry.matchsimulator.engine;

import org.tasktelemetry.matchsimulator.engine.config.ExpectedGoalsConfig;
import org.tasktelemetry.matchsimulator.engine.config.ShotConfig;

public final class TestShotConfig {

    private TestShotConfig() {
    }

    public static ShotConfig withExpectedGoals(ExpectedGoalsConfig expectedGoals) {
        ShotConfig defaults = ShotConfig.defaults();
        return new ShotConfig(expectedGoals, defaults.neutralGoalkeeping(), defaults.goalkeepingSlope(),
                defaults.blockedShare(), defaults.offTargetShare(), defaults.blockedRecoveredByAttackShare(),
                defaults.blockedToCornerShare(), defaults.savedToCornerShare(), defaults.halfChanceThreshold(), defaults.bigChanceThreshold(), defaults.difficultSaveThreshold(),
                defaults.spectacularSaveThreshold(), defaults.shotDuration(), defaults.goalWithRestartDuration(),
                defaults.penaltyDuration());
    }

    public static ShotConfig withGoalkeepingSlope(double goalkeepingSlope) {
        ShotConfig defaults = ShotConfig.defaults();
        return new ShotConfig(defaults.expectedGoals(), defaults.neutralGoalkeeping(), goalkeepingSlope,
                defaults.blockedShare(), defaults.offTargetShare(), defaults.blockedRecoveredByAttackShare(),
                defaults.blockedToCornerShare(), defaults.savedToCornerShare(), defaults.halfChanceThreshold(),
                defaults.bigChanceThreshold(), defaults.difficultSaveThreshold(), defaults.spectacularSaveThreshold(),
                defaults.shotDuration(), defaults.goalWithRestartDuration(), defaults.penaltyDuration());
    }

    public static ShotConfig withMissShares(double blockedShare, double offTargetShare,
            double blockedRecoveredByAttackShare) {
        ShotConfig defaults = ShotConfig.defaults();
        return new ShotConfig(defaults.expectedGoals(), defaults.neutralGoalkeeping(), defaults.goalkeepingSlope(),
                blockedShare, offTargetShare, blockedRecoveredByAttackShare, 0.0, 0.0, defaults.halfChanceThreshold(),
                defaults.bigChanceThreshold(), defaults.difficultSaveThreshold(), defaults.spectacularSaveThreshold(),
                defaults.shotDuration(), defaults.goalWithRestartDuration(), defaults.penaltyDuration());
    }
}
