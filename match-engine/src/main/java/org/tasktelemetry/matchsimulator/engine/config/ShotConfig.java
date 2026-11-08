package org.tasktelemetry.matchsimulator.engine.config;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;

public record ShotConfig(
        ExpectedGoalsConfig expectedGoals,
        int neutralGoalkeeping,
        double goalkeepingSlope,
        double blockedShare,
        double offTargetShare,
        double blockedRecoveredByAttackShare,
        double blockedToCornerShare,
        double savedToCornerShare,
        double halfChanceThreshold,
        double bigChanceThreshold,
        double difficultSaveThreshold,
        double spectacularSaveThreshold,
        DurationRange shotDuration,
        DurationRange goalWithRestartDuration,
        DurationRange penaltyDuration) {

    public ShotConfig {
        Objects.requireNonNull(expectedGoals, "expected goals configuration must not be null");
        Objects.requireNonNull(shotDuration, "shot duration must not be null");
        Objects.requireNonNull(goalWithRestartDuration, "goal duration must not be null");
        Objects.requireNonNull(penaltyDuration, "penalty duration must not be null");
        requireInScale(neutralGoalkeeping);
        requireNotNegative(goalkeepingSlope);
        requireShare("blocked share", blockedShare);
        requireShare("off-target share", offTargetShare);
        requireShare("blocked-recovered-by-attack share", blockedRecoveredByAttackShare);
        requireShare("blocked-to-corner share", blockedToCornerShare);
        requireShare("saved-to-corner share", savedToCornerShare);
        requireDefenceShareLeft(blockedToCornerShare, blockedRecoveredByAttackShare);
        requireSavedShareLeft(blockedShare, offTargetShare);
        requireAscendingOpenThresholds("chance thresholds", halfChanceThreshold, bigChanceThreshold);
        requireAscendingOpenThresholds("save thresholds", difficultSaveThreshold, spectacularSaveThreshold);
    }

    public static ShotConfig defaults() {
        ExpectedGoalsConfig expectedGoals = ExpectedGoalsConfig.defaults();
        int neutralGoalkeeping = 75;
        double goalkeepingSlope = 0.02;
        double blockedShare = 0.30;
        double offTargetShare = 0.33;
        double blockedRecoveredByAttackShare = 0.40;
        double blockedToCornerShare = 0.10;
        double savedToCornerShare = 0.12;
        double halfChanceThreshold = 0.08;
        double bigChanceThreshold = 0.25;
        double difficultSaveThreshold = 0.15;
        double spectacularSaveThreshold = 0.35;
        DurationRange shotDuration = new DurationRange(1, 3);
        DurationRange goalWithRestartDuration = new DurationRange(45, 75);
        DurationRange penaltyDuration = new DurationRange(60, 90);
        return new ShotConfig(expectedGoals, neutralGoalkeeping, goalkeepingSlope, blockedShare, offTargetShare,
                blockedRecoveredByAttackShare, blockedToCornerShare, savedToCornerShare, halfChanceThreshold,
                bigChanceThreshold, difficultSaveThreshold,
                spectacularSaveThreshold, shotDuration, goalWithRestartDuration, penaltyDuration);
    }

    private static void requireInScale(int rating) {
        boolean isInScale = rating >= PlayerAttributes.MIN_VALUE && rating <= PlayerAttributes.MAX_VALUE;
        if (!isInScale) {
            throw new IllegalArgumentException("neutral goalkeeping must be between " + PlayerAttributes.MIN_VALUE
                    + " and " + PlayerAttributes.MAX_VALUE + ", was " + rating);
        }
    }

    private static void requireNotNegative(double slope) {
        boolean isNotNegativeAndFinite = slope >= 0.0 && !Double.isInfinite(slope);
        if (!isNotNegativeAndFinite) {
            throw new IllegalArgumentException("goalkeeping slope must not be negative and must be finite, was "
                    + slope);
        }
    }

    private static void requireShare(String name, double share) {
        boolean isShare = share >= 0.0 && share <= 1.0;
        if (!isShare) {
            throw new IllegalArgumentException(name + " must be between 0.0 and 1.0, was " + share);
        }
    }

    private static void requireSavedShareLeft(double blockedShare, double offTargetShare) {
        double missesBeforeTheSave = blockedShare + offTargetShare;
        if (missesBeforeTheSave >= 1.0) {
            throw new IllegalArgumentException("blocked and off-target shares must leave room for saves, but they"
                    + " add up to " + missesBeforeTheSave);
        }
    }

    private static void requireDefenceShareLeft(double blockedToCornerShare, double blockedRecoveredByAttackShare) {
        double blockedBeforeTheDefenceRecovers = blockedToCornerShare + blockedRecoveredByAttackShare;
        if (blockedBeforeTheDefenceRecovers > 1.0) {
            throw new IllegalArgumentException("blocked-to-corner and blocked-recovered-by-attack shares must not"
                    + " add up to more than 1.0, but they add up to " + blockedBeforeTheDefenceRecovers);
        }
    }

    private static void requireAscendingOpenThresholds(String name, double lower, double higher) {
        boolean isAscending = lower > 0.0 && lower < higher && higher < 1.0;
        if (!isAscending) {
            throw new IllegalArgumentException(name + " must satisfy 0.0 < lower < higher < 1.0, were " + lower
                    + " and " + higher);
        }
    }
}
