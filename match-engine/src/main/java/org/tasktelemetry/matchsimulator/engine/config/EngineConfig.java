package org.tasktelemetry.matchsimulator.engine.config;

import java.util.Objects;

public record EngineConfig(
        DurationRange buildUpAndMidfieldDuration,
        DurationRange attackingThirdDuration,
        double dribbleProbability,
        double noTeammateDribbleThreshold,
        StoppageConfig stoppage,
        ShotConfig shot,
        SetPieceConfig setPiece,
        CrossConfig cross,
        CounterAttackConfig counterAttack,
        PlayStyleModifiers playStyles,
        FoulConfig foul,
        OffsideConfig offside,
        InjuryConfig injury,
        LineupConfig lineup,
        double homeAdvantage) {

    public EngineConfig {
        Objects.requireNonNull(buildUpAndMidfieldDuration, "build-up and midfield duration must not be null");
        Objects.requireNonNull(attackingThirdDuration, "attacking third duration must not be null");
        Objects.requireNonNull(stoppage, "stoppage configuration must not be null");
        Objects.requireNonNull(shot, "shot configuration must not be null");
        Objects.requireNonNull(setPiece, "set piece configuration must not be null");
        Objects.requireNonNull(cross, "cross configuration must not be null");
        Objects.requireNonNull(counterAttack, "counter-attack configuration must not be null");
        Objects.requireNonNull(playStyles, "play style modifiers must not be null");
        Objects.requireNonNull(foul, "foul configuration must not be null");
        Objects.requireNonNull(offside, "offside configuration must not be null");
        Objects.requireNonNull(injury, "injury configuration must not be null");
        Objects.requireNonNull(lineup, "lineup configuration must not be null");
        requireProbability("dribble probability", dribbleProbability);
        requireProbability("no-teammate dribble threshold", noTeammateDribbleThreshold);
        requirePositiveAndFinite("home advantage", homeAdvantage);
        requireCrossProbabilityBelowOneForEveryStyle(cross, playStyles);
        requireCounterAttackStartBelowOneForEveryStyle(counterAttack, playStyles);
    }

    public static EngineConfig defaults() {
        DurationRange buildUpAndMidfieldDuration = new DurationRange(12, 33);
        DurationRange attackingThirdDuration = new DurationRange(10, 37);
        double dribbleProbability = 0.30;
        double noTeammateDribbleThreshold = 0.50;
        StoppageConfig stoppage = StoppageConfig.defaults();
        ShotConfig shot = ShotConfig.defaults();
        SetPieceConfig setPiece = SetPieceConfig.defaults();
        CrossConfig cross = CrossConfig.defaults();
        CounterAttackConfig counterAttack = CounterAttackConfig.defaults();
        PlayStyleModifiers playStyles = PlayStyleModifiers.defaults();
        FoulConfig foul = FoulConfig.defaults();
        OffsideConfig offside = OffsideConfig.defaults();
        InjuryConfig injury = InjuryConfig.defaults();
        LineupConfig lineup = LineupConfig.defaults();
        double homeAdvantage = 1.08;
        return new EngineConfig(buildUpAndMidfieldDuration, attackingThirdDuration, dribbleProbability,
                noTeammateDribbleThreshold, stoppage, shot, setPiece, cross, counterAttack, playStyles, foul,
                offside, injury, lineup, homeAdvantage);
    }

    private static void requireProbability(String name, double value) {
        boolean isProbability = value >= 0.0 && value <= 1.0;
        if (!isProbability) {
            throw new IllegalArgumentException(name + " must be between 0.0 and 1.0, was " + value);
        }
    }

    private static void requirePositiveAndFinite(String name, double value) {
        boolean isPositiveAndFinite = value > 0.0 && !Double.isInfinite(value);
        if (!isPositiveAndFinite) {
            throw new IllegalArgumentException(name + " must be positive and finite, was " + value);
        }
    }

    private static void requireCrossProbabilityBelowOneForEveryStyle(CrossConfig cross,
            PlayStyleModifiers playStyles) {
        double highest = cross.crossProbability() * playStyles.highestCrossPropensity();
        if (highest > 1.0) {
            throw new IllegalArgumentException("cross probability times the highest style propensity must not"
                    + " exceed 1.0, was " + highest);
        }
    }

    private static void requireCounterAttackStartBelowOneForEveryStyle(CounterAttackConfig counterAttack,
            PlayStyleModifiers playStyles) {
        double highest = counterAttack.startProbability() * playStyles.highestCounterAttackStart();
        if (highest > 1.0) {
            throw new IllegalArgumentException("counter-attack start probability times the highest style"
                    + " multiplier must not exceed 1.0, was " + highest);
        }
    }
}
