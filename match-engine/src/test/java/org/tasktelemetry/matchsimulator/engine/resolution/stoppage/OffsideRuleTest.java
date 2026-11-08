package org.tasktelemetry.matchsimulator.engine.resolution.stoppage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.OffsideConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.ActionOutcome;
import org.tasktelemetry.matchsimulator.engine.resolution.PlayerPicker;
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

    private final PitchLayout layout = PitchLayout.standard();
    private final OffsideConfig config = OffsideConfig.defaults();
    private final PlayerAttributes ordinary = TestTeams.uniform(50);

    @Test
    void theProbabilityIsTheBaseWhenThePositioningIsTheNeutralOne() {
        Player receiver = forwardWithPositioning(config.neutralPositioning());

        double probability = ruleWith(config, SEED).offsideProbability(receiver);

        assertThat(probability).isCloseTo(config.baseProbability(), within(1e-9));
    }

    @Test
    void theProbabilityFollowsTheOddsShiftOfThePositioningBelowTheNeutralOne() {
        Player receiver = forwardWithPositioning(50);
        double odds = config.baseProbability() / (1.0 - config.baseProbability());
        double shiftedOdds = odds * StrictMath.exp(config.positioningSlope() * 20);
        double expected = shiftedOdds / (1.0 + shiftedOdds);

        double probability = ruleWith(config, SEED).offsideProbability(receiver);

        assertThat(probability).isCloseTo(expected, within(1e-9));
    }

    @Test
    void theProbabilityDecreasesAsThePositioningOfTheReceiverGrows() {
        OffsideRule rule = ruleWith(config, SEED);

        double poor = rule.offsideProbability(forwardWithPositioning(30));
        double neutral = rule.offsideProbability(forwardWithPositioning(70));
        double sharp = rule.offsideProbability(forwardWithPositioning(95));

        assertThat(poor).isGreaterThan(neutral);
        assertThat(neutral).isGreaterThan(sharp);
    }

    @ParameterizedTest
    @ValueSource(ints = {PlayerAttributes.MIN_VALUE, 50, PlayerAttributes.MAX_VALUE})
    void withTheDefaultConfigurationTheProbabilityIsNeverZeroNorOne(int positioning) {
        double probability = ruleWith(config, SEED).offsideProbability(forwardWithPositioning(positioning));

        assertThat(probability).isStrictlyBetween(0.0, 1.0);
    }

    @Test
    void anAdvancingPassIsOffsideInTheShareGivenByThePositioningOfTheReceivers() {
        TeamOnPitch attackers = attackersWithForwardPositioning(40, ordinary);
        OffsideRule rule = ruleWith(config, SEED);
        double expected = rule.offsideProbability(attackers.playersIn(Role.FORWARD).getFirst());

        double share = offsideShare(rule, attackers, MatchSide.HOME, ZoneRole.ATTACKING_THIRD);

        assertThat(share).isCloseTo(expected, within(0.01));
    }

    @Test
    void theShareOfOffsidesDependsOnThePositioningAndNotOnTheOtherAttributesOfTheReceiver() {
        PlayerAttributes sharpPositioningButWeakElsewhere = new PlayerAttributes(10, 10, 10, 10, 90, 10, 10, 10);
        PlayerAttributes poorPositioningButStrongElsewhere = new PlayerAttributes(90, 90, 90, 90, 10, 90, 90, 90);
        TeamOnPitch sharp = attackersWithForwardPositioning(sharpPositioningButWeakElsewhere);
        TeamOnPitch poor = attackersWithForwardPositioning(poorPositioningButStrongElsewhere);
        OffsideRule rule = ruleWith(config, SEED);
        double sharpExpected = rule.offsideProbability(sharp.playersIn(Role.FORWARD).getFirst());
        double poorExpected = rule.offsideProbability(poor.playersIn(Role.FORWARD).getFirst());

        double sharpShare = offsideShare(rule, sharp, MatchSide.HOME, ZoneRole.ATTACKING_THIRD);
        double poorShare = offsideShare(rule, poor, MatchSide.HOME, ZoneRole.ATTACKING_THIRD);

        assertThat(sharpShare).isCloseTo(sharpExpected, within(0.01));
        assertThat(poorShare).isCloseTo(poorExpected, within(0.01));
        assertThat(poorShare).isGreaterThan(sharpShare + 0.10);
    }

    @ParameterizedTest
    @EnumSource(value = ZoneRole.class, names = {"ATTACKING_THIRD", "OPPONENT_BOX"})
    void aPassArrivingInTheAttackingThirdOrInTheOpponentBoxCanBeOffside(ZoneRole arrivalRole) {
        TeamOnPitch attackers = attackersWithForwardPositioning(ordinary);
        OffsideRule rule = ruleWith(offsideAlmostCertain(), SEED);
        ActionOutcome pass = passArrivingIn(MatchSide.HOME, arrivalRole, attackers);

        ActionOutcome outcome = rule.callOffsideIfCaught(pass, attackers);

        assertThat(outcome.type()).isEqualTo(EventType.OFFSIDE);
    }

    @ParameterizedTest
    @EnumSource(value = ZoneRole.class, names = {"OWN_BOX", "DEFENCE", "MIDFIELD"})
    void aPassArrivingBeforeTheAttackingThirdIsNeverOffside(ZoneRole arrivalRole) {
        TeamOnPitch attackers = attackersWithForwardPositioning(ordinary);
        OffsideRule rule = ruleWith(offsideAlmostCertain(), SEED);
        ActionOutcome pass = passArrivingIn(MatchSide.HOME, arrivalRole, attackers);

        ActionOutcome outcome = rule.callOffsideIfCaught(pass, attackers);

        assertThat(outcome).isSameAs(pass);
    }

    @ParameterizedTest
    @EnumSource(value = EventType.class, names = {"DRIBBLE_COMPLETED", "INTERCEPTION", "TACKLE", "FOUL"})
    void anOutcomeWhichIsNotACompletedPassIsLeftAsItIs(EventType type) {
        TeamOnPitch attackers = attackersWithForwardPositioning(ordinary);
        OffsideRule rule = ruleWith(offsideAlmostCertain(), SEED);
        Player player = attackers.playersIn(Role.MIDFIELDER).getFirst();
        Zone arrival = zoneWithRole(MatchSide.HOME, ZoneRole.ATTACKING_THIRD);
        MatchState next = MatchState.openPlay(MatchSide.HOME, arrival);
        ActionOutcome outcome = ActionOutcome.byPlayer(type, player, next, PASS_SECONDS);

        ActionOutcome checked = rule.callOffsideIfCaught(outcome, attackers);

        assertThat(checked).isSameAs(outcome);
    }

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void anOffsideGivesTheBallToTheDefendersInTheirDefenceZoneForAnOffsideFreeKick(MatchSide attackingSide) {
        TeamOnPitch attackers = attackersWithForwardPositioning(ordinary);
        OffsideRule rule = ruleWith(offsideAlmostCertain(), SEED);
        ActionOutcome pass = passArrivingIn(attackingSide, ZoneRole.ATTACKING_THIRD, attackers);
        MatchSide defendingSide = attackingSide.opposite();

        ActionOutcome outcome = rule.callOffsideIfCaught(pass, attackers);

        assertThat(outcome.type()).isEqualTo(EventType.OFFSIDE);
        assertThat(outcome.next().possession()).isEqualTo(defendingSide);
        assertThat(outcome.next().zone()).isEqualTo(layout.defenceZone(defendingSide));
        assertThat(layout.roleOf(outcome.next().zone(), defendingSide)).isEqualTo(ZoneRole.DEFENCE);
        assertThat(outcome.next().phase()).isEqualTo(PlayPhase.SET_PIECE);
        assertThat(outcome.next().setPiece()).isEqualTo(SetPieceKind.OFFSIDE_FREE_KICK);
    }

    @Test
    void theOffsidePlayerIsAForwardOfTheAttackersAndThePasserIsTheSecondaryPlayer() {
        TeamOnPitch attackers = attackersWithForwardPositioning(ordinary);
        OffsideRule rule = ruleWith(offsideAlmostCertain(), SEED);
        ActionOutcome pass = passArrivingIn(MatchSide.HOME, ZoneRole.ATTACKING_THIRD, attackers);
        Player passer = pass.participants().primary().orElseThrow();

        ActionOutcome outcome = rule.callOffsideIfCaught(pass, attackers);

        Player offsidePlayer = outcome.participants().primary().orElseThrow();
        assertThat(attackers.playersIn(Role.FORWARD)).contains(offsidePlayer);
        assertThat(outcome.participants().secondary()).contains(passer);
        assertThat(outcome.participants()).isEqualTo(Participants.of(offsidePlayer, passer));
    }

    @Test
    void theOffsideKeepsTheDurationOfThePassAndCarriesNoDetail() {
        TeamOnPitch attackers = attackersWithForwardPositioning(ordinary);
        OffsideRule rule = ruleWith(offsideAlmostCertain(), SEED);
        ActionOutcome pass = passArrivingIn(MatchSide.HOME, ZoneRole.ATTACKING_THIRD, attackers);

        ActionOutcome outcome = rule.callOffsideIfCaught(pass, attackers);

        assertThat(outcome.durationSeconds()).isEqualTo(PASS_SECONDS);
        assertThat(outcome.detail()).isEqualTo(EventDetail.none());
    }

    @Test
    void aPassWhichIsNotOffsideIsReturnedUnchanged() {
        OffsideConfig almostNever = new OffsideConfig(0.000001, 70, 0.0);
        TeamOnPitch attackers = attackersWithForwardPositioning(ordinary);
        OffsideRule rule = ruleWith(almostNever, SEED);
        ActionOutcome pass = passArrivingIn(MatchSide.HOME, ZoneRole.ATTACKING_THIRD, attackers);

        ActionOutcome outcome = rule.callOffsideIfCaught(pass, attackers);

        assertThat(outcome).isSameAs(pass);
    }

    @Test
    void theSameSeedGivesTheSameOffsides() {
        TeamOnPitch attackers = attackersWithForwardPositioning(40, ordinary);
        ActionOutcome pass = passArrivingIn(MatchSide.HOME, ZoneRole.ATTACKING_THIRD, attackers);

        List<ActionOutcome> first = callMany(ruleWith(config, SEED), pass, attackers, 200);
        List<ActionOutcome> second = callMany(ruleWith(config, SEED), pass, attackers, 200);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void aCompletedPassWithoutThePasserIsRejected() {
        TeamOnPitch attackers = attackersWithForwardPositioning(ordinary);
        OffsideRule rule = ruleWith(offsideAlmostCertain(), SEED);
        Zone arrival = zoneWithRole(MatchSide.HOME, ZoneRole.ATTACKING_THIRD);
        MatchState next = MatchState.openPlay(MatchSide.HOME, arrival);
        ActionOutcome anonymousPass = new ActionOutcome(EventType.PASS_COMPLETED, Participants.nobody(), next,
                PASS_SECONDS, EventDetail.none());

        assertThatThrownBy(() -> rule.callOffsideIfCaught(anonymousPass, attackers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must name the passer");
    }

    private double offsideShare(OffsideRule rule, TeamOnPitch attackers, MatchSide side, ZoneRole arrivalRole) {
        ActionOutcome pass = passArrivingIn(side, arrivalRole, attackers);
        long offsides = callMany(rule, pass, attackers, TRIALS).stream()
                .filter(outcome -> outcome.type() == EventType.OFFSIDE)
                .count();
        return (double) offsides / TRIALS;
    }

    private List<ActionOutcome> callMany(OffsideRule rule, ActionOutcome pass, TeamOnPitch attackers, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> rule.callOffsideIfCaught(pass, attackers))
                .toList();
    }

    private ActionOutcome passArrivingIn(MatchSide side, ZoneRole arrivalRole, TeamOnPitch attackers) {
        Player passer = attackers.playersIn(Role.MIDFIELDER).getFirst();
        Zone arrival = zoneWithRole(side, arrivalRole);
        MatchState next = MatchState.openPlay(side, arrival);
        return ActionOutcome.byPlayer(EventType.PASS_COMPLETED, passer, next, PASS_SECONDS);
    }

    private Zone zoneWithRole(MatchSide side, ZoneRole role) {
        return layout.zones().stream()
                .filter(zone -> layout.roleOf(zone, side) == role)
                .findFirst()
                .orElseThrow();
    }

    private TeamOnPitch attackersWithForwardPositioning(int positioning, PlayerAttributes others) {
        PlayerAttributes forwards = new PlayerAttributes(others.passing(), others.dribbling(), others.shooting(),
                others.tackling(), positioning, others.pace(), others.aerial(), others.goalkeeping());
        return attackersWithForwardPositioning(forwards);
    }

    private TeamOnPitch attackersWithForwardPositioning(PlayerAttributes forwards) {
        return TeamOnPitch.startingWith(TestTeams.teamWithKeeperAndForwards("Home", ordinary, forwards));
    }

    private Player forwardWithPositioning(int positioning) {
        PlayerAttributes attributes = TestTeams.withDuelAttributes(50, 50, 50, positioning);
        return TestTeams.player("Receiver", Role.FORWARD, attributes);
    }

    private OffsideConfig offsideAlmostCertain() {
        return new OffsideConfig(NEARLY_CERTAIN, 70, 0.0);
    }

    private OffsideRule ruleWith(OffsideConfig offsideConfig, long seed) {
        MatchRandom random = new MatchRandom(seed);
        PlayerPicker picker = new PlayerPicker(random);
        return new OffsideRule(offsideConfig, layout, random, picker);
    }
}
