package org.tasktelemetry.matchsimulator.engine.resolution.shot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.TestConfigs;
import org.tasktelemetry.matchsimulator.engine.TestResolvers;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.CrossConfig;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.ZonePresenceConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.ActionOutcome;
import org.tasktelemetry.matchsimulator.engine.resolution.Duel;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.state.SetPieceKind;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class CrossResolverTest {

    private static final long SEED = 31L;
    private static final int TRIALS = 20_000;
    private static final int MANY_TRIALS = 100_000;
    private static final double ONE_POINT = 0.01;
    private static final double EXACT = 1e-9;
    private static final double EVEN_DUEL = 0.5;
    private static final Set<EventType> SHOT_TYPES = EnumSet.of(EventType.GOAL, EventType.SHOT_SAVED,
            EventType.SHOT_OFF_TARGET, EventType.SHOT_BLOCKED);

    private final PitchLayout layout = PitchLayout.standard();
    private final EngineConfig engineConfig = TestConfigs.withoutHomeAdvantage(EngineConfig.defaults());
    private final EngineConfig withVenueAndStyles = EngineConfig.defaults();
    private final CrossConfig config = engineConfig.cross();
    private final TeamOnPitch home = TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 60));
    private final TeamOnPitch away = TeamOnPitch.startingWith(TestTeams.uniformTeam("Away", 60));

    @ParameterizedTest
    @CsvSource({"BALANCED, 0.20", "ATTACKING, 0.23", "DEFENSIVE, 0.20", "POSSESSION, 0.17", "COUNTER_ATTACK, 0.20"})
    void theProbabilityOfACrossIsTheConfiguredOneTimesThePropensityOfTheStyleOfTheAttackers(PlayStyle style,
            double expected) {
        double probability = resolverWith(config).crossProbability(style);

        assertThat(probability).isCloseTo(expected, within(EXACT));
    }

    @Test
    void theCrossIsTheDuelOfThePassingOfTheCrosserAgainstThePositioningOfTheDefender() {
        CrossResolver resolver = resolverWith(config);
        TeamOnPitch attackers = teamWith("Attackers", playerAttributes(80, 50));
        TeamOnPitch defenders = teamWith("Defenders", defenderAttributes(40, 90));
        MatchState state = openPlayOnTheAttackingThird(MatchSide.HOME);
        Player crosser = attackers.playersIn(Role.MIDFIELDER).getFirst();
        Player defender = defenders.playersIn(Role.DEFENDER).getFirst();

        double probability = resolver.crossDuel(state, attackers, crosser, defenders, defender)
                .attackerWinProbability();

        assertThat(probability).isCloseTo(80.0 / (80.0 + 40.0), within(EXACT));
    }

    @Test
    void thePositioningOfTheDefenderLowersTheCrossWhileTheTacklingOfTheDefenderDoesNotChangeIt() {
        CrossResolver resolver = resolverWith(config);
        TeamOnPitch attackers = teamWith("Attackers", playerAttributes(80, 50));
        TeamOnPitch defenders = teamWith("Defenders", defenderAttributes(40, 90));
        TeamOnPitch weakTackling = teamWith("WeakTackling", defenderAttributes(40, 10));
        TeamOnPitch betterPositioned = teamWith("BetterPositioned", defenderAttributes(60, 90));
        MatchState state = openPlayOnTheAttackingThird(MatchSide.HOME);
        Player crosser = attackers.playersIn(Role.MIDFIELDER).getFirst();

        double base = crossProbability(resolver, state, attackers, crosser, defenders);
        double sameButWeakTackling = crossProbability(resolver, state, attackers, crosser, weakTackling);
        double betterPositionedProbability = crossProbability(resolver, state, attackers, crosser, betterPositioned);

        assertThat(sameButWeakTackling).isCloseTo(base, within(EXACT));
        assertThat(betterPositionedProbability).isLessThan(base);
        assertThat(betterPositionedProbability).isCloseTo(80.0 / (80.0 + 60.0), within(EXACT));
    }

    @ParameterizedTest
    @CsvSource({"90, 10, 60", "30, 90, 60"})
    void theFrequencyOfCompletedCrossesFollowsThePassingAgainstThePositioningNotTheAerialOfTheCrosser(int passing,
            int aerial, int positioning) {
        TeamOnPitch crossers = teamWith("Crossers", playerAttributes(passing, aerial));
        TeamOnPitch defenders = teamWith("Defenders", new PlayerAttributes(50, 50, 50, 50, positioning, 50, 50, 50));
        MatchState state = openPlayOnTheAttackingThird(MatchSide.HOME);
        CrossResolver resolver = resolverWith(config);
        double expected = (double) passing / (passing + positioning);

        List<ActionOutcome> outcomes = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolveCross(state, crossers, defenders))
                .toList();

        assertThat(share(outcomes, EventType.CROSS_COMPLETED)).isCloseTo(expected, within(ONE_POINT));
        assertThat(outcomes).extracting(ActionOutcome::type)
                .containsOnly(EventType.CROSS_COMPLETED, EventType.CROSS_FAILED,
                        EventType.CROSS_DEFLECTED_TO_CORNER);
    }

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void aCompletedCrossPutsTheBallInTheBoxOfTheDefendersWithTheCrosserInFlight(MatchSide side) {
        MatchState state = openPlayOnTheAttackingThird(side);
        TeamOnPitch attackers = teamOf(side);
        Zone defendersBox = layout.ownBoxZone(side.opposite());

        List<ActionOutcome> completed = crosses(state, attackers, teamOf(side.opposite()),
                EventType.CROSS_COMPLETED);

        assertThat(completed).isNotEmpty().allSatisfy(outcome -> {
            Player crosser = outcome.participants().primary().orElseThrow();
            assertThat(attackers.players()).contains(crosser);
            assertThat(outcome.participants().secondary()).isEmpty();
            MatchState inFlight = MatchState.openPlay(side, defendersBox).withCrossInFlightFrom(crosser);
            assertThat(outcome.next()).isEqualTo(inFlight);
            assertThat(config.crossDuration().contains(outcome.durationSeconds())).isTrue();
            assertThat(outcome.detail()).isEqualTo(EventDetail.none());
        });
    }

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void aFailedCrossGivesTheBallToTheDefendersInTheirOwnBox(MatchSide side) {
        MatchState state = openPlayOnTheAttackingThird(side);
        TeamOnPitch attackers = teamOf(side);
        Zone defendersBox = layout.ownBoxZone(side.opposite());

        List<ActionOutcome> failed = crosses(state, attackers, teamOf(side.opposite()), EventType.CROSS_FAILED);

        assertThat(failed).isNotEmpty().allSatisfy(outcome -> {
            assertThat(attackers.players()).contains(outcome.participants().primary().orElseThrow());
            assertThat(outcome.next()).isEqualTo(MatchState.openPlay(side.opposite(), defendersBox));
            assertThat(config.crossDuration().contains(outcome.durationSeconds())).isTrue();
        });
    }

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void aCrossDeflectedToACornerNamesTheDefenderOfTheDuelAndTheCrosserAndGivesTheCornerToTheCrossingSide(
            MatchSide side) {
        MatchState state = openPlayOnTheAttackingThird(side);
        TeamOnPitch attackers = teamOf(side);
        TeamOnPitch defenders = teamOf(side.opposite());
        Zone defendersBox = layout.ownBoxZone(side.opposite());

        List<ActionOutcome> deflected = crosses(state, attackers, defenders, EventType.CROSS_DEFLECTED_TO_CORNER);

        assertThat(deflected).isNotEmpty().allSatisfy(outcome -> {
            Player defender = outcome.participants().primary().orElseThrow();
            Player crosser = outcome.participants().secondary().orElseThrow();
            assertThat(defender.role()).isNotEqualTo(Role.GOALKEEPER);
            assertThat(defenders.players()).contains(defender);
            assertThat(attackers.players()).contains(crosser);
            assertThat(outcome.next()).isEqualTo(MatchState.forSetPiece(side, defendersBox, SetPieceKind.CORNER));
            assertThat(config.crossDuration().contains(outcome.durationSeconds())).isTrue();
        });
    }

    @Test
    void aFailedCrossIsDeflectedToACornerAtTheConfiguredShare() {
        MatchState state = openPlayOnTheAttackingThird(MatchSide.HOME);
        CrossResolver resolver = resolverWith(config);

        List<ActionOutcome> failed = IntStream.range(0, MANY_TRIALS)
                .mapToObj(index -> resolver.resolveCross(state, home, away))
                .filter(outcome -> outcome.type() != EventType.CROSS_COMPLETED)
                .toList();

        assertThat(share(failed, EventType.CROSS_DEFLECTED_TO_CORNER))
                .isCloseTo(config.deflectedToCornerShare(), within(ONE_POINT));
    }

    @Test
    void theCrosserIsAnOutfieldPlayerOfAnyLineAndNeverTheGoalkeeperEvenWhenAnOutfielderPlaysInGoal() {
        Player midfielderInGoal = home.playersIn(Role.MIDFIELDER).get(0);
        TeamOnPitch withMidfielderInGoal = home.withRole(midfielderInGoal, Role.GOALKEEPER);
        CrossResolver resolver = resolverWith(config);

        List<Player> crossers = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.pickCrosser(withMidfielderInGoal))
                .toList();

        assertThat(crossers).extracting(Player::role).contains(Role.DEFENDER, Role.MIDFIELDER, Role.FORWARD);
        assertThat(crossers).doesNotContain(midfielderInGoal);
        assertThat(crossers).extracting(Player::role).doesNotContain(Role.GOALKEEPER);
    }

    @Test
    void theCrosserIsDrawnWithAWeightProportionalToThePassing() {
        Player skilled = TestTeams.player("Skilled", Role.MIDFIELDER, playerAttributes(90, 50));
        Player modest = TestTeams.player("Modest", Role.MIDFIELDER, playerAttributes(30, 50));
        List<Player> starters = new ArrayList<>(TestTeams.squad("W", "S", playerAttributes(50, 50), 1, 4, 2, 2));
        starters.addAll(List.of(skilled, modest));
        TeamOnPitch team = TeamOnPitch.startingWith(TestTeams.teamOf("W", starters));
        CrossResolver resolver = resolverWith(config);
        double totalPassing = 90.0 + 30.0 + 8 * 50.0;

        List<Player> crossers = IntStream.range(0, MANY_TRIALS)
                .mapToObj(index -> resolver.pickCrosser(team))
                .toList();

        double skilledShare = crossers.stream().filter(skilled::equals).count() / (double) MANY_TRIALS;
        double modestShare = crossers.stream().filter(modest::equals).count() / (double) MANY_TRIALS;
        assertThat(skilledShare).isCloseTo(90.0 / totalPassing, within(ONE_POINT));
        assertThat(modestShare).isCloseTo(30.0 / totalPassing, within(ONE_POINT));
    }

    @Test
    void theOnlyPlayerWithPresenceInTheOpponentBoxNeverCrosses() {
        List<Player> starters = TestTeams.squad("Solo", "S", playerAttributes(50, 50), 1, 4, 5, 1);
        TeamOnPitch soloForward = TeamOnPitch.startingWith(TestTeams.teamOf("Solo", starters));
        Player forward = soloForward.playersIn(Role.FORWARD).get(0);
        EngineConfig onlyForwardsInTheBox = TestConfigs.withZonePresence(engineConfig, onlyForwardsInTheOpponentBox());
        CrossResolver resolver = resolverFor(onlyForwardsInTheBox, SEED);

        List<Player> crossers = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.pickCrosser(soloForward))
                .toList();

        assertThat(crossers).isNotEmpty().doesNotContain(forward);
    }

    @Test
    void aLoneForwardCanCrossWhenOtherPlayersHavePresenceInTheOpponentBox() {
        List<Player> starters = TestTeams.squad("Solo", "S", playerAttributes(50, 50), 1, 4, 5, 1);
        TeamOnPitch soloForward = TeamOnPitch.startingWith(TestTeams.teamOf("Solo", starters));
        Player forward = soloForward.playersIn(Role.FORWARD).get(0);
        CrossResolver resolver = resolverWith(config);

        List<Player> crossers = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.pickCrosser(soloForward))
                .toList();

        assertThat(crossers).contains(forward);
    }

    @Test
    void theHeaderIsNeverTheCrosserEvenWhenTheCrosserIsAForwardOfTheTeam() {
        Zone defendersBox = layout.ownBoxZone(MatchSide.AWAY);
        Player forwardCrosser = home.playersIn(Role.FORWARD).get(0);
        MatchState inFlight = MatchState.openPlay(MatchSide.HOME, defendersBox).withCrossInFlightFrom(forwardCrosser);
        CrossResolver resolver = resolverWith(config);

        List<ActionOutcome> headers = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolveAerialDuel(inFlight, Score.goalless(), home, away))
                .filter(outcome -> SHOT_TYPES.contains(outcome.type()))
                .toList();

        assertThat(headers).isNotEmpty().allSatisfy(outcome -> assertThat(outcome.participants().primary())
                .isNotEqualTo(Optional.of(forwardCrosser)));
    }

    @Test
    void aCornerCrossIsFirstADuelOfTheCrosserAgainstTheDefenderAndOnlyThenAnAerialDuel() {
        Zone box = layout.ownBoxZone(MatchSide.AWAY);
        TeamOnPitch strongPassers = teamWith("Strong", playerAttributes(90, 50));
        TeamOnPitch strongDefenders = teamWith("Closers", new PlayerAttributes(50, 50, 50, 50, 90, 50, 50, 50));
        Player taker = strongPassers.playersIn(Role.MIDFIELDER).get(0);
        MatchState corner = MatchState.forSetPiece(MatchSide.HOME, box, SetPieceKind.CORNER);
        MatchState fromCorner = corner.withCrossInFlightFrom(taker);
        CrossResolver resolver = resolverWith(config);

        List<ActionOutcome> outcomes = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolveCrossInFlight(fromCorner, Score.goalless(), strongPassers,
                        strongDefenders))
                .toList();

        long reachingTheBox = outcomes.stream()
                .filter(outcome -> SHOT_TYPES.contains(outcome.type()) || outcome.type() == EventType.CLEARANCE)
                .count();
        assertThat(reachingTheBox / (double) TRIALS).isCloseTo(90.0 / (90.0 + 90.0), within(ONE_POINT));
        assertThat(outcomes).filteredOn(outcome -> outcome.type() == EventType.CROSS_DEFLECTED_TO_CORNER)
                .isNotEmpty()
                .allSatisfy(outcome -> assertThat(outcome.next()).isEqualTo(corner));
        assertThat(outcomes).filteredOn(outcome -> outcome.type() == EventType.CROSS_FAILED)
                .isNotEmpty()
                .allSatisfy(outcome -> assertThat(outcome.next()).isEqualTo(MatchState.openPlay(MatchSide.AWAY, box)));
    }

    @Test
    void aCrossInFlightFromOpenPlayGoesStraightToTheAerialDuelBecauseItsCrossDuelWasAlreadyWon() {
        MatchState inFlight = crossInFlight(MatchSide.HOME, home);
        CrossResolver resolver = resolverWith(config);

        List<ActionOutcome> outcomes = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolveCrossInFlight(inFlight, Score.goalless(), home, away))
                .toList();

        assertThat(outcomes).extracting(ActionOutcome::type)
                .allMatch(type -> SHOT_TYPES.contains(type) || type == EventType.CLEARANCE);
    }

    @Test
    void theAerialDuelIsWonByTheAttackerWithTheProbabilityOfTheirAerialAgainstTheAerialOfTheDefender() {
        TeamOnPitch attackers = teamWith("Attackers", new PlayerAttributes(30, 30, 50, 30, 30, 50, 80, 50));
        TeamOnPitch defenders = teamWith("Defenders", new PlayerAttributes(90, 90, 50, 90, 90, 50, 40, 50));
        MatchState inFlight = crossInFlight(MatchSide.HOME, attackers);
        CrossResolver resolver = resolverWith(config);

        List<ActionOutcome> outcomes = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolveAerialDuel(inFlight, Score.goalless(), attackers, defenders))
                .toList();

        long headers = outcomes.stream().filter(outcome -> SHOT_TYPES.contains(outcome.type())).count();
        assertThat((double) headers / TRIALS).isCloseTo(80.0 / 120.0, within(ONE_POINT));
        assertThat(outcomes).extracting(ActionOutcome::type)
                .allMatch(type -> SHOT_TYPES.contains(type) || type == EventType.CLEARANCE);
    }

    @Test
    void aWonAerialDuelIsAHeaderByAnOutfieldPlayerOfTheAttackersWithTheCrosserNamedUnlessItIsASave() {
        MatchState inFlight = crossInFlight(MatchSide.HOME, home);
        Player crosser = inFlight.crosser();
        CrossResolver resolver = resolverWith(config);

        List<ActionOutcome> outcomes = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolveAerialDuel(inFlight, Score.goalless(), home, away))
                .filter(outcome -> SHOT_TYPES.contains(outcome.type()))
                .toList();

        assertThat(outcomes).isNotEmpty().allSatisfy(outcome -> {
            Player header = outcome.participants().primary().orElseThrow();
            assertThat(header.role()).isNotEqualTo(Role.GOALKEEPER);
            assertThat(home.players()).contains(header);
            assertThat(header).isNotEqualTo(crosser);
            assertThat(shotKindOf(outcome.detail())).isEqualTo(ShotKind.HEADER);
        });
        assertThat(outcomes).extracting(outcome -> outcome.participants().primary().orElseThrow().role())
                .contains(Role.FORWARD);
        assertThat(outcomes).filteredOn(outcome -> outcome.type() != EventType.SHOT_SAVED)
                .allSatisfy(outcome -> assertThat(outcome.participants().secondary()).contains(crosser));
    }

    @Test
    void aLostAerialDuelIsAClearanceByADefenderThatNamesTheCrosser() {
        MatchState inFlight = crossInFlight(MatchSide.HOME, home);
        Player crosser = inFlight.crosser();
        Zone inFrontOfTheBox = layout.retreat(inFlight.zone(), MatchSide.HOME).orElseThrow();
        List<ActionOutcome> clearances = clearances(inFlight, MANY_TRIALS);
        MatchState corner = MatchState.forSetPiece(MatchSide.HOME, inFlight.zone(), SetPieceKind.CORNER);
        MatchState defendersBall = MatchState.openPlay(MatchSide.AWAY, inFrontOfTheBox);

        assertThat(clearances).isNotEmpty().allSatisfy(outcome -> {
            Player defender = outcome.participants().primary().orElseThrow();
            assertThat(defender.role()).isIn(Role.DEFENDER, Role.MIDFIELDER);
            assertThat(away.players()).contains(defender);
            assertThat(outcome.participants().secondary()).contains(crosser);
            assertThat(outcome.next()).isIn(corner, defendersBall);
            assertThat(config.clearanceDuration().contains(outcome.durationSeconds())).isTrue();
        });
    }

    @Test
    void aClearanceGivesACornerToTheAttackersAtTheConfiguredShare() {
        MatchState inFlight = crossInFlight(MatchSide.HOME, home);
        MatchState corner = MatchState.forSetPiece(MatchSide.HOME, inFlight.zone(), SetPieceKind.CORNER);

        List<ActionOutcome> clearances = clearances(inFlight, MANY_TRIALS);

        long corners = clearances.stream().filter(outcome -> outcome.next().equals(corner)).count();
        assertThat((double) corners / clearances.size()).isCloseTo(config.clearedToCornerShare(), within(ONE_POINT));
    }

    @Test
    void aCrossInFlightFromACornerIsResolvedLikeAnyCross() {
        Zone box = layout.ownBoxZone(MatchSide.AWAY);
        Player taker = home.playersIn(Role.MIDFIELDER).get(0);
        MatchState fromCorner = MatchState.forSetPiece(MatchSide.HOME, box, SetPieceKind.CORNER)
                .withCrossInFlightFrom(taker);
        CrossResolver resolver = resolverWith(config);

        List<ActionOutcome> outcomes = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolveAerialDuel(fromCorner, Score.goalless(), home, away))
                .toList();

        long headers = outcomes.stream().filter(outcome -> SHOT_TYPES.contains(outcome.type())).count();
        assertThat((double) headers / TRIALS).isCloseTo(0.5, within(ONE_POINT));
        assertThat(outcomes).filteredOn(outcome -> outcome.type() == EventType.CLEARANCE)
                .allSatisfy(outcome -> assertThat(outcome.participants().secondary()).contains(taker));
    }

    @Test
    void theSameSeedGivesTheSameCrosses() {
        MatchState state = openPlayOnTheAttackingThird(MatchSide.HOME);

        List<ActionOutcome> first = resolveCrosses(resolverWith(config, SEED), state, 200);
        List<ActionOutcome> second = resolveCrosses(resolverWith(config, SEED), state, 200);

        assertThat(first).containsExactlyElementsOf(second);
    }

    @ParameterizedTest
    @CsvSource({
            "ATTACKING, BALANCED, HOME, 1",
            "BALANCED, DEFENSIVE, HOME, -1",
            "ATTACKING, DEFENSIVE, HOME, 1",
            "BALANCED, BALANCED, HOME, 1",
            "BALANCED, BALANCED, AWAY, -1",
            "DEFENSIVE, BALANCED, AWAY, -1",
            "ATTACKING, DEFENSIVE, AWAY, -1"})
    void styleAndHomeAdvantageMoveTheFrequencyOfCompletedCrossesInTheExpectedDirection(PlayStyle attackersStyle,
            PlayStyle defendersStyle, MatchSide attackingSide, int direction) {
        TeamOnPitch attackers = TeamOnPitch.startingWith(TestTeams.team("Attackers", attackersStyle, 60));
        TeamOnPitch defenders = TeamOnPitch.startingWith(TestTeams.team("Defenders", defendersStyle, 60));
        MatchState state = openPlayOnTheAttackingThird(attackingSide);
        CrossResolver resolver = resolverFor(withVenueAndStyles, SEED);
        Player crosser = attackers.playersIn(Role.MIDFIELDER).getFirst();
        Player defender = defenders.playersIn(Role.DEFENDER).getFirst();
        double expected = resolver.crossDuel(state, attackers, crosser, defenders, defender)
                .attackerWinProbability();

        List<ActionOutcome> outcomes = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolveCross(state, attackers, defenders))
                .toList();

        assertThat(Math.signum(expected - EVEN_DUEL)).isEqualTo((double) direction);
        assertThat(share(outcomes, EventType.CROSS_COMPLETED)).isCloseTo(expected, within(ONE_POINT));
    }

    @ParameterizedTest
    @CsvSource({
            "ATTACKING, BALANCED, HOME, 1",
            "BALANCED, DEFENSIVE, HOME, -1",
            "ATTACKING, DEFENSIVE, HOME, 1",
            "BALANCED, BALANCED, HOME, 1",
            "BALANCED, BALANCED, AWAY, -1",
            "DEFENSIVE, BALANCED, AWAY, -1",
            "ATTACKING, DEFENSIVE, AWAY, -1"})
    void styleAndHomeAdvantageMoveTheFrequencyOfWonAerialDuelsInTheExpectedDirection(PlayStyle attackersStyle,
            PlayStyle defendersStyle, MatchSide attackingSide, int direction) {
        TeamOnPitch attackers = TeamOnPitch.startingWith(TestTeams.team("Attackers", attackersStyle, 60));
        TeamOnPitch defenders = TeamOnPitch.startingWith(TestTeams.team("Defenders", defendersStyle, 60));
        MatchState inFlight = crossInFlight(attackingSide, attackers);
        CrossResolver resolver = resolverFor(withVenueAndStyles, SEED);
        Player header = attackers.playersIn(Role.FORWARD).getFirst();
        Player defender = defenders.playersIn(Role.DEFENDER).getFirst();
        double expected = resolver.aerialDuel(inFlight, attackers, header, defenders, defender)
                .attackerWinProbability();

        List<ActionOutcome> outcomes = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolveAerialDuel(inFlight, Score.goalless(), attackers, defenders))
                .toList();

        assertThat(Math.signum(expected - EVEN_DUEL)).isEqualTo((double) direction);
        assertThat(1.0 - share(outcomes, EventType.CLEARANCE)).isCloseTo(expected, within(ONE_POINT));
    }

    @Test
    void withBalancedStylesAndNoHomeAdvantageTheCrossAndTheAerialDuelAreThoseOfTheAttributes() {
        TeamOnPitch attackers = teamWith("Attackers", playerAttributes(70, 70));
        TeamOnPitch defenders = teamWith("Defenders", new PlayerAttributes(50, 50, 50, 50, 50, 50, 50, 50));
        CrossResolver resolver = resolverWith(config);
        MatchState state = openPlayOnTheAttackingThird(MatchSide.HOME);
        MatchState inFlight = crossInFlight(MatchSide.HOME, attackers);
        double crossExpected = 70.0 / (70.0 + 50.0);
        double aerialExpected = 70.0 / (70.0 + 50.0);

        List<ActionOutcome> crossOutcomes = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolveCross(state, attackers, defenders))
                .toList();
        List<ActionOutcome> aerialOutcomes = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolveAerialDuel(inFlight, Score.goalless(), attackers, defenders))
                .toList();

        assertThat(share(crossOutcomes, EventType.CROSS_COMPLETED)).isCloseTo(crossExpected, within(ONE_POINT));
        assertThat(1.0 - share(aerialOutcomes, EventType.CLEARANCE)).isCloseTo(aerialExpected, within(ONE_POINT));
    }

    @Test
    void aCrosserOutOfPositionCrossesWithTheOutOfPositionMultiplier() {
        TeamOnPitch defenders = teamWith("Defenders", new PlayerAttributes(50, 50, 50, 50, 50, 50, 50, 50));
        TeamOnPitch attackers = teamWith("Attackers", playerAttributes(80, 50));
        Player crosser = attackers.playersIn(Role.MIDFIELDER).getFirst();
        TeamOnPitch crosserMisplaced = attackers.withRole(crosser, Role.DEFENDER);
        MatchState state = openPlayOnTheAttackingThird(MatchSide.HOME);
        ZoneRole defendingRole = layout.roleOf(state.zone(), MatchSide.AWAY);
        double superiority = TestResolvers.zonePresence(engineConfig)
                .superiorityFactor(crosserMisplaced, ZoneRole.ATTACKING_THIRD, defenders, defendingRole);
        double multiplier = engineConfig.lineup().outOfPositionMultiplier();
        double attack = 80.0 * superiority * multiplier;
        double expected = attack / (attack + 50.0);
        CrossResolver resolver = resolverWith(config);
        Player defender = defenders.playersIn(Role.DEFENDER).getFirst();

        Duel duel = resolver.crossDuel(state, crosserMisplaced, crosser, defenders, defender);

        assertThat(duel.attackerWinProbability()).isCloseTo(expected, within(EXACT));
    }

    private double crossProbability(CrossResolver resolver, MatchState state, TeamOnPitch attackers, Player crosser,
            TeamOnPitch defenders) {
        Player defender = defenders.playersIn(Role.DEFENDER).getFirst();
        return resolver.crossDuel(state, attackers, crosser, defenders, defender).attackerWinProbability();
    }

    private ZonePresenceConfig onlyForwardsInTheOpponentBox() {
        ZonePresenceConfig defaults = engineConfig.zonePresence();
        Map<ZoneRole, Map<Role, Double>> table = new EnumMap<>(defaults.presenceByZoneRole());
        Map<Role, Double> row = new EnumMap<>(Role.class);
        row.put(Role.DEFENDER, 0.0);
        row.put(Role.MIDFIELDER, 0.0);
        row.put(Role.FORWARD, 1.0);
        table.put(ZoneRole.OPPONENT_BOX, row);
        return new ZonePresenceConfig(table, defaults.referenceFormation(), defaults.advantageFactorPerMan(),
                defaults.advantageCap(), defaults.representativeWeight(), defaults.passMinimumPlayers());
    }

    private CrossResolver resolverFor(EngineConfig engine, long seed) {
        MatchRandom random = new MatchRandom(seed);
        ShotResolver shotResolver = TestResolvers.shotResolver(engine, layout, random);
        return TestResolvers.crossResolver(engine, layout, random, shotResolver);
    }

    private List<ActionOutcome> crosses(MatchState state, TeamOnPitch attackers, TeamOnPitch defenders,
            EventType type) {
        CrossResolver resolver = resolverWith(config);
        return IntStream.range(0, 2_000)
                .mapToObj(index -> resolver.resolveCross(state, attackers, defenders))
                .filter(outcome -> outcome.type() == type)
                .toList();
    }

    private List<ActionOutcome> clearances(MatchState inFlight, int trials) {
        CrossResolver resolver = resolverWith(config);
        return IntStream.range(0, trials)
                .mapToObj(index -> resolver.resolveAerialDuel(inFlight, Score.goalless(), home, away))
                .filter(outcome -> outcome.type() == EventType.CLEARANCE)
                .toList();
    }

    private List<ActionOutcome> resolveCrosses(CrossResolver resolver, MatchState state, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> resolver.resolveCross(state, home, away))
                .toList();
    }

    private MatchState crossInFlight(MatchSide side, TeamOnPitch attackers) {
        Zone defendersBox = layout.ownBoxZone(side.opposite());
        Player crosser = attackers.playersIn(Role.MIDFIELDER).get(0);
        return MatchState.openPlay(side, defendersBox).withCrossInFlightFrom(crosser);
    }

    private MatchState openPlayOnTheAttackingThird(MatchSide side) {
        Zone zone = layout.zones().stream()
                .filter(candidate -> layout.roleOf(candidate, side) == ZoneRole.ATTACKING_THIRD)
                .findFirst()
                .orElseThrow();
        return MatchState.openPlay(side, zone);
    }

    private ShotKind shotKindOf(EventDetail detail) {
        return switch (detail) {
            case EventDetail.Shot shot -> shot.profile().kind();
            case EventDetail.Save save -> save.profile().kind();
            case EventDetail.Goal goal -> goal.profile().kind();
            default -> throw new AssertionError("not a shot detail: " + detail);
        };
    }

    private double share(List<ActionOutcome> outcomes, EventType type) {
        long matching = outcomes.stream().filter(outcome -> outcome.type() == type).count();
        return (double) matching / outcomes.size();
    }

    private TeamOnPitch teamOf(MatchSide side) {
        return side == MatchSide.HOME ? home : away;
    }

    private TeamOnPitch teamWith(String name, PlayerAttributes attributes) {
        List<Player> starters = TestTeams.squad(name, "S", attributes, 1, 4, 4, 2);
        return TeamOnPitch.startingWith(TestTeams.teamOf(name, starters));
    }

    private PlayerAttributes playerAttributes(int passing, int aerial) {
        return new PlayerAttributes(passing, 50, 50, 50, 50, 50, aerial, 50);
    }

    private PlayerAttributes defenderAttributes(int positioning, int tackling) {
        return new PlayerAttributes(50, 50, 50, tackling, positioning, 50, 50, 50);
    }

    private CrossResolver resolverWith(CrossConfig crossConfig) {
        return resolverWith(crossConfig, SEED);
    }

    private CrossResolver resolverWith(CrossConfig crossConfig, long seed) {
        EngineConfig custom = new EngineConfig(engineConfig.buildUpAndMidfieldDuration(),
                engineConfig.attackingThirdDuration(), engineConfig.dribbleProbability(),
                engineConfig.noTeammateDribbleThreshold(),
                engineConfig.stoppage(), engineConfig.shot(), engineConfig.setPiece(), crossConfig,
                engineConfig.counterAttack(), engineConfig.playStyles(), engineConfig.foul(), engineConfig.offside(), engineConfig.injury(),
                engineConfig.lineup(), engineConfig.zonePresence(),
                engineConfig.homeAdvantage());
        MatchRandom random = new MatchRandom(seed);
        ShotResolver shotResolver = TestResolvers.shotResolver(custom, layout, random);
        return TestResolvers.crossResolver(custom, layout, random, shotResolver);
    }
}
