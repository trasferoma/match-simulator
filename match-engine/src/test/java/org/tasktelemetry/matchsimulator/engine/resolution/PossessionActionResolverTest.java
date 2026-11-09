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
    private static final Set<EventType> COMPLETED = EnumSet.of(EventType.PASS_COMPLETED, EventType.DRIBBLE_COMPLETED);
    private static final Set<EventType> TURNOVERS = EnumSet.of(EventType.INTERCEPTION, EventType.TACKLE);
    private static final Set<EventType> CROSS_TYPES = EnumSet.of(EventType.CROSS_COMPLETED,
            EventType.CROSS_FAILED);
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
    void inTheOpponentBoxACompletedActionIsAShotByAForwardOfTheAttackers(MatchSide side) {
        PitchLayout layout = PitchLayout.standard();
        Zone box = zoneWithRole(layout, side, ZoneRole.OPPONENT_BOX);
        MatchState state = new MatchState(side, box, PlayPhase.BUILD_UP);
        List<ActionOutcome> outcomes = resolveMany(resolverFor(EngineConfig.defaults(), layout, SEED), state, 500);

        List<ActionOutcome> shots = outcomes.stream()
                .filter(outcome -> SHOT_TYPES.contains(outcome.type()))
                .toList();

        assertThat(shots).isNotEmpty().allSatisfy(outcome ->
                assertThat(primaryPlayerOf(outcome).role()).isEqualTo(Role.FORWARD));
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
void theCrosserOfACrossIsAMidfielderOfTheAttackersAndNotTheForwardWhoWouldCarryTheBall() {
    PitchLayout layout = PitchLayout.standard();
    Zone attackingThird = zoneWithRole(layout, MatchSide.HOME, ZoneRole.ATTACKING_THIRD);
    MatchState state = new MatchState(MatchSide.HOME, attackingThird, PlayPhase.BUILD_UP);

    List<ActionOutcome> crosses = resolveMany(resolverFor(EngineConfig.defaults(), layout, SEED), state, 2_000)
            .stream()
            .filter(outcome -> CROSS_TYPES.contains(outcome.type()))
            .toList();

    assertThat(crosses).isNotEmpty().allSatisfy(outcome ->
            assertThat(primaryPlayerOf(outcome).role()).isEqualTo(Role.MIDFIELDER));
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
        return IntStream.range(0, count)
                .mapToObj(index -> resolver.resolve(state, Score.goalless(), attackers, defenders))
                .toList();
    }

    private Player primaryPlayerOf(ActionOutcome outcome) {
        return outcome.participants().primary().orElseThrow();
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
                dribbleProbability, defaults.stoppage(), defaults.shot(), defaults.setPiece(),
                defaults.cross(), defaults.counterAttack(), defaults.playStyles(), defaults.foul(), defaults.offside(), defaults.injury(),
                defaults.lineup(), defaults.homeAdvantage());
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
        return new EngineConfig(new DurationRange(4, 5), new DurationRange(20, 25), 0.3, defaults.stoppage(),
                defaults.shot(),
                defaults.setPiece(), defaults.cross(), defaults.counterAttack(), defaults.playStyles(), defaults.foul(), defaults.offside(), defaults.injury(),
                defaults.lineup(),
                defaults.homeAdvantage());
    }

    private DurationRange rangeFor(EngineConfig config, ZoneRole role) {
        boolean attackingThird = role == ZoneRole.ATTACKING_THIRD || role == ZoneRole.OPPONENT_BOX;
        return attackingThird ? config.attackingThirdDuration() : config.buildUpAndMidfieldDuration();
    }
}
