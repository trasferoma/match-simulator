package org.tasktelemetry.matchsimulator.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;

class EngineConfigTest {

    private static final double HOME_ADVANTAGE = 1.08;

    private final ShotConfig shot = ShotConfig.defaults();
    private final SetPieceConfig setPiece = SetPieceConfig.defaults();
    private final CrossConfig cross = CrossConfig.defaults();
    private final CounterAttackConfig counterAttack = CounterAttackConfig.defaults();
    private final PlayStyleModifiers playStyles = PlayStyleModifiers.defaults();

    @Test
    void defaultsCarryTheOpenPlayDurationsOfTheSpecification() {
        EngineConfig config = EngineConfig.defaults();

        assertThat(config.buildUpAndMidfieldDuration()).isEqualTo(new DurationRange(12, 33));
        assertThat(config.attackingThirdDuration()).isEqualTo(new DurationRange(10, 37));
    }

    @Test
    void defaultsAreTheSameEveryTime() {
        assertThat(EngineConfig.defaults()).isEqualTo(EngineConfig.defaults());
    }

    @Test
    void defaultsCarryTheShotAndTheSetPieceConfigurations() {
        EngineConfig config = EngineConfig.defaults();

        assertThat(config.shot()).isEqualTo(ShotConfig.defaults());
        assertThat(config.setPiece()).isEqualTo(SetPieceConfig.defaults());
        assertThat(config.cross()).isEqualTo(CrossConfig.defaults());
    }

    @Test
    void defaultsCarryTheCounterAttackTheStyleModifiersAndTheHomeAdvantageOfTheSpecification() {
        EngineConfig config = EngineConfig.defaults();

        assertThat(config.counterAttack()).isEqualTo(CounterAttackConfig.defaults());
        assertThat(config.playStyles()).isEqualTo(PlayStyleModifiers.defaults());
        assertThat(config.homeAdvantage()).isEqualTo(HOME_ADVANTAGE);
    }

    @Test
    void rejectsMissingParameters() {
        DurationRange range = new DurationRange(1, 2);

        assertThatThrownBy(() -> configWith(null, range, 0.5, 1, shot, setPiece, cross, counterAttack, playStyles))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> configWith(range, null, 0.5, 1, shot, setPiece, cross, counterAttack, playStyles))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> configWith(range, range, 0.5, 1, null, setPiece, cross, counterAttack, playStyles))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> configWith(range, range, 0.5, 1, shot, null, cross, counterAttack, playStyles))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> configWith(range, range, 0.5, 1, shot, setPiece, null, counterAttack, playStyles))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> configWith(range, range, 0.5, 1, shot, setPiece, cross, null, playStyles))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> configWith(range, range, 0.5, 1, shot, setPiece, cross, counterAttack, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void defaultsCarryTheDribbleShareAndTheMinimumStoppage() {
        EngineConfig config = EngineConfig.defaults();

        assertThat(config.dribbleProbability()).isStrictlyBetween(0.0, 1.0);
        assertThat(config.stoppage().minimumMinutes()).isGreaterThanOrEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN})
    void rejectsADribbleProbabilityOutsideZeroAndOne(double probability) {
        DurationRange range = new DurationRange(1, 2);

        assertThatThrownBy(() -> configWith(range, range, probability, 1, shot, setPiece, cross, counterAttack,
                playStyles))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dribble probability must be between 0.0 and 1.0");
    }

    @Test
    void defaultsCarryTheDribbleThresholdWhenThereIsNoTeammateToPassTo() {
        assertThat(EngineConfig.defaults().noTeammateDribbleThreshold()).isStrictlyBetween(0.0, 1.0);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN})
    void rejectsADribbleThresholdOutsideZeroAndOne(double threshold) {
        DurationRange range = new DurationRange(1, 2);

        assertThatThrownBy(() -> new EngineConfig(range, range, 0.5, threshold, StoppageConfig.defaults(), shot,
                setPiece, cross, counterAttack, playStyles, FoulConfig.defaults(), OffsideConfig.defaults(),
                InjuryConfig.defaults(), LineupConfig.defaults(), ZonePresenceConfig.defaults(), HOME_ADVANTAGE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-teammate dribble threshold must be between 0.0 and 1.0");
    }

    @Test
    void rejectsAMinimumStoppageBelowOneMinute() {
        DurationRange range = new DurationRange(1, 2);

        assertThatThrownBy(() -> configWith(range, range, 0.5, 0, shot, setPiece, cross, counterAttack, playStyles))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimum stoppage must be at least 1 minute");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsAHomeAdvantageThatIsNotPositiveAndFinite(double homeAdvantage) {
        DurationRange range = new DurationRange(1, 2);

        assertThatThrownBy(() -> new EngineConfig(range, range, 0.5, 0.5, StoppageConfig.defaults(), shot, setPiece, cross, counterAttack,
                playStyles, FoulConfig.defaults(), OffsideConfig.defaults(), InjuryConfig.defaults(), LineupConfig.defaults(), ZonePresenceConfig.defaults(),
                homeAdvantage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("home advantage must be positive and finite");
    }

    @Test
    void rejectsAStylePropensityThatPushesTheCrossProbabilityAboveOne() {
        DurationRange range = new DurationRange(1, 2);
        CrossConfig likelyCross = new CrossConfig(0.9, 0.3, 0.2, range, range);

        assertThatThrownBy(() -> configWith(range, range, 0.5, 1, shot, setPiece, likelyCross, counterAttack,
                playStyles))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cross probability times the highest style propensity must not exceed 1.0");
    }

    @Test
    void rejectsAStyleMultiplierThatPushesTheCounterAttackStartAboveOne() {
        DurationRange range = new DurationRange(1, 2);
        CounterAttackConfig likelyStart = new CounterAttackConfig(0.9, range);

        assertThatThrownBy(() -> configWith(range, range, 0.5, 1, shot, setPiece, cross, likelyStart, playStyles))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("counter-attack start probability times the highest style multiplier must not"
                        + " exceed 1.0");
    }

    @Test
    void theBalancedStyleNeverRaisesTheLimitOfTheStyleCombinations() {
        Map<PlayStyle, StyleEffect> neutralOnly = new EnumMap<>(PlayStyle.class);
        for (PlayStyle style : PlayStyle.values()) {
            neutralOnly.put(style, StyleEffect.neutral());
        }
        PlayStyleModifiers allNeutral = new PlayStyleModifiers(neutralOnly);
        DurationRange range = new DurationRange(1, 2);
        CrossConfig certainCross = new CrossConfig(0.99, 0.3, 0.2, range, range);

        EngineConfig config = configWith(range, range, 0.5, 1, shot, setPiece, certainCross, counterAttack,
                allNeutral);

        assertThat(config.cross().crossProbability()).isEqualTo(0.99);
    }

    private EngineConfig configWith(DurationRange buildUp, DurationRange attacking, double dribbleProbability,
            int minimumStoppageMinutes, ShotConfig shotConfig, SetPieceConfig setPieceConfig,
            CrossConfig crossConfig, CounterAttackConfig counterAttackConfig, PlayStyleModifiers styles) {
        StoppageConfig stoppage = stoppageWithMinimum(minimumStoppageMinutes);
        return new EngineConfig(buildUp, attacking, dribbleProbability, 0.5, stoppage, shotConfig,
                setPieceConfig, crossConfig, counterAttackConfig, styles, FoulConfig.defaults(), OffsideConfig.defaults(),
                InjuryConfig.defaults(),
                LineupConfig.defaults(), ZonePresenceConfig.defaults(), HOME_ADVANTAGE);
    }

    private StoppageConfig stoppageWithMinimum(int minimumMinutes) {
        StoppageConfig defaults = StoppageConfig.defaults();
        return new StoppageConfig(minimumMinutes, defaults.secondsPerEvent(), defaults.maximumFirstHalfMinutes(),
                defaults.maximumSecondHalfMinutes());
    }
}
