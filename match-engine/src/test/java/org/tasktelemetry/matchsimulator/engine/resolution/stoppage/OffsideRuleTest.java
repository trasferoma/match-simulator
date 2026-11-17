package org.tasktelemetry.matchsimulator.engine.resolution.stoppage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.tasktelemetry.matchsimulator.engine.resolution.TestInvolvement.involvedOn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.TestResolvers;
import org.tasktelemetry.matchsimulator.engine.TestEnergy;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.OffsideConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.ActionOutcome;
import org.tasktelemetry.matchsimulator.engine.resolution.InvolvedPlayers;
import org.tasktelemetry.matchsimulator.engine.resolution.OutOfPositionPenalty;
import org.tasktelemetry.matchsimulator.engine.resolution.PlayerPicker;
import org.tasktelemetry.matchsimulator.engine.resolution.fatigue.FatiguePenalty;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.state.SetPieceKind;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class OffsideRuleTest {

    private static final long SEED = 21L;
    private static final int TRIALS = 100_000;
    private static final int PASS_SECONDS = 17;
    private static final double NEARLY_CERTAIN = 0.999999;
    private static final int SAME_POSITIONING = 60;

    private final PitchLayout layout = PitchLayout.standard();
    private final OffsideConfig config = OffsideConfig.defaults();
    private final PlayerAttributes ordinary = TestTeams.uniform(50);
    private final TeamOnPitch homeTeam = TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 50));
    private final TeamOnPitch defenders = TeamOnPitch.startingWith(TestTeams.uniformTeam("Away", 50));

    @Test
    void theProbabilityIsTheBaseWhenReceiverAndDefenderHaveTheSamePositioning() {
        Player receiver = forwardWithPositioning(SAME_POSITIONING);
        Player defender = defenderWithPositioning(SAME_POSITIONING);

        double probability = ruleWith(config, SEED).offsideProbability(TestEnergy.withPlayerOnPitch(homeTeam, receiver), receiver, TestEnergy.withPlayerOnPitch(defenders, defender), defender);

        assertThat(probability).isCloseTo(config.baseProbability(), within(1e-9));
    }

    @Test
    void theProbabilityFollowsTheOddsShiftOfTheDifferenceBetweenDefenderAndReceiverPositioning() {
        Player receiver = forwardWithPositioning(50);
        Player defender = defenderWithPositioning(70);
        double odds = config.baseProbability() / (1.0 - config.baseProbability());
        double shiftedOdds = odds * StrictMath.exp(config.positioningSlope() * 20);
        double expected = shiftedOdds / (1.0 + shiftedOdds);

        double probability = ruleWith(config, SEED).offsideProbability(TestEnergy.withPlayerOnPitch(homeTeam, receiver), receiver, TestEnergy.withPlayerOnPitch(defenders, defender), defender);

        assertThat(probability).isCloseTo(expected, within(1e-9));
    }

    @Test
    void aReceiverOutOfPositionIsCaughtAsIfHisPositioningWereWeakenedByTheConfiguredMultiplier() {
        OffsideRule rule = ruleWith(config, SEED);
        Player receiver = homeTeam.playersIn(Role.FORWARD).getFirst();
        Player defender = defenders.playersIn(Role.DEFENDER).getFirst();
        int weakened = weakenedByTheOutOfPositionMultiplier(receiver.attributes().positioning());
        TeamOnPitch receiverInMidfield = homeTeam.withRole(receiver, Role.MIDFIELDER);

        double outOfPosition = rule.offsideProbability(TestEnergy.withPlayerOnPitch(receiverInMidfield, receiver), receiver, TestEnergy.withPlayerOnPitch(defenders, defender), defender);
        double inPosition = rule.offsideProbability(TestEnergy.withPlayerOnPitch(homeTeam, receiver), receiver, TestEnergy.withPlayerOnPitch(defenders, defender), defender);
        double weakenedReceiver = rule.offsideProbability(TestEnergy.withPlayerOnPitch(homeTeam, forwardWithPositioning(weakened)), forwardWithPositioning(weakened), TestEnergy.withPlayerOnPitch(defenders, defender), defender);

        assertThat(outOfPosition).isGreaterThan(inPosition);
        assertThat(outOfPosition).isCloseTo(weakenedReceiver, within(1e-9));
    }

    @Test
    void aDefenderOutOfPositionLowersTheProbabilityAsIfHisPositioningWereWeakenedByTheConfiguredMultiplier() {
        OffsideRule rule = ruleWith(config, SEED);
        Player receiver = homeTeam.playersIn(Role.FORWARD).getFirst();
        Player defender = defenders.playersIn(Role.DEFENDER).getFirst();
        int weakened = weakenedByTheOutOfPositionMultiplier(defender.attributes().positioning());
        TeamOnPitch defenderInMidfield = defenders.withRole(defender, Role.MIDFIELDER);

        double outOfPosition = rule.offsideProbability(TestEnergy.withPlayerOnPitch(homeTeam, receiver), receiver, TestEnergy.withPlayerOnPitch(defenderInMidfield, defender), defender);
        double inPosition = rule.offsideProbability(TestEnergy.withPlayerOnPitch(homeTeam, receiver), receiver, TestEnergy.withPlayerOnPitch(defenders, defender), defender);
        double weakenedDefender = rule.offsideProbability(TestEnergy.withPlayerOnPitch(homeTeam, receiver), receiver, TestEnergy.withPlayerOnPitch(defenders, defenderWithPositioning(weakened)), defenderWithPositioning(weakened));

        assertThat(outOfPosition).isLessThan(inPosition);
        assertThat(outOfPosition).isCloseTo(weakenedDefender, within(1e-9));
    }

    @Test
    void whenBothAreOutOfPositionAndHaveTheSamePositioningTheProbabilityIsTheBase() {
        OffsideRule rule = ruleWith(config, SEED);
        Player receiver = homeTeam.playersIn(Role.FORWARD).getFirst();
        Player defender = defenders.playersIn(Role.DEFENDER).getFirst();
        TeamOnPitch receiverInMidfield = homeTeam.withRole(receiver, Role.MIDFIELDER);
        TeamOnPitch defenderInMidfield = defenders.withRole(defender, Role.MIDFIELDER);

        double probability = rule.offsideProbability(TestEnergy.withPlayerOnPitch(receiverInMidfield, receiver), receiver, TestEnergy.withPlayerOnPitch(defenderInMidfield, defender), defender);

        assertThat(probability).isCloseTo(config.baseProbability(), within(1e-9));
    }

    @Test
    void theProbabilityDependsOnlyOnTheDifferenceOfThePositioning() {
        OffsideRule rule = ruleWith(config, SEED);

        double lowPair = rule.offsideProbability(TestEnergy.withPlayerOnPitch(homeTeam, forwardWithPositioning(30)), forwardWithPositioning(30), TestEnergy.withPlayerOnPitch(defenders, defenderWithPositioning(50)), defenderWithPositioning(50));
        double highPair = rule.offsideProbability(TestEnergy.withPlayerOnPitch(homeTeam, forwardWithPositioning(70)), forwardWithPositioning(70), TestEnergy.withPlayerOnPitch(defenders, defenderWithPositioning(90)), defenderWithPositioning(90));

        assertThat(lowPair).isCloseTo(highPair, within(1e-9));
    }

    @Test
    void theProbabilityRisesAsTheDefenderPositioningGrowsAndFallsAsTheReceiverPositioningGrows() {
        OffsideRule rule = ruleWith(config, SEED);
        Player receiver = forwardWithPositioning(SAME_POSITIONING);
        Player defender = defenderWithPositioning(SAME_POSITIONING);
        double base = rule.offsideProbability(TestEnergy.withPlayerOnPitch(homeTeam, receiver), receiver, TestEnergy.withPlayerOnPitch(defenders, defender), defender);

        double sharperDefender = rule.offsideProbability(TestEnergy.withPlayerOnPitch(homeTeam, receiver), receiver, TestEnergy.withPlayerOnPitch(defenders, defenderWithPositioning(SAME_POSITIONING + 10)), defenderWithPositioning(SAME_POSITIONING + 10));
        double sharperReceiver = rule.offsideProbability(TestEnergy.withPlayerOnPitch(homeTeam, forwardWithPositioning(SAME_POSITIONING + 10)), forwardWithPositioning(SAME_POSITIONING + 10), TestEnergy.withPlayerOnPitch(defenders, defender), defender);

        assertThat(sharperDefender).isGreaterThan(base);
        assertThat(sharperReceiver).isLessThan(base);
    }

    @ParameterizedTest
    @ValueSource(ints = {PlayerAttributes.MIN_VALUE, 50, PlayerAttributes.MAX_VALUE})
    void withTheDefaultConfigurationTheProbabilityIsNeverZeroNorOne(int positioning) {
        OffsideRule rule = ruleWith(config, SEED);
        Player receiver = forwardWithPositioning(positioning);
        Player weakestDefender = defenderWithPositioning(PlayerAttributes.MIN_VALUE);
        Player strongestDefender = defenderWithPositioning(PlayerAttributes.MAX_VALUE);

        double againstTheWeakest = rule.offsideProbability(TestEnergy.withPlayerOnPitch(homeTeam, receiver), receiver, TestEnergy.withPlayerOnPitch(defenders, weakestDefender), weakestDefender);
        double againstTheStrongest = rule.offsideProbability(TestEnergy.withPlayerOnPitch(homeTeam, receiver), receiver, TestEnergy.withPlayerOnPitch(defenders, strongestDefender), strongestDefender);

        assertThat(againstTheWeakest).isStrictlyBetween(0.0, 1.0);
        assertThat(againstTheStrongest).isStrictlyBetween(0.0, 1.0);
    }

    @Test
    void anAdvancingPassIsOffsideInTheShareGivenByThePositioningOfReceiversAndDefenders() {
        TeamOnPitch attackers = attackersWithOutfield(40, ordinary);
        TeamOnPitch backline = defendersWithPositioning(80);
        OffsideRule rule = ruleWith(config, SEED);
        Player receiver = attackers.playersIn(Role.FORWARD).getFirst();
        Player defender = backline.playersIn(Role.DEFENDER).getFirst();
        double expected = rule.offsideProbability(TestEnergy.withPlayerOnPitch(attackers, receiver), receiver, TestEnergy.withPlayerOnPitch(backline, defender), defender);

        double share = offsideShare(rule, attackers, backline, MatchSide.HOME, ZoneRole.ATTACKING_THIRD);

        assertThat(share).isCloseTo(expected, within(0.01));
    }

    @Test
    void theShareOfOffsidesDependsOnThePositioningOfTheReceiverAndNotOnHisOtherAttributes() {
        PlayerAttributes sharpPositioningButWeakElsewhere = new PlayerAttributes(10, 10, 10, 10, 90, 10, 10, 10, 50);
        PlayerAttributes poorPositioningButStrongElsewhere = new PlayerAttributes(90, 90, 90, 90, 10, 90, 90, 90, 50);
        TeamOnPitch sharp = attackersWithOutfield(sharpPositioningButWeakElsewhere);
        TeamOnPitch poor = attackersWithOutfield(poorPositioningButStrongElsewhere);
        OffsideRule rule = ruleWith(config, SEED);
        Player defender = defenders.playersIn(Role.DEFENDER).getFirst();
        double sharpExpected = rule.offsideProbability(TestEnergy.withPlayerOnPitch(sharp, sharp.playersIn(Role.FORWARD).getFirst()), sharp.playersIn(Role.FORWARD).getFirst(), TestEnergy.withPlayerOnPitch(defenders, defender), defender);
        double poorExpected = rule.offsideProbability(TestEnergy.withPlayerOnPitch(poor, poor.playersIn(Role.FORWARD).getFirst()), poor.playersIn(Role.FORWARD).getFirst(), TestEnergy.withPlayerOnPitch(defenders, defender), defender);

        double sharpShare = offsideShare(rule, sharp, defenders, MatchSide.HOME, ZoneRole.ATTACKING_THIRD);
        double poorShare = offsideShare(rule, poor, defenders, MatchSide.HOME, ZoneRole.ATTACKING_THIRD);

        assertThat(sharpShare).isCloseTo(sharpExpected, within(0.01));
        assertThat(poorShare).isCloseTo(poorExpected, within(0.01));
        assertThat(poorShare).isGreaterThan(sharpShare + 0.10);
    }

    @Test
    void theShareOfOffsidesDependsOnThePositioningOfTheDefenderAndNotOnHisOtherAttributes() {
        PlayerAttributes sharpPositioningButWeakElsewhere = new PlayerAttributes(10, 10, 10, 10, 90, 10, 10, 10, 50);
        PlayerAttributes poorPositioningButStrongElsewhere = new PlayerAttributes(90, 90, 90, 90, 10, 90, 90, 90, 50);
        TeamOnPitch attackers = attackersWithOutfield(ordinary);
        TeamOnPitch sharp = defendersWith(sharpPositioningButWeakElsewhere);
        TeamOnPitch poor = defendersWith(poorPositioningButStrongElsewhere);
        OffsideRule rule = ruleWith(config, SEED);
        Player receiver = attackers.playersIn(Role.FORWARD).getFirst();
        double sharpExpected = rule.offsideProbability(TestEnergy.withPlayerOnPitch(attackers, receiver), receiver, TestEnergy.withPlayerOnPitch(sharp, sharp.playersIn(Role.DEFENDER).getFirst()), sharp.playersIn(Role.DEFENDER).getFirst());
        double poorExpected = rule.offsideProbability(TestEnergy.withPlayerOnPitch(attackers, receiver), receiver, TestEnergy.withPlayerOnPitch(poor, poor.playersIn(Role.DEFENDER).getFirst()), poor.playersIn(Role.DEFENDER).getFirst());

        double sharpShare = offsideShare(rule, attackers, sharp, MatchSide.HOME, ZoneRole.ATTACKING_THIRD);
        double poorShare = offsideShare(rule, attackers, poor, MatchSide.HOME, ZoneRole.ATTACKING_THIRD);

        assertThat(sharpShare).isCloseTo(sharpExpected, within(0.01));
        assertThat(poorShare).isCloseTo(poorExpected, within(0.01));
        assertThat(sharpShare).isGreaterThan(poorShare + 0.10);
    }

    @ParameterizedTest
    @EnumSource(value = ZoneRole.class, names = {"ATTACKING_THIRD", "OPPONENT_BOX"})
    void aPassArrivingInTheAttackingThirdOrInTheOpponentBoxCanBeOffside(ZoneRole arrivalRole) {
        TeamOnPitch attackers = attackersWithOutfield(ordinary);
        OffsideRule rule = ruleWith(offsideAlmostCertain(), SEED);
        ActionOutcome pass = passArrivingIn(MatchSide.HOME, arrivalRole, attackers);

        ActionOutcome outcome = rule.callOffsideIfCaught(pass, attackers, defenders);

        assertThat(outcome.type()).isEqualTo(EventType.OFFSIDE);
    }

    @ParameterizedTest
    @EnumSource(value = ZoneRole.class, names = {"OWN_BOX", "DEFENCE", "MIDFIELD"})
    void aPassArrivingBeforeTheAttackingThirdIsNeverOffside(ZoneRole arrivalRole) {
        TeamOnPitch attackers = attackersWithOutfield(ordinary);
        OffsideRule rule = ruleWith(offsideAlmostCertain(), SEED);
        ActionOutcome pass = passArrivingIn(MatchSide.HOME, arrivalRole, attackers);

        ActionOutcome outcome = rule.callOffsideIfCaught(pass, attackers, defenders);

        assertThat(outcome).isSameAs(pass);
    }

    @ParameterizedTest
    @EnumSource(value = EventType.class, names = {"DRIBBLE_COMPLETED", "INTERCEPTION", "TACKLE", "FOUL"})
    void anOutcomeWhichIsNotACompletedPassIsLeftAsItIs(EventType type) {
        TeamOnPitch attackers = attackersWithOutfield(ordinary);
        OffsideRule rule = ruleWith(offsideAlmostCertain(), SEED);
        Player player = attackers.playersIn(Role.MIDFIELDER).getFirst();
        Zone arrival = zoneWithRole(MatchSide.HOME, ZoneRole.ATTACKING_THIRD);
        MatchState next = MatchState.openPlay(MatchSide.HOME, arrival);
        ActionOutcome outcome = ActionOutcome.byPlayer(type, player, MatchSide.HOME, next, PASS_SECONDS,
                InvolvedPlayers.none());

        ActionOutcome checked = rule.callOffsideIfCaught(outcome, attackers, defenders);

        assertThat(checked).isSameAs(outcome);
    }

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void anOffsideGivesTheBallToTheDefendersInTheirDefenceZoneForAnOffsideFreeKick(MatchSide attackingSide) {
        TeamOnPitch attackers = attackersWithOutfield(ordinary);
        OffsideRule rule = ruleWith(offsideAlmostCertain(), SEED);
        ActionOutcome pass = passArrivingIn(attackingSide, ZoneRole.ATTACKING_THIRD, attackers);
        MatchSide defendingSide = attackingSide.opposite();

        ActionOutcome outcome = rule.callOffsideIfCaught(pass, attackers, defenders);

        assertThat(outcome.type()).isEqualTo(EventType.OFFSIDE);
        assertThat(outcome.next().possession()).isEqualTo(defendingSide);
        assertThat(outcome.next().zone()).isEqualTo(layout.defenceZone(defendingSide));
        assertThat(layout.roleOf(outcome.next().zone(), defendingSide)).isEqualTo(ZoneRole.DEFENCE);
        assertThat(outcome.next().phase()).isEqualTo(PlayPhase.SET_PIECE);
        assertThat(outcome.next().setPiece()).isEqualTo(SetPieceKind.OFFSIDE_FREE_KICK);
    }

    @Test
    void theOffsidePlayerIsAnOutfieldPlayerOfTheAttackersAndThePasserIsTheSecondaryPlayer() {
        TeamOnPitch attackers = attackersWithOutfield(ordinary);
        OffsideRule rule = ruleWith(offsideAlmostCertain(), SEED);
        ActionOutcome pass = passArrivingIn(MatchSide.HOME, ZoneRole.ATTACKING_THIRD, attackers);
        Player passer = pass.participants().primary().orElseThrow();

        ActionOutcome outcome = rule.callOffsideIfCaught(pass, attackers, defenders);

        Player offsidePlayer = outcome.participants().primary().orElseThrow();
        assertThat(attackers.players()).contains(offsidePlayer);
        assertThat(attackers.roleOf(offsidePlayer)).isNotEqualTo(Role.GOALKEEPER);
        assertThat(offsidePlayer).isNotEqualTo(passer);
        assertThat(outcome.participants().secondary()).contains(passer);
        assertThat(outcome.participants()).isEqualTo(Participants.of(offsidePlayer, MatchSide.HOME, passer, MatchSide.HOME));
    }

    @Test
    void theOffsidePlayerIsNeverThePasserEvenWhenBothBelongToTheArrivalSector() {
        TeamOnPitch attackers = attackersWithOutfield(ordinary);
        OffsideRule rule = ruleWith(offsideAlmostCertain(), SEED);
        Player passer = attackers.playersIn(Role.FORWARD).getFirst();
        ActionOutcome pass = passFrom(passer, MatchSide.HOME, ZoneRole.OPPONENT_BOX);

        List<ActionOutcome> outcomes = callMany(rule, pass, attackers, defenders, 500);

        assertThat(outcomes).allSatisfy(outcome -> {
            assertThat(outcome.type()).isEqualTo(EventType.OFFSIDE);
            assertThat(outcome.participants().primary()).isNotEmpty().isNotEqualTo(Optional.of(passer));
        });
    }

    @Test
    void theOffsideKeepsTheDurationOfThePassAndCarriesNoDetail() {
        TeamOnPitch attackers = attackersWithOutfield(ordinary);
        OffsideRule rule = ruleWith(offsideAlmostCertain(), SEED);
        ActionOutcome pass = passArrivingIn(MatchSide.HOME, ZoneRole.ATTACKING_THIRD, attackers);

        ActionOutcome outcome = rule.callOffsideIfCaught(pass, attackers, defenders);

        assertThat(outcome.durationSeconds()).isEqualTo(PASS_SECONDS);
        assertThat(outcome.detail()).isEqualTo(EventDetail.none());
    }

    @Test
    void aPassWhichIsNotOffsideIsReturnedUnchanged() {
        OffsideConfig almostNever = new OffsideConfig(0.000001, 0.0);
        TeamOnPitch attackers = attackersWithOutfield(ordinary);
        OffsideRule rule = ruleWith(almostNever, SEED);
        ActionOutcome pass = passArrivingIn(MatchSide.HOME, ZoneRole.ATTACKING_THIRD, attackers);

        ActionOutcome outcome = rule.callOffsideIfCaught(pass, attackers, defenders);

        assertThat(outcome).usingRecursiveComparison().ignoringFields("involved").isEqualTo(pass);
    }

    @Test
    void aPassWhichIsNotOffsideInvolvesAlsoTheReceiverAndTheDefenderOfTheCheck() {
        OffsideConfig almostNever = new OffsideConfig(0.000001, 0.0);
        TeamOnPitch attackers = attackersWithOutfield(ordinary);
        OffsideRule rule = ruleWith(almostNever, SEED);
        ActionOutcome pass = passArrivingIn(MatchSide.HOME, ZoneRole.ATTACKING_THIRD, attackers);

        ActionOutcome outcome = rule.callOffsideIfCaught(pass, attackers, defenders);

        List<InvolvedPlayers.Involvement> involvements = outcome.involved().involvements();
        Player passer = pass.participants().primary().orElseThrow();
        assertThat(involvements).hasSize(3);
        assertThat(involvements.get(0)).isEqualTo(new InvolvedPlayers.Involvement(passer, MatchSide.HOME));
        assertThat(involvements.get(1).side()).isEqualTo(MatchSide.HOME);
        assertThat(involvements.get(2).side()).isEqualTo(MatchSide.AWAY);
        assertThat(attackers.players()).contains(involvements.get(1).player());
        assertThat(defenders.players()).contains(involvements.get(2).player());
    }

    @Test
    void theSameSeedGivesTheSameOffsides() {
        TeamOnPitch attackers = attackersWithOutfield(40, ordinary);
        ActionOutcome pass = passArrivingIn(MatchSide.HOME, ZoneRole.ATTACKING_THIRD, attackers);

        List<ActionOutcome> first = callMany(ruleWith(config, SEED), pass, attackers, defenders, 200);
        List<ActionOutcome> second = callMany(ruleWith(config, SEED), pass, attackers, defenders, 200);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void aCompletedPassWithoutThePasserIsRejected() {
        TeamOnPitch attackers = attackersWithOutfield(ordinary);
        OffsideRule rule = ruleWith(offsideAlmostCertain(), SEED);
        Zone arrival = zoneWithRole(MatchSide.HOME, ZoneRole.ATTACKING_THIRD);
        MatchState next = MatchState.openPlay(MatchSide.HOME, arrival);
        ActionOutcome anonymousPass = new ActionOutcome(EventType.PASS_COMPLETED, Participants.nobody(), next,
                PASS_SECONDS, EventDetail.none(), InvolvedPlayers.none());

        assertThatThrownBy(() -> rule.callOffsideIfCaught(anonymousPass, attackers, defenders))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must name the passer");
    }

    private double offsideShare(OffsideRule rule, TeamOnPitch attackers, TeamOnPitch backline, MatchSide side,
            ZoneRole arrivalRole) {
        ActionOutcome pass = passArrivingIn(side, arrivalRole, attackers);
        long offsides = callMany(rule, pass, attackers, backline, TRIALS).stream()
                .filter(outcome -> outcome.type() == EventType.OFFSIDE)
                .count();
        return (double) offsides / TRIALS;
    }

    private List<ActionOutcome> callMany(OffsideRule rule, ActionOutcome pass, TeamOnPitch attackers,
            TeamOnPitch backline, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> rule.callOffsideIfCaught(pass, attackers, backline))
                .toList();
    }

    private ActionOutcome passFrom(Player passer, MatchSide side, ZoneRole arrivalRole) {
        Zone arrival = zoneWithRole(side, arrivalRole);
        MatchState next = MatchState.openPlay(side, arrival);
        return ActionOutcome.byPlayer(EventType.PASS_COMPLETED, passer, side, next, PASS_SECONDS,
                InvolvedPlayers.of(passer, side));
    }

    private ActionOutcome passArrivingIn(MatchSide side, ZoneRole arrivalRole, TeamOnPitch attackers) {
        Player passer = attackers.playersIn(Role.MIDFIELDER).getFirst();
        Zone arrival = zoneWithRole(side, arrivalRole);
        MatchState next = MatchState.openPlay(side, arrival);
        return ActionOutcome.byPlayer(EventType.PASS_COMPLETED, passer, side, next, PASS_SECONDS,
                InvolvedPlayers.of(passer, side));
    }

    private Zone zoneWithRole(MatchSide side, ZoneRole role) {
        return layout.zones().stream()
                .filter(zone -> layout.roleOf(zone, side) == role)
                .findFirst()
                .orElseThrow();
    }

    private TeamOnPitch attackersWithOutfield(int positioning, PlayerAttributes others) {
        PlayerAttributes outfield = new PlayerAttributes(others.passing(), others.dribbling(), others.shooting(),
                others.tackling(), positioning, others.pace(), others.aerial(), others.goalkeeping(),
                others.stamina());
        return attackersWithOutfield(outfield);
    }

    private TeamOnPitch attackersWithOutfield(PlayerAttributes outfield) {
        List<Player> starters = new ArrayList<>();
        starters.addAll(TestTeams.squad("Home", "S", ordinary, 1, 0, 0, 0));
        starters.addAll(TestTeams.squad("Home", "S", outfield, 0, 4, 4, 2));
        return TeamOnPitch.startingWith(TestTeams.teamOf("Home", starters));
    }

    private TeamOnPitch defendersWithPositioning(int positioning) {
        return defendersWith(TestTeams.withDuelAttributes(50, 50, 50, positioning));
    }

    private TeamOnPitch defendersWith(PlayerAttributes outfieldAttributes) {
        List<Player> starters = new ArrayList<>();
        starters.addAll(TestTeams.squad("Away", "S", ordinary, 1, 0, 0, 0));
        starters.addAll(TestTeams.squad("Away", "D", outfieldAttributes, 0, 4, 4, 2));
        return TeamOnPitch.startingWith(TestTeams.teamOf("Away", starters));
    }

    private Player forwardWithPositioning(int positioning) {
        PlayerAttributes attributes = TestTeams.withDuelAttributes(50, 50, 50, positioning);
        return TestTeams.player("Receiver", Role.FORWARD, attributes);
    }

    private Player defenderWithPositioning(int positioning) {
        PlayerAttributes attributes = TestTeams.withDuelAttributes(50, 50, 50, positioning);
        return TestTeams.player("Marker", Role.DEFENDER, attributes);
    }

    private int weakenedByTheOutOfPositionMultiplier(int positioning) {
        double multiplier = EngineConfig.defaults().lineup().outOfPositionMultiplier();
        return (int) Math.round(positioning * multiplier);
    }

    private OffsideConfig offsideAlmostCertain() {
        return new OffsideConfig(NEARLY_CERTAIN, 0.0);
    }

    private OffsideRule ruleWith(OffsideConfig offsideConfig, long seed) {
        MatchRandom random = new MatchRandom(seed);
        PlayerPicker picker = TestResolvers.playerPicker(EngineConfig.defaults(), random);
        OutOfPositionPenalty outOfPosition = TestResolvers.outOfPositionPenalty(EngineConfig.defaults());
        FatiguePenalty fatigue = TestResolvers.fatiguePenalty(EngineConfig.defaults());
        return new OffsideRule(offsideConfig, layout, random, picker, outOfPosition, fatigue);
    }

    @Test
    void anOffsideKeepsThePlayersOfThePassAndInvolvesAlsoTheReceiverAndTheDefenderOfTheCheck() {
        TeamOnPitch attackers = attackersWithOutfield(ordinary);
        OffsideRule rule = ruleWith(offsideAlmostCertain(), SEED);
        ActionOutcome pass = passArrivingIn(MatchSide.HOME, ZoneRole.ATTACKING_THIRD, attackers);
        Player passer = pass.participants().primary().orElseThrow();

        ActionOutcome offside = rule.callOffsideIfCaught(pass, attackers, defenders);

        Player receiver = offside.participants().primary().orElseThrow();
        assertThat(offside.type()).isEqualTo(EventType.OFFSIDE);
        assertThat(involvedOn(offside, MatchSide.HOME)).containsExactly(passer, receiver);
        assertThat(involvedOn(offside, MatchSide.AWAY)).hasSize(1)
                .allSatisfy(defender -> assertThat(defenders.players()).contains(defender));
    }

    @Test
    void aTiredReceiverIsCaughtAsIfHisPositioningWereLoweredByTheFatigueFactor() {
        OffsideRule rule = ruleWith(config, SEED);
        Player receiver = homeTeam.playersIn(Role.FORWARD).getFirst();
        Player defender = defenders.playersIn(Role.DEFENDER).getFirst();
        TeamOnPitch tiredAttackers = TestEnergy.at(homeTeam, 35.0, receiver);
        Player lowered = forwardWithPositioning(45);

        double tired = rule.offsideProbability(tiredAttackers, receiver, defenders, defender);
        double equivalent = rule.offsideProbability(TestEnergy.withPlayerOnPitch(homeTeam, lowered), lowered,
                defenders, defender);
        double rested = rule.offsideProbability(homeTeam, receiver, defenders, defender);

        assertThat(tired).isGreaterThan(rested);
        assertThat(tired).isCloseTo(equivalent, within(1e-9));
    }

    @Test
    void aTiredDefenderLowersTheProbabilityAsIfHisPositioningWereLoweredByTheFatigueFactor() {
        OffsideRule rule = ruleWith(config, SEED);
        Player receiver = homeTeam.playersIn(Role.FORWARD).getFirst();
        Player defender = defenders.playersIn(Role.DEFENDER).getFirst();
        TeamOnPitch tiredDefenders = TestEnergy.at(defenders, 35.0, defender);
        Player lowered = defenderWithPositioning(45);

        double tired = rule.offsideProbability(homeTeam, receiver, tiredDefenders, defender);
        double equivalent = rule.offsideProbability(homeTeam, receiver,
                TestEnergy.withPlayerOnPitch(defenders, lowered), lowered);
        double rested = rule.offsideProbability(homeTeam, receiver, defenders, defender);

        assertThat(tired).isLessThan(rested);
        assertThat(tired).isCloseTo(equivalent, within(1e-9));
    }
}
