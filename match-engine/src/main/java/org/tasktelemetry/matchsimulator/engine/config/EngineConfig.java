package org.tasktelemetry.matchsimulator.engine.config;

import java.util.Objects;

public record EngineConfig(
        DurationRange buildUpAndMidfieldDuration,
        DurationRange attackingThirdDuration,
        double dribbleProbability,
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
        double homeAdvantage,
        double duelSharpness) {

    private static final double NEUTRAL_SHARPNESS = 1.0;

    public EngineConfig(DurationRange buildUpAndMidfieldDuration, DurationRange attackingThirdDuration,
            double dribbleProbability, StoppageConfig stoppage, ShotConfig shot, SetPieceConfig setPiece,
            CrossConfig cross, CounterAttackConfig counterAttack, PlayStyleModifiers playStyles, FoulConfig foul,
            OffsideConfig offside, InjuryConfig injury, LineupConfig lineup, double homeAdvantage) {
        this(buildUpAndMidfieldDuration, attackingThirdDuration, dribbleProbability, stoppage, shot, setPiece, cross,
                counterAttack, playStyles, foul, offside, injury, lineup, homeAdvantage, NEUTRAL_SHARPNESS);
    }

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
        requireProbability(dribbleProbability);
        requirePositiveAndFinite("home advantage", homeAdvantage);
        requirePositiveAndFinite("duel sharpness", duelSharpness);
        requireCrossProbabilityBelowOneForEveryStyle(cross, playStyles);
        requireCounterAttackStartBelowOneForEveryStyle(counterAttack, playStyles);
    }

    public static EngineConfig defaults() {
        DurationRange buildUpAndMidfieldDuration = new DurationRange(12, 33);
        DurationRange attackingThirdDuration = new DurationRange(10, 37);
        double dribbleProbability = 0.30;
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
        double duelSharpness = 1.0;
        return new EngineConfig(buildUpAndMidfieldDuration, attackingThirdDuration, dribbleProbability,
                stoppage, shot, setPiece, cross, counterAttack, playStyles, foul, offside, injury,
                lineup, homeAdvantage, duelSharpness);
    }

    private static void requireProbability(double dribbleProbability) {
        boolean isProbability = dribbleProbability >= 0.0 && dribbleProbability <= 1.0;
        if (!isProbability) {
            throw new IllegalArgumentException("dribble probability must be between 0.0 and 1.0, was "
                    + dribbleProbability);
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
