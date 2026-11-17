package org.tasktelemetry.matchsimulator.engine.resolution.fatigue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.FatigueConfig;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class HalfTimeRecoveryTest {

    private static final double EXACT = 1e-9;

    private final HalfTimeRecovery recovery = new HalfTimeRecovery(FatigueConfig.defaults());

    @ParameterizedTest
    @CsvSource({"50, 85.0", "100, 89.5", "1, 80.59"})
    void aPlayerAtSeventyRecoversAShareOfTheMissingEnergyThatGrowsWithTheStamina(int stamina, double expected) {
        TeamOnPitch team = teamWith(PlayStyle.BALANCED, stamina);
        Player player = team.players().getLast();
        TeamOnPitch tired = withEnergy(team, player, 70.0);

        FatigueUpdate update = recovery.recover(tired);

        assertThat(update.team().energy().energyOf(player)).isCloseTo(expected, within(EXACT));
    }

    @Test
    void theStyleOfTheTeamDoesNotChangeTheRecovery() {
        TeamOnPitch balanced = teamWith(PlayStyle.BALANCED, 50);
        TeamOnPitch attacking = teamWith(PlayStyle.ATTACKING, 50);
        Player player = balanced.players().getLast();
        TeamOnPitch tiredBalanced = withEnergy(balanced, player, 70.0);
        TeamOnPitch tiredAttacking = withEnergy(attacking, attacking.players().getLast(), 70.0);

        double afterBalanced = recovery.recover(tiredBalanced).team().energy().energyOf(player);
        double afterAttacking = recovery.recover(tiredAttacking).team().energy()
                .energyOf(attacking.players().getLast());

        assertThat(afterAttacking).isEqualTo(afterBalanced);
    }

    @Test
    void aPlayerAtFullEnergyStaysAtFullEnergy() {
        TeamOnPitch team = teamWith(PlayStyle.BALANCED, 100);
        TeamOnPitch rested = recovery.recover(team).team();

        assertThat(rested.energy().energyByPlayer().values()).containsOnly(100.0);
    }

    @Test
    void aPlayerWhoMovesToABetterBandProducesAShiftWithTheBandReached() {
        TeamOnPitch team = teamWith(PlayStyle.BALANCED, 50);
        Player exhausted = team.players().get(2);
        Player tired = team.players().get(4);
        TeamOnPitch tiring = team.withEnergy(team.energy().withEnergy(exhausted, 30.0).withEnergy(tired, 60.0));

        FatigueUpdate update = recovery.recover(tiring);

        assertThat(update.shifts()).containsExactly(
                new FatigueUpdate.BandShift(exhausted, FatigueBand.TIRED),
                new FatigueUpdate.BandShift(tired, FatigueBand.FRESH));
    }

    @Test
    void aPlayerWhoStaysInTheSameBandProducesNoShift() {
        TeamOnPitch team = teamWith(PlayStyle.BALANCED, 1);
        Player exhausted = team.players().getLast();
        TeamOnPitch tiring = team.withEnergy(team.energy().withEnergy(exhausted, 5.0));

        FatigueUpdate update = recovery.recover(tiring);

        assertThat(update.team().energy().energyOf(exhausted)).isCloseTo(5.0 + 95.0 * 0.353, within(EXACT));
        assertThat(update.shifts()).isEmpty();
    }

    @Test
    void theShiftsFollowTheOrderOfThePlayersOnThePitch() {
        TeamOnPitch team = teamWith(PlayStyle.BALANCED, 50);
        Player later = team.players().get(9);
        Player earlier = team.players().get(1);
        TeamOnPitch tiring = team.withEnergy(team.energy().withEnergy(later, 60.0).withEnergy(earlier, 60.0));

        FatigueUpdate update = recovery.recover(tiring);

        assertThat(update.shifts()).extracting(FatigueUpdate.BandShift::player).containsExactly(earlier, later);
    }

    private TeamOnPitch withEnergy(TeamOnPitch team, Player player, double energy) {
        return team.withEnergy(team.energy().withEnergy(player, energy));
    }

    private TeamOnPitch teamWith(PlayStyle style, int staminaOfTheLastMidfielder) {
        PlayerAttributes ordinary = new PlayerAttributes(50, 50, 50, 50, 50, 50, 50, 50, 50);
        PlayerAttributes special = new PlayerAttributes(50, 50, 50, 50, 50, 50, 50, 50, staminaOfTheLastMidfielder);
        List<Player> starters = new ArrayList<>(TestTeams.squad("T", "S", ordinary, 1, 4, 3, 2));
        starters.addAll(TestTeams.squad("T", "X", special, 0, 0, 1, 0));
        Team team = new Team("T", style, starters, List.of());
        return TeamOnPitch.startingWith(team);
    }
}
