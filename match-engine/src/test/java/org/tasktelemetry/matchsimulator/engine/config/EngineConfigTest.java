package org.tasktelemetry.matchsimulator.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EngineConfigTest {

    private final ShotConfig shot = ShotConfig.defaults();
    private final SetPieceConfig setPiece = SetPieceConfig.defaults();
    private final CrossConfig cross = CrossConfig.defaults();

    @Test
    void defaultsCarryTheOpenPlayDurationsOfTheSpecification() {
        EngineConfig config = EngineConfig.defaults();

        assertThat(config.buildUpAndMidfieldDuration()).isEqualTo(new DurationRange(12, 36));
        assertThat(config.attackingThirdDuration()).isEqualTo(new DurationRange(10, 35));
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
    void rejectsMissingParameters() {
        DurationRange range = new DurationRange(1, 2);

        assertThatThrownBy(() -> new EngineConfig(null, range, 0.5, 1, shot, setPiece, cross))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EngineConfig(range, null, 0.5, 1, shot, setPiece, cross))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EngineConfig(range, range, 0.5, 1, null, setPiece, cross))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EngineConfig(range, range, 0.5, 1, shot, null, cross))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EngineConfig(range, range, 0.5, 1, shot, setPiece, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void defaultsCarryTheDribbleShareAndTheMinimumStoppage() {
        EngineConfig config = EngineConfig.defaults();

        assertThat(config.dribbleProbability()).isStrictlyBetween(0.0, 1.0);
        assertThat(config.minimumStoppageMinutes()).isGreaterThanOrEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, Double.NaN})
    void rejectsADribbleProbabilityOutsideZeroAndOne(double probability) {
        DurationRange range = new DurationRange(1, 2);

        assertThatThrownBy(() -> new EngineConfig(range, range, probability, 1, shot, setPiece, cross))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dribble probability must be between 0.0 and 1.0");
    }

    @Test
    void rejectsAMinimumStoppageBelowOneMinute() {
        DurationRange range = new DurationRange(1, 2);

        assertThatThrownBy(() -> new EngineConfig(range, range, 0.5, 0, shot, setPiece, cross))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minimum stoppage must be at least 1 minute");
    }
}
