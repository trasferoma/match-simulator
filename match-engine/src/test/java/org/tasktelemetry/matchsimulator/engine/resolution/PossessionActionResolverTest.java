package org.tasktelemetry.matchsimulator.engine.resolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.TestConfigs;
import org.tasktelemetry.matchsimulator.engine.TestResolvers;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.DurationRange;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.GoalContext;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class PossessionActionResolverTest {

    private static final long SEED = 11L;
    private static final int TRIALS = 20_000;
    private static final double TOO_MANY_TEAMMATES_REQUIRED = 3.0;
    private static final Set<EventType> COMPLETED = EnumSet.of(EventType.PASS_COMPLETED, EventType.DRIBBLE_COMPLETED);
    private static final Set<EventType> TURNOVERS = EnumSet.of(EventType.INTERCEPTION, EventType.TACKLE);
    private static final Set<EventType> PASS_OUTCOMES = EnumSet.of(EventType.PASS_COMPLETED, EventType.INTERCEPTION);
    private static final Set<EventType> CROSS_TYPES = EnumSet.of(EventType.CROSS_COMPLETED,
            EventType.CROSS_FAILED, EventType.CROSS_DEFLECTED_TO_CORNER);
    private static final Set<EventType> SHOT_TYPES = EnumSet.of(EventType.GOAL, EventType.SHOT_SAVED,
            EventType.SHOT_OFF_TARGET, EventType.SHOT_BLOCKED);

    private final TeamOnPitch home = TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 50));
    private final TeamOnPitch away = TeamOnPitch.startingWith(TestTeams.uniformTeam("Away", 50));

    @ParameterizedTest
    @ValueSource(ints = {5, 7, 9})
    void everyOutcomeIsAValidStateWithADurationInsideTheConfiguredRange(int zoneCount) {
        EngineConfig config = distinctRangesConfig();
        PitchLayout layout = new PitchLayout(zoneCount);
        PossessionActionResolver resolver = resolverFor(config, layout, SEED);

        for (MatchSide side : MatchSide.values()) {
            for (Zone zone : layout.zones()) {
                MatchState state = new MatchState(side, zone, PlayPhase.BUILD_UP);
                ZoneRole role = layout.roleOf(zone, side);
                DurationRange range = rangeFor(config, role);
                List<ActionOutcome> outcomes = resolveMany(resolver, state, 300);

                assertThat(outcomes).allSatisfy(outcome -> {
                    assertThat(layout.zones()).contains(outcome.next().zone());
                    if (SHOT_TYPES.contains(outcome.type())) {
                        assertThat(shotRangeFor(config, outcome.type()).contains(outcome.durationSeconds()))
                                .isTrue();
                    } else if (outcome.type() == EventType.FOUL) {
                        assertThat(outcome.next().phase()).isEqualTo(PlayPhase.SET_PIECE);
                        assertThat(config.foul().foulDuration().contains(outcome.durationSeconds())).isTrue();
                    } else if (outcome.type() == EventType.CROSS_DEFLECTED_TO_CORNER) {
                        assertThat(outcome.next().phase()).isEqualTo(PlayPhase.SET_PIECE);
                        assertThat(config.cross().crossDuration().contains(outcome.durationSeconds())).isTrue();
                    } else if (CROSS_TYPES.contains(outcome.type())) {
                        assertThat(outcome.next().phase()).isEqualTo(PlayPhase.BUILD_UP);
                        assertThat(config.cross().crossDuration().contains(outcome.durationSeconds())).isTrue();
                    } else {
                        assertThat(outcome.next().phase()).isEqualTo(PlayPhase.BUILD_UP);
                        assertThat(range.contains(outcome.durationSeconds())).isTrue();
                    }
                });
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void aCompletedActionKeepsPossessionAndMovesOneZoneForward(MatchSide side) {
        PitchLayout layout = PitchLayout.standard();
        Zone midfield = zoneWithRole(layout, side, ZoneRole.MIDFIELD);
        MatchState state = new MatchState(side, midfield, PlayPhase.BUILD_UP);
        Zone expected = layout.advance(midfield, side).orElseThrow();
        List<ActionOutcome> completed = resolveMany(resolverFor(EngineConfig.defaults(), layout, SEED), state, 500)
                .stream()
                .filter(outcome -> COMPLETED.contains(outcome.type()))
                .toList();

        assertThat(completed).isNotEmpty().allSatisfy(outcome -> {
            assertThat(outcome.next().possession()).isEqualTo(side);
            assertThat(outcome.next().zone()).isEqualTo(expected);
            assertThat(teamOf(side).players()).contains(primaryPlayerOf(outcome));
        });
    }

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void aTurnoverPassesPossessionToTheOpponentInTheSameZone(MatchSide side) {
        PitchLayout layout = PitchLayout.standard();
        Zone midfield = zoneWithRole(layout, side, ZoneRole.MIDFIELD);
        MatchState state = new MatchState(side, midfield, PlayPhase.BUILD_UP);
        List<ActionOutcome> turnovers = resolveMany(resolverFor(EngineConfig.defaults(), layout, SEED), state, 500)
                .stream()
                .filter(outcome -> TURNOVERS.contains(outcome.type()))
                .toList();

        assertThat(turnovers).isNotEmpty().allSatisfy(outcome -> {
            assertThat(outcome.next().possession()).isEqualTo(side.opposite());
            assertThat(outcome.next().zone()).isEqualTo(midfield);
            assertThat(teamOf(side.opposite()).players()).contains(primaryPlayerOf(outcome));
        });
    }

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void inTheOpponentBoxACompletedActionIsAShotByAnOutfieldPlayerOfTheAttackers(MatchSide side) {
        PitchLayout layout = PitchLayout.standard();
        Zone box = zoneWithRole(layout, side, ZoneRole.OPPONENT_BOX);
        MatchState state = new MatchState(side, box, PlayPhase.BUILD_UP);
        List<ActionOutcome> outcomes = resolveMany(resolverFor(EngineConfig.defaults(), layout, SEED), state, 500);

        List<ActionOutcome> shots = outcomes.stream()
                .filter(outcome -> SHOT_TYPES.contains(outcome.type()))
                .toList();

        assertThat(shots).isNotEmpty().allSatisfy(outcome ->
                assertThat(primaryPlayerOf(outcome).role()).isNotEqualTo(Role.GOALKEEPER));
        assertThat(shots).extracting(outcome -> primaryPlayerOf(outcome).role()).contains(Role.FORWARD);
        assertThat(shots).extracting(outcome -> teamOf(side).players().contains(primaryPlayerOf(outcome)))
                .containsOnly(true);
        assertThat(outcomes).filteredOn(outcome -> COMPLETED.contains(outcome.type())).isEmpty();
    }

    @Test
    void inTheOpponentBoxHalfOfTheAttemptsEndInAShotWithEqualAttributes() {
        PitchLayout layout = PitchLayout.standard();
        Zone box = zoneWithRole(layout, MatchSide.HOME, ZoneRole.OPPONENT_BOX);
        MatchState state = new MatchState(MatchSide.HOME, box, PlayPhase.BUILD_UP);

        List<ActionOutcome> outcomes = resolveMany(resolverFor(EngineConfig.defaults(), layout, SEED), state, TRIALS);

        long shots = outcomes.stream().filter(outcome -> SHOT_TYPES.contains(outcome.type())).count();
        assertThat((double) shots / TRIALS).isCloseTo(0.5, within(0.01));
    }

    @Test
    void aShotFromTheOpponentBoxReceivesTheScoreForTheGoalContext() {
        PitchLayout layout = PitchLayout.standard();
        Zone box = zoneWithRole(layout, MatchSide.HOME, ZoneRole.OPPONENT_BOX);
        MatchState state = new MatchState(MatchSide.HOME, box, PlayPhase.BUILD_UP);
        PossessionActionResolver resolver = resolverFor(EngineConfig.defaults(), layout, SEED);

        List<ActionOutcome> goals = IntStream.range(0, 5_000)
                .mapToObj(index -> resolver.resolve(state, new Score(0, 1), home, away))
                .filter(outcome -> outcome.type() == EventType.GOAL)
                .toList();

        assertThat(goals).isNotEmpty().allSatisfy(outcome ->
                assertThat(outcome.detail()).isInstanceOfSatisfying(EventDetail.Goal.class, goal ->
                        assertThat(goal.context()).isEqualTo(GoalContext.EQUALISER)));
    }

@Test
void onTheAttackingThirdACrossIsChosenWithTheConfiguredProbabilityTimesTheStyleMultiplier() {
    PitchLayout layout = PitchLayout.standard();
    Zone attackingThird = zoneWithRole(layout, MatchSide.HOME, ZoneRole.ATTACKING_THIRD);
    MatchState state = new MatchState(MatchSide.HOME, attackingThird, PlayPhase.BUILD_UP);
    EngineConfig config = EngineConfig.defaults();
    double expected = config.cross().crossProbability()
            * config.playStyles().effectOf(PlayStyle.BALANCED).crossPropensity();

    List<ActionOutcome> outcomes = resolveMany(resolverFor(config, layout, SEED), state, TRIALS);

    long crosses = outcomes.stream().filter(outcome -> CROSS_TYPES.contains(outcome.type())).count();
    assertThat((double) crosses / TRIALS).isCloseTo(expected, within(0.01));
}

@Test
void theCrossIsChosenWithThePropensityOfTheStyleOfTheAttackersNotOfTheDefenders() {
    PitchLayout layout = PitchLayout.standard();
    Zone attackingThird = zoneWithRole(layout, MatchSide.HOME, ZoneRole.ATTACKING_THIRD);
    MatchState state = new MatchState(MatchSide.HOME, attackingThird, PlayPhase.BUILD_UP);
    TeamOnPitch attackers = TeamOnPitch.startingWith(TestTeams.team("Attackers", PlayStyle.ATTACKING, 50));
    TeamOnPitch defenders = TeamOnPitch.startingWith(TestTeams.team("Defenders", PlayStyle.POSSESSION, 50));
    EngineConfig config = EngineConfig.defaults();
    double expected = config.cross().crossProbability()
            * config.playStyles().effectOf(PlayStyle.ATTACKING).crossPropensity();
    PossessionActionResolver resolver = resolverFor(config, layout, SEED);

    long crosses = IntStream.range(0, TRIALS)
            .mapToObj(index -> resolver.resolve(state, Score.goalless(), attackers, defenders))
            .filter(outcome -> CROSS_TYPES.contains(outcome.type()))
            .count();

    assertThat((double) crosses / TRIALS).isCloseTo(expected, within(0.01));
}

@ParameterizedTest
@EnumSource(value = ZoneRole.class, names = "ATTACKING_THIRD", mode = EnumSource.Mode.EXCLUDE)
void outsideTheAttackingThirdNoCrossIsAttempted(ZoneRole role) {
    PitchLayout layout = PitchLayout.standard();
    MatchState state = new MatchState(MatchSide.HOME, zoneWithRole(layout, MatchSide.HOME, role),
            PlayPhase.BUILD_UP);

    List<ActionOutcome> outcomes = resolveMany(resolverFor(EngineConfig.defaults(), layout, SEED), state, 2_000);

    assertThat(outcomes).extracting(ActionOutcome::type).doesNotContainAnyElementsOf(CROSS_TYPES);
}

@Test
void theCrosserOfACrossIsAnOutfieldPlayerOfAnyLineOfTheAttackersAndNeverTheGoalkeeper() {
    PitchLayout layout = PitchLayout.standard();
    Zone attackingThird = zoneWithRole(layout, MatchSide.HOME, ZoneRole.ATTACKING_THIRD);
    MatchState state = new MatchState(MatchSide.HOME, attackingThird, PlayPhase.BUILD_UP);

    List<ActionOutcome> crosses = resolveMany(resolverFor(EngineConfig.defaults(), layout, SEED), state, 2_000)
            .stream()
            .filter(outcome -> CROSS_TYPES.contains(outcome.type()))
            .toList();

    assertThat(crosses).isNotEmpty().extracting(outcome -> crosserOf(outcome).role())
            .contains(Role.DEFENDER, Role.MIDFIELDER, Role.FORWARD)
            .doesNotContain(Role.GOALKEEPER);
}

@Test
void aDribbleIsChosenWithTheConfiguredProbability() {
        PitchLayout layout = PitchLayout.standard();
        MatchState state = new MatchState(MatchSide.HOME, layout.kickOffZone(), PlayPhase.BUILD_UP);
        EngineConfig config = TestConfigs.withoutFouls(EngineConfig.defaults());

        List<ActionOutcome> outcomes = resolveMany(resolverFor(config, layout, SEED), state, TRIALS);

        long dribbles = outcomes.stream().filter(this::isDribbleAttempt).count();
        assertThat((double) dribbles / TRIALS).isCloseTo(config.dribbleProbability(), within(0.01));
    }

    @Test
    void withEqualAttributesHalfOfTheAttemptsSucceed() {
        PitchLayout layout = PitchLayout.standard();
        MatchState state = new MatchState(MatchSide.HOME, layout.kickOffZone(), PlayPhase.BUILD_UP);

        List<ActionOutcome> outcomes = resolveMany(resolverFor(EngineConfig.defaults(), layout, SEED), state, TRIALS);

        long successes = outcomes.stream().filter(outcome -> COMPLETED.contains(outcome.type())).count();
        assertThat((double) successes / TRIALS).isCloseTo(0.5, within(0.01));
    }

    @Test
    void aDribbleAlwaysAttemptedProducesOnlyDribbleOutcomes() {
        EngineConfig onlyDribbles = TestConfigs.withoutFouls(withDribbleProbability(1.0));
        PitchLayout layout = PitchLayout.standard();
        MatchState state = new MatchState(MatchSide.HOME, layout.kickOffZone(), PlayPhase.BUILD_UP);

        List<ActionOutcome> outcomes = resolveMany(resolverFor(onlyDribbles, layout, SEED), state, 500);

        assertThat(outcomes).extracting(ActionOutcome::type)
                .containsOnly(EventType.DRIBBLE_COMPLETED, EventType.TACKLE);
    }

    @Test
    void aPassAlwaysAttemptedProducesOnlyPassOutcomes() {
        EngineConfig onlyPasses = TestConfigs.withoutFouls(withDribbleProbability(0.0));
        PitchLayout layout = PitchLayout.standard();
        MatchState state = new MatchState(MatchSide.HOME, layout.kickOffZone(), PlayPhase.BUILD_UP);

        List<ActionOutcome> outcomes = resolveMany(resolverFor(onlyPasses, layout, SEED), state, 500);

        assertThat(outcomes).extracting(ActionOutcome::type)
                .containsOnly(EventType.PASS_COMPLETED, EventType.INTERCEPTION);
    }

    @ParameterizedTest
    @CsvSource({"0.0, PASS_COMPLETED, 0.8", "1.0, DRIBBLE_COMPLETED, 0.3"})
    void theSuccessOfEachActionFollowsTheAttributesOfItsOwnDuel(double dribbleProbability, EventType success,
            double expected) {
        EngineConfig config = withDribbleProbability(dribbleProbability);
        PitchLayout layout = PitchLayout.standard();
        MatchState state = new MatchState(MatchSide.HOME, layout.kickOffZone(), PlayPhase.BUILD_UP);
        PlayerAttributes attributes = TestTeams.withDuelAttributes(80, 30, 70, 20);
        TeamOnPitch tuned = TeamOnPitch.startingWith(teamWith("Tuned", attributes));
        TeamOnPitch rival = TeamOnPitch.startingWith(teamWith("Rival", attributes));
        PossessionActionResolver resolver = resolverFor(config, layout, SEED);

        long successes = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolve(state, Score.goalless(), tuned, rival))
                .filter(outcome -> outcome.type() == success)
                .count();

        assertThat((double) successes / TRIALS).isCloseTo(expected, within(0.01));
    }

    @Test
    void theSameSeedGivesTheSameOutcomes() {
        PitchLayout layout = PitchLayout.standard();
        MatchState state = new MatchState(MatchSide.AWAY, layout.kickOffZone(), PlayPhase.BUILD_UP);

        List<ActionOutcome> first = resolveMany(resolverFor(EngineConfig.defaults(), layout, SEED), state, 200);
        List<ActionOutcome> second = resolveMany(resolverFor(EngineConfig.defaults(), layout, SEED), state, 200);

        assertThat(first).containsExactlyElementsOf(second);
    }

    @ParameterizedTest
    @CsvSource({"10, 90", "50, 50", "90, 10"})
    void noPassIsAttemptedTowardsASectorWithoutTeammates(int dribbling, int tackling) {
        PitchLayout layout = PitchLayout.standard();
        MatchState state = new MatchState(MatchSide.HOME, zoneWithRole(layout, MatchSide.HOME, ZoneRole.MIDFIELD),
                PlayPhase.BUILD_UP);
        TeamOnPitch attackers = teamWithoutForwards(dribblerAttributes(dribbling));
        TeamOnPitch defenders = tacklerTeam(tackling);

        List<ActionOutcome> outcomes = resolveMany(resolverFor(withTooFewTeammatesToPassTo(), layout, SEED), state,
                attackers, defenders, 2_000);

        assertThat(outcomes).extracting(ActionOutcome::type).doesNotContainAnyElementsOf(PASS_OUTCOMES);
    }

    @ParameterizedTest
    @CsvSource({"90, 10, 0.9", "50, 50, 0.5"})
    void withADribbleProbabilityAtLeastTheThresholdThePlayerDribblesInsteadOfPassing(int dribbling, int tackling,
            double expected) {
        PitchLayout layout = PitchLayout.standard();
        MatchState state = new MatchState(MatchSide.HOME, zoneWithRole(layout, MatchSide.HOME, ZoneRole.MIDFIELD),
                PlayPhase.BUILD_UP);
        TeamOnPitch attackers = teamWithoutForwards(dribblerAttributes(dribbling));
        TeamOnPitch defenders = withoutForwards(tacklerTeam(tackling));

        List<ActionOutcome> outcomes = resolveMany(resolverFor(withTooFewTeammatesToPassTo(), layout, SEED), state,
                attackers, defenders, TRIALS);

        assertThat(outcomes).extracting(ActionOutcome::type)
                .containsOnly(EventType.DRIBBLE_COMPLETED, EventType.TACKLE);
        long successes = outcomes.stream().filter(outcome -> outcome.type() == EventType.DRIBBLE_COMPLETED).count();
        assertThat((double) successes / TRIALS).isCloseTo(expected, within(0.01));
    }

    @ParameterizedTest
    @CsvSource({"10, 90", "49, 51"})
    void withADribbleProbabilityBelowTheThresholdTheTeamCirculatesTheBallLeavingTheStateUnchanged(int dribbling,
            int tackling) {
        PitchLayout layout = PitchLayout.standard();
        Zone midfield = zoneWithRole(layout, MatchSide.HOME, ZoneRole.MIDFIELD);
        MatchState state = new MatchState(MatchSide.HOME, midfield, PlayPhase.BUILD_UP);
        TeamOnPitch attackers = teamWithoutForwards(dribblerAttributes(dribbling));
        TeamOnPitch defenders = tacklerTeam(tackling);
        EngineConfig config = withTooFewTeammatesToPassTo();

        List<ActionOutcome> outcomes = resolveMany(resolverFor(config, layout, SEED), state, attackers, defenders,
                500);

        assertThat(outcomes).allSatisfy(outcome -> {
            assertThat(outcome.type()).isEqualTo(EventType.BALL_CIRCULATED);
            assertThat(outcome.next()).isEqualTo(state);
            assertThat(attackers.players()).contains(primaryPlayerOf(outcome));
            assertThat(config.buildUpAndMidfieldDuration().contains(outcome.durationSeconds())).isTrue();
        });
    }

    @Test
    void theBallCirculationKeepsTheLastPasserAndTakesTheDurationOfTheAttackingThird() {
        PitchLayout layout = PitchLayout.standard();
        Zone attackingThird = zoneWithRole(layout, MatchSide.HOME, ZoneRole.ATTACKING_THIRD);
        TeamOnPitch attackers = teamWithLoneForward(dribblerAttributes(10));
        TeamOnPitch defenders = tacklerTeam(90);
        Player passer = attackers.playersIn(Role.MIDFIELDER).getFirst();
        MatchState state = new MatchState(MatchSide.HOME, attackingThird, PlayPhase.BUILD_UP).withLastPasser(passer);
        EngineConfig config = TestConfigs.withPassMinimumPlayers(TestConfigs.withoutFouls(distinctRangesConfig()),
                TOO_MANY_TEAMMATES_REQUIRED);

        List<ActionOutcome> circulations = resolveMany(resolverFor(config, layout, SEED), state, attackers,
                defenders, 2_000).stream()
                .filter(outcome -> outcome.type() == EventType.BALL_CIRCULATED)
                .toList();

        assertThat(circulations).isNotEmpty().allSatisfy(outcome -> {
            assertThat(outcome.next()).isEqualTo(state);
            assertThat(outcome.next().lastPasser()).isEqualTo(passer);
            assertThat(config.attackingThirdDuration().contains(outcome.durationSeconds())).isTrue();
            assertThat(attackers.players()).contains(primaryPlayerOf(outcome));
        });
    }

    @Test
    void withFewerMenInTheZoneAheadThanTheMinimumNoPassIsAttempted() {
        PitchLayout layout = PitchLayout.standard();
        Zone attackingThird = zoneWithRole(layout, MatchSide.HOME, ZoneRole.ATTACKING_THIRD);
        MatchState state = new MatchState(MatchSide.HOME, attackingThird, PlayPhase.BUILD_UP);
        TeamOnPitch attackers = teamWithLoneForward(dribblerAttributes(10));

        List<ActionOutcome> outcomes = resolveMany(resolverFor(withTooFewTeammatesToPassTo(), layout, SEED), state,
                attackers, tacklerTeam(90), 2_000);

        assertThat(outcomes).extracting(ActionOutcome::type).doesNotContainAnyElementsOf(PASS_OUTCOMES);
        assertThat(outcomes).extracting(ActionOutcome::type).contains(EventType.BALL_CIRCULATED);
    }

    @Test
    void aFourFourZeroInTheOpponentBoxTriesThePassWithTheDefaultMinimumButNotWithOnePointFive() {
        PitchLayout layout = PitchLayout.standard();
        Zone attackingThird = zoneWithRole(layout, MatchSide.HOME, ZoneRole.ATTACKING_THIRD);
        MatchState state = new MatchState(MatchSide.HOME, attackingThird, PlayPhase.BUILD_UP);
        TeamOnPitch attackers = teamWithoutForwards(dribblerAttributes(10));
        EngineConfig defaultMinimum = onlyPassChosenByTheDraw();
        EngineConfig onePointFive = TestConfigs.withPassMinimumPlayers(onlyPassChosenByTheDraw(), 1.5);

        List<ActionOutcome> withDefault = resolveMany(resolverFor(defaultMinimum, layout, SEED), state, attackers,
                tacklerTeam(90), 2_000);
        List<ActionOutcome> withOnePointFive = resolveMany(resolverFor(onePointFive, layout, SEED), state,
                attackers, tacklerTeam(90), 2_000);

        assertThat(withDefault).extracting(ActionOutcome::type).contains(EventType.PASS_COMPLETED);
        assertThat(withDefault).extracting(ActionOutcome::type).doesNotContain(EventType.BALL_CIRCULATED);
        assertThat(withOnePointFive).extracting(ActionOutcome::type)
                .doesNotContainAnyElementsOf(PASS_OUTCOMES);
        assertThat(withOnePointFive).extracting(ActionOutcome::type).contains(EventType.BALL_CIRCULATED);
    }

    @Test
    void aTeamWithExactlyTheMinimumOfMenInTheZoneAheadStillTriesThePass() {
        PitchLayout layout = PitchLayout.standard();
        MatchState state = new MatchState(MatchSide.HOME, zoneWithRole(layout, MatchSide.HOME, ZoneRole.MIDFIELD),
                PlayPhase.BUILD_UP);
        TeamOnPitch attackers = keeperAndMidfieldersOnly(dribblerAttributes(10));
        EngineConfig exactlyTheMen = TestConfigs.withPassMinimumPlayers(onlyPassChosenByTheDraw(), 1.5);
        EngineConfig oneManMore = TestConfigs.withPassMinimumPlayers(onlyPassChosenByTheDraw(), 1.6);

        List<ActionOutcome> atTheMinimum = resolveMany(resolverFor(exactlyTheMen, layout, SEED), state, attackers,
                tacklerTeam(90), 500);
        List<ActionOutcome> belowTheMinimum = resolveMany(resolverFor(oneManMore, layout, SEED), state, attackers,
                tacklerTeam(90), 500);

        assertThat(atTheMinimum).extracting(ActionOutcome::type)
                .containsOnly(EventType.PASS_COMPLETED, EventType.INTERCEPTION);
        assertThat(belowTheMinimum).extracting(ActionOutcome::type).containsOnly(EventType.BALL_CIRCULATED);
    }

    @Test
    void inTheOpponentBoxTheRuleIsNotAppliedAndTheCompletedPassStaysTheShotOfWhoHasTheBall() {
        PitchLayout layout = PitchLayout.standard();
        Zone box = zoneWithRole(layout, MatchSide.HOME, ZoneRole.OPPONENT_BOX);
        MatchState state = new MatchState(MatchSide.HOME, box, PlayPhase.BUILD_UP);
        TeamOnPitch attackers = teamWithLoneForward(dribblerAttributes(10));

        List<ActionOutcome> outcomes = resolveMany(resolverFor(onlyPassChosenByTheDraw(), layout, SEED), state,
                attackers, tacklerTeam(90), 2_000);

        assertThat(outcomes).extracting(ActionOutcome::type).doesNotContain(EventType.BALL_CIRCULATED);
        assertThat(outcomes).extracting(ActionOutcome::type).containsAnyElementsOf(SHOT_TYPES);
    }

    @Test
    void withATeammateInTheSectorAheadThePassIsAttemptedAsBefore() {
        PitchLayout layout = PitchLayout.standard();
        MatchState state = new MatchState(MatchSide.HOME, zoneWithRole(layout, MatchSide.HOME, ZoneRole.MIDFIELD),
                PlayPhase.BUILD_UP);
        TeamOnPitch attackers = TeamOnPitch.startingWith(teamWith("Full", dribblerAttributes(10)));

        List<ActionOutcome> outcomes = resolveMany(resolverFor(onlyPassChosenByTheDraw(), layout, SEED), state,
                attackers, tacklerTeam(90), 2_000);

        assertThat(outcomes).extracting(ActionOutcome::type)
                .containsOnly(EventType.PASS_COMPLETED, EventType.INTERCEPTION);
    }

    @Test
    void theDribbleThresholdIsTheOneOfTheConfiguration() {
        PitchLayout layout = PitchLayout.standard();
        MatchState state = new MatchState(MatchSide.HOME, zoneWithRole(layout, MatchSide.HOME, ZoneRole.MIDFIELD),
                PlayPhase.BUILD_UP);
        TeamOnPitch attackers = teamWithoutForwards(dribblerAttributes(90));
        TeamOnPitch defenders = tacklerTeam(10);
        EngineConfig demanding = TestConfigs.withNoTeammateDribbleThreshold(withTooFewTeammatesToPassTo(), 0.95);
        EngineConfig lenient = TestConfigs.withNoTeammateDribbleThreshold(withTooFewTeammatesToPassTo(), 0.85);

        List<ActionOutcome> underDemanding = resolveMany(resolverFor(demanding, layout, SEED), state, attackers,
                defenders, 500);
        List<ActionOutcome> underLenient = resolveMany(resolverFor(lenient, layout, SEED), state, attackers,
                defenders, 500);

        assertThat(underDemanding).extracting(ActionOutcome::type).containsOnly(EventType.BALL_CIRCULATED);
        assertThat(underLenient).extracting(ActionOutcome::type)
                .containsOnly(EventType.DRIBBLE_COMPLETED, EventType.TACKLE);
    }

    @Test
    void aCompletedPassRemembersItsPasserInTheNextState() {
        PitchLayout layout = PitchLayout.standard();
        MatchState state = new MatchState(MatchSide.HOME, layout.kickOffZone(), PlayPhase.BUILD_UP);

        List<ActionOutcome> passes = resolveMany(resolverFor(onlyPassChosenByTheDraw(), layout, SEED), state, 500)
                .stream()
                .filter(outcome -> outcome.type() == EventType.PASS_COMPLETED)
                .toList();

        assertThat(passes).isNotEmpty().allSatisfy(outcome ->
                assertThat(outcome.next().lastPasser()).isEqualTo(primaryPlayerOf(outcome)));
    }

    @Test
    void aCompletedDribbleKeepsTheLastPasserOfThePreviousState() {
        PitchLayout layout = PitchLayout.standard();
        Player passer = home.playersIn(Role.MIDFIELDER).getFirst();
        MatchState state = new MatchState(MatchSide.HOME, layout.kickOffZone(), PlayPhase.BUILD_UP)
                .withLastPasser(passer);
        EngineConfig onlyDribbles = TestConfigs.withoutFouls(withDribbleProbability(1.0));

        List<ActionOutcome> dribbles = resolveMany(resolverFor(onlyDribbles, layout, SEED), state, 500).stream()
                .filter(outcome -> outcome.type() == EventType.DRIBBLE_COMPLETED)
                .toList();

        assertThat(dribbles).isNotEmpty().allSatisfy(outcome ->
                assertThat(outcome.next().lastPasser()).isEqualTo(passer));
    }

    @Test
    void aTurnoverForgetsTheLastPasser() {
        PitchLayout layout = PitchLayout.standard();
        Player passer = home.playersIn(Role.MIDFIELDER).getFirst();
        MatchState state = new MatchState(MatchSide.HOME, layout.kickOffZone(), PlayPhase.BUILD_UP)
                .withLastPasser(passer);

        List<ActionOutcome> turnovers = resolveMany(resolverFor(EngineConfig.defaults(), layout, SEED), state, 500)
                .stream()
                .filter(outcome -> TURNOVERS.contains(outcome.type()))
                .toList();

        assertThat(turnovers).isNotEmpty().allSatisfy(outcome ->
                assertThat(outcome.next().hasLastPasser()).isFalse());
    }

    @Test
    void aCarrierOutOfPositionWeighsLessInTheDribbleThresholdAndTheTeamCirculatesTheBall() {
        PitchLayout layout = PitchLayout.standard();
        MatchState state = new MatchState(MatchSide.HOME, zoneWithRole(layout, MatchSide.HOME, ZoneRole.MIDFIELD),
                PlayPhase.BUILD_UP);
        TeamOnPitch inPosition = teamWithoutForwards(dribblerAttributes(50));
        TeamOnPitch outOfPosition = swapMidfieldersAndDefenders(inPosition);
        TeamOnPitch defenders = withoutForwards(tacklerTeam(50));
        PossessionActionResolver resolver = resolverFor(withTooFewTeammatesToPassTo(), layout, SEED);

        List<ActionOutcome> asInPosition = resolveMany(resolver, state, inPosition, defenders, 300);
        List<ActionOutcome> asOutOfPosition = resolveMany(resolver, state, outOfPosition, defenders, 300);

        assertThat(asInPosition).extracting(ActionOutcome::type)
                .containsOnly(EventType.DRIBBLE_COMPLETED, EventType.TACKLE);
        assertThat(asOutOfPosition).extracting(ActionOutcome::type).containsOnly(EventType.BALL_CIRCULATED);
    }

    @Test
    void aMidfielderLessInTheZoneOfTheCarrierWeighsLessInTheDribbleThresholdAndTheTeamCirculatesTheBall() {
        PitchLayout layout = PitchLayout.standard();
        MatchState state = new MatchState(MatchSide.HOME, zoneWithRole(layout, MatchSide.HOME, ZoneRole.MIDFIELD),
                PlayPhase.BUILD_UP);
        TeamOnPitch complete = teamWithoutForwards(dribblerAttributes(50));
        Player missing = complete.playersIn(Role.MIDFIELDER).getFirst();
        TeamOnPitch oneMidfielderDown = complete.withoutPlayer(missing);
        TeamOnPitch defenders = withoutForwards(tacklerTeam(50));
        PossessionActionResolver resolver = resolverFor(withTooFewTeammatesToPassTo(), layout, SEED);

        List<ActionOutcome> withAll = resolveMany(resolver, state, complete, defenders, 300);
        List<ActionOutcome> withOneDown = resolveMany(resolver, state, oneMidfielderDown, defenders, 300);

        assertThat(withAll).extracting(ActionOutcome::type)
                .containsOnly(EventType.DRIBBLE_COMPLETED, EventType.TACKLE);
        assertThat(withOneDown).extracting(ActionOutcome::type).containsOnly(EventType.BALL_CIRCULATED);
    }

    @Test
    void aMidfielderLessInTheZoneOfTheOpponentWeighsInTheDribbleThresholdInFavourOfTheCarrier() {
        PitchLayout layout = PitchLayout.standard();
        MatchState state = new MatchState(MatchSide.HOME, zoneWithRole(layout, MatchSide.HOME, ZoneRole.MIDFIELD),
                PlayPhase.BUILD_UP);
        TeamOnPitch attackers = teamWithoutForwards(dribblerAttributes(47));
        TeamOnPitch completeDefenders = withoutForwards(tacklerTeam(50));
        Player missing = completeDefenders.playersIn(Role.MIDFIELDER).getFirst();
        TeamOnPitch defendersOneDown = completeDefenders.withoutPlayer(missing);
        PossessionActionResolver resolver = resolverFor(withTooFewTeammatesToPassTo(), layout, SEED);

        List<ActionOutcome> againstAll = resolveMany(resolver, state, attackers, completeDefenders, 300);
        List<ActionOutcome> againstOneDown = resolveMany(resolver, state, attackers, defendersOneDown, 300);

        assertThat(againstAll).extracting(ActionOutcome::type).containsOnly(EventType.BALL_CIRCULATED);
        assertThat(againstOneDown).extracting(ActionOutcome::type)
                .containsOnly(EventType.DRIBBLE_COMPLETED, EventType.TACKLE);
    }

    private TeamOnPitch swapMidfieldersAndDefenders(TeamOnPitch team) {
        TeamOnPitch swapped = team;
        for (Player midfielder : team.playersIn(Role.MIDFIELDER)) {
            swapped = swapped.withRole(midfielder, Role.DEFENDER);
        }
        for (Player defender : team.playersIn(Role.DEFENDER)) {
            swapped = swapped.withRole(defender, Role.MIDFIELDER);
        }
        return swapped;
    }

    private EngineConfig withTooFewTeammatesToPassTo() {
        return TestConfigs.withPassMinimumPlayers(onlyPassChosenByTheDraw(), TOO_MANY_TEAMMATES_REQUIRED);
    }

    private EngineConfig onlyPassChosenByTheDraw() {
        return TestConfigs.withoutFouls(withDribbleProbability(0.0));
    }

    private PlayerAttributes dribblerAttributes(int dribbling) {
        return TestTeams.withDuelAttributes(50, dribbling, 50, 50);
    }

    private TeamOnPitch tacklerTeam(int tackling) {
        PlayerAttributes tacklers = TestTeams.withDuelAttributes(50, 50, tackling, 50);
        return TeamOnPitch.startingWith(teamWith("Rival", tacklers));
    }

    private TeamOnPitch teamWithoutForwards(PlayerAttributes attributes) {
        return withoutForwards(TeamOnPitch.startingWith(teamWith("Short", attributes)));
    }

    private TeamOnPitch withoutForwards(TeamOnPitch complete) {
        List<Player> withoutForwards = complete.players().stream()
                .filter(player -> complete.roleOf(player) != Role.FORWARD)
                .toList();
        return new TeamOnPitch(complete.team(), withoutForwards);
    }

    private TeamOnPitch keeperAndMidfieldersOnly(PlayerAttributes attributes) {
        TeamOnPitch complete = TeamOnPitch.startingWith(teamWith("Midfield", attributes));
        List<Player> keeperAndMidfielders = complete.players().stream()
                .filter(player -> complete.roleOf(player) != Role.DEFENDER)
                .filter(player -> complete.roleOf(player) != Role.FORWARD)
                .toList();
        return new TeamOnPitch(complete.team(), keeperAndMidfielders);
    }

    private TeamOnPitch teamWithLoneForward(PlayerAttributes attributes) {
        List<Player> starters = TestTeams.squad("Lone", "S", attributes, 1, 4, 5, 1);
        List<Player> bench = TestTeams.squad("Lone", "B", attributes, 1, 2, 2, 2);
        return TeamOnPitch.startingWith(new Team("Lone", PlayStyle.BALANCED, starters, bench));
    }

    private Team teamWith(String name, PlayerAttributes attributes) {
        List<Player> starters = TestTeams.squad(name, "S", attributes, 1, 4, 4, 2);
        List<Player> bench = TestTeams.squad(name, "B", attributes, 1, 2, 2, 2);
        return new Team(name, PlayStyle.BALANCED, starters, bench);
    }

    private boolean isDribbleAttempt(ActionOutcome outcome) {
        return outcome.type() == EventType.DRIBBLE_COMPLETED || outcome.type() == EventType.TACKLE;
    }

    private List<ActionOutcome> resolveMany(PossessionActionResolver resolver, MatchState state, int count) {
        TeamOnPitch attackers = teamOf(state.possession());
        TeamOnPitch defenders = teamOf(state.possession().opposite());
        return resolveMany(resolver, state, attackers, defenders, count);
    }

    private List<ActionOutcome> resolveMany(PossessionActionResolver resolver, MatchState state,
            TeamOnPitch attackers, TeamOnPitch defenders, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> resolver.resolve(state, Score.goalless(), attackers, defenders))
                .toList();
    }

    private Player primaryPlayerOf(ActionOutcome outcome) {
        return outcome.participants().primary().orElseThrow();
    }

    private Player crosserOf(ActionOutcome outcome) {
        if (outcome.type() == EventType.CROSS_DEFLECTED_TO_CORNER) {
            return outcome.participants().secondary().orElseThrow();
        }
        return primaryPlayerOf(outcome);
    }

    private DurationRange shotRangeFor(EngineConfig config, EventType type) {
        if (type == EventType.GOAL) {
            return config.shot().goalWithRestartDuration();
        }
        return config.shot().shotDuration();
    }

    private EngineConfig withDribbleProbability(double dribbleProbability) {
        EngineConfig defaults = EngineConfig.defaults();
        return new EngineConfig(defaults.buildUpAndMidfieldDuration(), defaults.attackingThirdDuration(),
                dribbleProbability, defaults.noTeammateDribbleThreshold(), defaults.stoppage(), defaults.shot(),
                defaults.setPiece(),
                defaults.cross(), defaults.counterAttack(), defaults.playStyles(), defaults.foul(), defaults.offside(), defaults.injury(),
                defaults.lineup(), defaults.zonePresence(), defaults.homeAdvantage());
    }

    private TeamOnPitch teamOf(MatchSide side) {
        return side == MatchSide.HOME ? home : away;
    }

    private PossessionActionResolver resolverFor(EngineConfig config, PitchLayout layout, long seed) {
        MatchRandom random = new MatchRandom(seed);
        EngineConfig withoutVenueEffect = TestConfigs.withoutHomeAdvantage(config);
        return TestResolvers.possessionResolver(withoutVenueEffect, layout, random);
    }

    private Zone zoneWithRole(PitchLayout layout, MatchSide side, ZoneRole role) {
        return layout.zones().stream()
                .filter(zone -> layout.roleOf(zone, side) == role)
                .findFirst()
                .orElseThrow();
    }

    private EngineConfig distinctRangesConfig() {
        EngineConfig defaults = EngineConfig.defaults();
        return new EngineConfig(new DurationRange(4, 5), new DurationRange(20, 25), 0.3, 0.5,
                defaults.stoppage(),
                defaults.shot(),
                defaults.setPiece(), defaults.cross(), defaults.counterAttack(), defaults.playStyles(), defaults.foul(), defaults.offside(), defaults.injury(),
                defaults.lineup(), defaults.zonePresence(),
                defaults.homeAdvantage());
    }

    private DurationRange rangeFor(EngineConfig config, ZoneRole role) {
        boolean attackingThird = role == ZoneRole.ATTACKING_THIRD || role == ZoneRole.OPPONENT_BOX;
        return attackingThird ? config.attackingThirdDuration() : config.buildUpAndMidfieldDuration();
    }
}
