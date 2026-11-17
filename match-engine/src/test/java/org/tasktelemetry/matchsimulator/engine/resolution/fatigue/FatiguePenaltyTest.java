package org.tasktelemetry.matchsimulator.engine.resolution.fatigue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.tasktelemetry.matchsimulator.engine.TestEnergy;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.FatigueConfig;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;

class FatiguePenaltyTest {

    private static final double EXACT = 1e-9;

    private final TeamOnPitch team = TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 60));
    private final Player player = team.players().getLast();
    private final FatiguePenalty penalty = new FatiguePenalty(FatigueConfig.defaults());

    @ParameterizedTest
    @CsvSource({"100, 1.0", "70, 1.0", "56, 0.96", "35, 0.90", "0, 0.80"})
    void theFactorIsOneFromTheThresholdUpAndFallsLinearlyToTheMinimumAtZeroEnergy(double energy, double expected) {
        TeamOnPitch tired = TestEnergy.at(team, energy, player);

        double factor = penalty.factorOf(tired, player);

        assertThat(factor).isCloseTo(expected, within(EXACT));
    }

    @Test
    void theFactorGrowsWithTheEnergyBelowTheThreshold() {
        double exhausted = penalty.factorOf(TestEnergy.at(team, 10.0, player), player);
        double tired = penalty.factorOf(TestEnergy.at(team, 50.0, player), player);

        assertThat(exhausted).isLessThan(tired);
        assertThat(tired).isLessThan(1.0);
    }

    @Test
    void aMinimumFactorOfOneMakesEveryPlayerRenderFully() {
        FatigueConfig defaults = FatigueConfig.defaults();
        FatigueConfig withoutLoss = new FatigueConfig(defaults.performanceThreshold(), defaults.exhaustionThreshold(),
                1.0, defaults.drainPerMinute(), defaults.participationCost(), defaults.drainStaminaSensitivity(),
                defaults.neutralStamina(), defaults.goalkeeperDrainMultiplier(), defaults.recoveryShare(),
                defaults.recoveryStaminaSensitivity());

        double factor = new FatiguePenalty(withoutLoss).factorOf(TestEnergy.at(team, 0.0, player), player);

        assertThat(factor).isEqualTo(1.0);
    }

    @Test
    void theFactorOfAPlayerWhoIsNotOnThePitchIsRefused() {
        Player outsider = TestTeams.team("Away", team.team().style(), 60).starters().getFirst();

        assertThatThrownBy(() -> penalty.factorOf(team, outsider))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
