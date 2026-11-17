package org.tasktelemetry.matchsimulator.engine.resolution.fatigue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.TestConfigs;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.FatigueConfig;
import org.tasktelemetry.matchsimulator.engine.config.PlayStyleModifiers;
import org.tasktelemetry.matchsimulator.engine.config.StyleEffect;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.resolution.ActionOutcome;
import org.tasktelemetry.matchsimulator.engine.resolution.InvolvedPlayers;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class EnergyDrainTest {

    private static final double EXACT = 1e-9;
    private static final int HALF_A_MINUTE = 30;

    private final PitchLayout layout = PitchLayout.standard();
    private final FatigueConfig config = TestConfigs.specificationExamples();
    private final EnergyDrain drain = new EnergyDrain(config, PlayStyleModifiers.defaults());

    @ParameterizedTest
    @CsvSource({"50, 0.475", "100, 0.2375", "1, 0.70775"})
    void aPlayerInvolvedInAThirtySecondActionPaysTheBaseAndTheParticipationScaledByHisStamina(int stamina,
            double expected) {
        TeamOnPitch team = teamWithMidfielderOfStamina(PlayStyle.BALANCED, stamina);
        Player midfielder = lastOf(team);
        ActionOutcome action = action(HALF_A_MINUTE, InvolvedPlayers.of(midfielder, MatchSide.HOME));

        assertThat(drain.consumptionOf(team, MatchSide.HOME, midfielder, action)).isCloseTo(expected, within(EXACT));
        assertThat(energyAfter(team, action, midfielder)).isCloseTo(100.0 - expected, within(EXACT));
    }

    @Test
    void aPlayerWhoIsNotInvolvedPaysOnlyTheBase() {
        TeamOnPitch team = teamWithMidfielderOfStamina(PlayStyle.BALANCED, 50);
        Player midfielder = lastOf(team);
        ActionOutcome action = action(HALF_A_MINUTE, InvolvedPlayers.none());

        assertThat(energyAfter(team, action, midfielder)).isCloseTo(100.0 - 0.225, within(EXACT));
    }

    @Test
    void aGoalkeeperWhoIsNotInvolvedPaysAThirdOfTheBaseAndAnInvolvedOnePaysTheFullParticipation() {
        TeamOnPitch team = teamWithMidfielderOfStamina(PlayStyle.BALANCED, 50);
        Player goalkeeper = team.goalkeeper();
        ActionOutcome idle = action(HALF_A_MINUTE, InvolvedPlayers.none());
        ActionOutcome shotAt = action(HALF_A_MINUTE, InvolvedPlayers.of(goalkeeper, MatchSide.HOME));

        assertThat(energyAfter(team, idle, goalkeeper)).isCloseTo(100.0 - 0.0675, within(EXACT));
        assertThat(energyAfter(team, shotAt, goalkeeper)).isCloseTo(100.0 - 0.3175, within(EXACT));
    }

    @Test
    void anImprovisedGoalkeeperDrainsLikeAGoalkeeper() {
        TeamOnPitch team = teamWithMidfielderOfStamina(PlayStyle.BALANCED, 50);
        Player improvised = lastOf(team);
        TeamOnPitch inGoal = team.withRole(improvised, Role.GOALKEEPER);
        ActionOutcome idle = action(HALF_A_MINUTE, InvolvedPlayers.none());

        assertThat(energyAfter(inGoal, idle, improvised)).isCloseTo(100.0 - 0.0675, within(EXACT));
    }

    @ParameterizedTest
    @CsvSource({
            "BALANCED, 0.475, 0.225",
            "ATTACKING, 0.54625, 0.25875",
            "POSSESSION, 0.5225, 0.2475",
            "COUNTER_ATTACK, 0.475, 0.225",
            "DEFENSIVE, 0.475, 0.225"})
    void theStyleOfTheTeamMultipliesBothTheBaseAndTheParticipation(PlayStyle style, double involvedPays,
            double notInvolvedPays) {
        TeamOnPitch team = teamWithMidfielderOfStamina(style, 50);
        Player midfielder = lastOf(team);
        ActionOutcome involved = action(HALF_A_MINUTE, InvolvedPlayers.of(midfielder, MatchSide.HOME));
        ActionOutcome notInvolved = action(HALF_A_MINUTE, InvolvedPlayers.none());

        assertThat(energyAfter(team, involved, midfielder)).isCloseTo(100.0 - involvedPays, within(EXACT));
        assertThat(energyAfter(team, notInvolved, midfielder)).isCloseTo(100.0 - notInvolvedPays, within(EXACT));
    }

    @Test
    void theInvolvementIsReadTogetherWithTheSide() {
        TeamOnPitch team = teamWithMidfielderOfStamina(PlayStyle.BALANCED, 50);
        Player midfielder = lastOf(team);
        ActionOutcome involvesTheOtherSide = action(HALF_A_MINUTE, InvolvedPlayers.of(midfielder, MatchSide.AWAY));

        assertThat(energyAfter(team, MatchSide.HOME, involvesTheOtherSide, midfielder))
                .isCloseTo(100.0 - 0.225, within(EXACT));
    }

    @Test
    void aPlayerAddedTwiceToTheActionPaysTheParticipationOnce() {
        TeamOnPitch team = teamWithMidfielderOfStamina(PlayStyle.BALANCED, 50);
        Player midfielder = lastOf(team);
        InvolvedPlayers twice = InvolvedPlayers.of(midfielder, MatchSide.HOME).with(midfielder, MatchSide.HOME);
        ActionOutcome action = action(HALF_A_MINUTE, twice);

        assertThat(energyAfter(team, action, midfielder)).isCloseTo(100.0 - 0.475, within(EXACT));
    }

    @Test
    void whoIsOnlyNamedByTheEventPaysTheBaseWhileWhoPlaysTheActionPaysTheParticipation() {
        TeamOnPitch attackers = teamWithMidfielderOfStamina(PlayStyle.BALANCED, 50);
        Player crosser = attackers.playersIn(Role.MIDFIELDER).getFirst();
        Player header = lastOf(attackers);
        Participants clearance = Participants.of(attackers.players().get(1), MatchSide.AWAY, crosser, MatchSide.HOME);
        InvolvedPlayers involved = InvolvedPlayers.of(header, MatchSide.HOME);
        ActionOutcome action = new ActionOutcome(EventType.CLEARANCE, clearance, anyState(), HALF_A_MINUTE,
                EventDetail.none(), involved);

        assertThat(energyAfter(attackers, action, crosser)).isCloseTo(100.0 - 0.225, within(EXACT));
        assertThat(energyAfter(attackers, action, header)).isCloseTo(100.0 - 0.475, within(EXACT));
    }

    @Test
    void theEnergyNeverGoesBelowZeroEvenWithAHugeCostOfTheStyle() {
        Map<PlayStyle, StyleEffect> effects = new EnumMap<>(PlayStyleModifiers.defaults().effectByStyle());
        effects.put(PlayStyle.ATTACKING, StyleEffect.neutral().withEnergyCost(50.0));
        EnergyDrain costly = new EnergyDrain(config, new PlayStyleModifiers(effects));
        TeamOnPitch fresh = teamWithMidfielderOfStamina(PlayStyle.ATTACKING, 50);
        Player midfielder = lastOf(fresh);
        TeamOnPitch nearlyEmpty = fresh.withEnergy(fresh.energy().withEnergy(midfielder, 1.0));
        ActionOutcome action = action(HALF_A_MINUTE, InvolvedPlayers.of(midfielder, MatchSide.HOME));

        FatigueUpdate update = costly.drain(nearlyEmpty, MatchSide.HOME, action);

        assertThat(update.team().energy().energyOf(midfielder)).isZero();
        assertThat(update.team().energy().energyByPlayer().values()).allMatch(energy -> energy >= 0.0);
    }

    @Test
    void anActionWithoutDurationDoesNotConsumeAnything() {
        TeamOnPitch team = teamWithMidfielderOfStamina(PlayStyle.BALANCED, 50);
        Player midfielder = lastOf(team);
        ActionOutcome action = action(0, InvolvedPlayers.of(midfielder, MatchSide.HOME));

        FatigueUpdate update = drain.drain(team, MatchSide.HOME, action);

        assertThat(update.team()).isEqualTo(team);
        assertThat(update.shifts()).isEmpty();
    }

    @Test
    void aPlayerWhoCrossesAThresholdProducesOneShiftWithTheBandReached() {
        TeamOnPitch team = teamWithMidfielderOfStamina(PlayStyle.BALANCED, 50);
        Player nearTired = team.players().get(2);
        Player nearExhausted = team.players().get(5);
        TeamOnPitch tiring = team.withEnergy(team.energy().withEnergy(nearTired, 70.1)
                .withEnergy(nearExhausted, 40.1));
        ActionOutcome action = action(HALF_A_MINUTE, InvolvedPlayers.none());

        FatigueUpdate update = drain.drain(tiring, MatchSide.HOME, action);

        assertThat(update.shifts()).containsExactly(
                new FatigueUpdate.BandShift(nearTired, FatigueBand.TIRED),
                new FatigueUpdate.BandShift(nearExhausted, FatigueBand.EXHAUSTED));
    }

    @Test
    void anActionThatSkipsABandProducesASingleShiftWithTheBandReached() {
        FatigueConfig heavy = new FatigueConfig(70.0, 40.0, 0.80, 0.45, 40.0, 0.5, 50, 0.3, 0.5, 0.3);
        EnergyDrain heavyDrain = new EnergyDrain(heavy, PlayStyleModifiers.defaults());
        TeamOnPitch team = teamWithMidfielderOfStamina(PlayStyle.BALANCED, 50);
        Player midfielder = lastOf(team);
        TeamOnPitch fresh = team.withEnergy(team.energy().withEnergy(midfielder, 71.0));
        ActionOutcome action = action(HALF_A_MINUTE, InvolvedPlayers.of(midfielder, MatchSide.HOME));

        FatigueUpdate update = heavyDrain.drain(fresh, MatchSide.HOME, action);

        assertThat(update.shifts()).containsExactly(new FatigueUpdate.BandShift(midfielder, FatigueBand.EXHAUSTED));
    }

    @Test
    void aPlayerWhoStaysInTheSameBandProducesNoShift() {
        TeamOnPitch team = teamWithMidfielderOfStamina(PlayStyle.BALANCED, 50);
        ActionOutcome action = action(HALF_A_MINUTE, InvolvedPlayers.none());

        FatigueUpdate update = drain.drain(team, MatchSide.HOME, action);

        assertThat(update.shifts()).isEmpty();
    }

    @Test
    void theShiftsFollowTheOrderOfThePlayersOnThePitch() {
        TeamOnPitch team = teamWithMidfielderOfStamina(PlayStyle.BALANCED, 50);
        Player first = team.players().get(8);
        Player second = team.players().get(1);
        TeamOnPitch tiring = team.withEnergy(team.energy().withEnergy(first, 70.1).withEnergy(second, 70.1));
        ActionOutcome action = action(HALF_A_MINUTE, InvolvedPlayers.none());

        FatigueUpdate update = drain.drain(tiring, MatchSide.HOME, action);

        assertThat(update.shifts()).extracting(FatigueUpdate.BandShift::player).containsExactly(second, first);
    }

    private double energyAfter(TeamOnPitch team, ActionOutcome action, Player player) {
        return energyAfter(team, MatchSide.HOME, action, player);
    }

    private double energyAfter(TeamOnPitch team, MatchSide side, ActionOutcome action, Player player) {
        return drain.drain(team, side, action).team().energy().energyOf(player);
    }

    private ActionOutcome action(int seconds, InvolvedPlayers involved) {
        return new ActionOutcome(EventType.PASS_COMPLETED, Participants.nobody(), anyState(), seconds,
                EventDetail.none(), involved);
    }

    private MatchState anyState() {
        return MatchState.openPlay(MatchSide.HOME, layout.kickOffZone());
    }

    private Player lastOf(TeamOnPitch team) {
        return team.players().getLast();
    }

    private TeamOnPitch teamWithMidfielderOfStamina(PlayStyle style, int stamina) {
        PlayerAttributes ordinary = new PlayerAttributes(50, 50, 50, 50, 50, 50, 50, 50, 50);
        PlayerAttributes special = new PlayerAttributes(50, 50, 50, 50, 50, 50, 50, 50, stamina);
        List<Player> starters = new ArrayList<>(TestTeams.squad("T", "S", ordinary, 1, 4, 3, 2));
        starters.addAll(TestTeams.squad("T", "X", special, 0, 0, 1, 0));
        Team team = new Team("T", style, starters, List.of());
        return TeamOnPitch.startingWith(team);
    }
}
