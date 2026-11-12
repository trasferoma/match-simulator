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
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.TestConfigs;
import org.tasktelemetry.matchsimulator.engine.TestResolvers;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ShotResolver;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class CounterAttackResolverTest {

    private static final long SEED = 23L;
    private static final int TRIALS = 20_000;
    private static final double ONE_POINT = 0.01;
    private static final double EXACT = 1e-9;
    private static final Set<EventType> SHOT_TYPES = EnumSet.of(EventType.GOAL, EventType.SHOT_SAVED,
            EventType.SHOT_OFF_TARGET, EventType.SHOT_BLOCKED);

    private final PitchLayout layout = PitchLayout.standard();
    private final EngineConfig config = EngineConfig.defaults();
    private final EngineConfig neutralVenue = TestConfigs.withoutHomeAdvantage(config);
    private final TeamOnPitch home = TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 50));
    private final TeamOnPitch away = TeamOnPitch.startingWith(TestTeams.uniformTeam("Away", 50));

    @ParameterizedTest
    @CsvSource({
            "BALANCED, DEFENCE, 0.08",
            "BALANCED, MIDFIELD, 0.08",
            "COUNTER_ATTACK, DEFENCE, 0.112",
            "COUNTER_ATTACK, MIDFIELD, 0.112",
            "POSSESSION, DEFENCE, 0.056",
            "POSSESSION, MIDFIELD, 0.056",
            "ATTACKING, DEFENCE, 0.08",
            "DEFENSIVE, MIDFIELD, 0.08",
            "COUNTER_ATTACK, OWN_BOX, 0.0",
            "COUNTER_ATTACK, ATTACKING_THIRD, 0.0",
            "BALANCED, OPPONENT_BOX, 0.0"})
    void theStartProbabilityIsTheConfiguredOneTimesTheStyleOfTheRecoverersOnlyFromDefenceAndMidfield(
            PlayStyle style, ZoneRole recoveryZoneRole, double expected) {
        CounterAttackResolver resolver = resolverWith(config);

        double probability = resolver.startProbability(recoveryZoneRole, style);

        assertThat(probability).isCloseTo(expected, within(EXACT));
    }

    @ParameterizedTest
    @CsvSource({"BALANCED, 0.08", "COUNTER_ATTACK, 0.112", "POSSESSION, 0.056"})
    void aRecoveryInDefenceOpensACounterAttackWithTheStartProbabilityOfTheStyleOfTheRecoverers(PlayStyle style,
            double expected) {
        CounterAttackResolver resolver = resolverWith(config);
        TeamOnPitch recoverers = TeamOnPitch.startingWith(TestTeams.team("Recoverers", style, 50));
        ActionOutcome recovery = recoveryAt(MatchSide.AWAY, ZoneRole.DEFENCE, EventType.INTERCEPTION, recoverers);

        List<ActionOutcome> outcomes = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.openCounterAttackIfBallRecovered(recovery, recoverers))
                .toList();

        long counterAttacks = outcomes.stream()
                .filter(outcome -> outcome.next().phase() == PlayPhase.COUNTER_ATTACK)
                .count();
        assertThat((double) counterAttacks / TRIALS).isCloseTo(expected, within(ONE_POINT));
    }

    @Test
    void anOpenedCounterAttackChangesOnlyThePhaseOfTheRecovery() {
        CounterAttackResolver resolver = resolverWith(config);
        TeamOnPitch recoverers = TeamOnPitch.startingWith(TestTeams.team("Recoverers", PlayStyle.COUNTER_ATTACK, 50));
        ActionOutcome recovery = recoveryAt(MatchSide.AWAY, ZoneRole.MIDFIELD, EventType.TACKLE, recoverers);

        ActionOutcome opened = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.openCounterAttackIfBallRecovered(recovery, recoverers))
                .filter(outcome -> outcome.next().phase() == PlayPhase.COUNTER_ATTACK)
                .findFirst()
                .orElseThrow();

        assertThat(opened.type()).isEqualTo(recovery.type());
        assertThat(opened.participants()).isEqualTo(recovery.participants());
        assertThat(opened.durationSeconds()).isEqualTo(recovery.durationSeconds());
        assertThat(opened.detail()).isEqualTo(recovery.detail());
        assertThat(opened.next()).isEqualTo(recovery.next().inCounterAttack());
    }

    @ParameterizedTest
    @EnumSource(value = ZoneRole.class, names = {"OWN_BOX", "ATTACKING_THIRD", "OPPONENT_BOX"})
    void aRecoveryOutsideDefenceAndMidfieldNeverOpensACounterAttack(ZoneRole recoveryZoneRole) {
        CounterAttackResolver resolver = resolverWith(config);
        TeamOnPitch recoverers = TeamOnPitch.startingWith(TestTeams.team("Recoverers", PlayStyle.COUNTER_ATTACK, 50));
        ActionOutcome recovery = recoveryAt(MatchSide.AWAY, recoveryZoneRole, EventType.INTERCEPTION, recoverers);

        List<ActionOutcome> outcomes = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.openCounterAttackIfBallRecovered(recovery, recoverers))
                .toList();

        assertThat(outcomes).containsOnly(recovery);
    }

    @ParameterizedTest
    @EnumSource(EventType.class)
    void onlyAnInterceptionOrATackleCanOpenACounterAttack(EventType type) {
        CounterAttackResolver resolver = resolverWith(config);
        TeamOnPitch recoverers = TeamOnPitch.startingWith(TestTeams.team("Recoverers", PlayStyle.COUNTER_ATTACK, 50));
        ActionOutcome outcome = recoveryAt(MatchSide.AWAY, ZoneRole.MIDFIELD, type, recoverers);

        List<ActionOutcome> results = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.openCounterAttackIfBallRecovered(outcome, recoverers))
                .toList();

        boolean canOpen = type == EventType.INTERCEPTION || type == EventType.TACKLE;
        assertThat(type.isBallRecovery()).isEqualTo(canOpen);
        assertThat(results.stream().anyMatch(result -> result.next().phase() == PlayPhase.COUNTER_ATTACK))
                .isEqualTo(canOpen);
    }

    @Test
    void aSuccessfulRunMovesOneZoneForwardKeepingTheCounterAttackWithAShortDuration() {
        CounterAttackResolver resolver = resolverWith(neutralVenue);
        MatchState state = counterAttackAt(MatchSide.HOME, ZoneRole.MIDFIELD);
        Zone expected = layout.advance(state.zone(), MatchSide.HOME).orElseThrow();

        List<ActionOutcome> runs = resolveMany(resolver, state, 2_000).stream()
                .filter(outcome -> outcome.type() == EventType.DRIBBLE_COMPLETED)
                .toList();

        assertThat(runs).isNotEmpty().allSatisfy(outcome -> {
            assertThat(outcome.next()).isEqualTo(new MatchState(MatchSide.HOME, expected, PlayPhase.COUNTER_ATTACK));
            assertThat(config.counterAttack().advanceDuration().contains(outcome.durationSeconds())).isTrue();
            assertThat(home.players()).contains(outcome.participants().primary().orElseThrow());
        });
    }

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void aLostRunGivesTheBallToTheChasersInTheSameZoneBackInBuildUp(MatchSide side) {
        CounterAttackResolver resolver = resolverWith(neutralVenue);
        MatchState state = counterAttackAt(side, ZoneRole.MIDFIELD);
        TeamOnPitch attackers = side == MatchSide.HOME ? home : away;
        TeamOnPitch defenders = side == MatchSide.HOME ? away : home;

        List<ActionOutcome> lost = IntStream.range(0, 2_000)
                .mapToObj(index -> resolver.resolve(state, Score.goalless(), attackers, defenders))
                .filter(outcome -> outcome.type() == EventType.TACKLE)
                .toList();

        assertThat(lost).isNotEmpty().allSatisfy(outcome -> {
            assertThat(outcome.next()).isEqualTo(MatchState.openPlay(side.opposite(), state.zone()));
            assertThat(defenders.players()).contains(outcome.participants().primary().orElseThrow());
            assertThat(config.counterAttack().advanceDuration().contains(outcome.durationSeconds())).isTrue();
        });
    }

    @Test
    void inTheOpponentBoxARunEndsInAShotByAFieldPlayerOrIsLost() {
        CounterAttackResolver resolver = resolverWith(neutralVenue);
        MatchState state = counterAttackAt(MatchSide.HOME, ZoneRole.OPPONENT_BOX);

        List<ActionOutcome> outcomes = resolveMany(resolver, state, 2_000);

        List<ActionOutcome> shots = outcomes.stream().filter(outcome -> SHOT_TYPES.contains(outcome.type())).toList();
        assertThat(outcomes).extracting(ActionOutcome::type).doesNotContain(EventType.DRIBBLE_COMPLETED);
        assertThat(shots).isNotEmpty().allSatisfy(outcome -> {
            assertThat(home.players()).contains(outcome.participants().primary().orElseThrow());
            assertThat(outcome.participants().primary().orElseThrow().role()).isNotEqualTo(Role.GOALKEEPER);
        });
        assertThat(shots).extracting(outcome -> outcome.participants().primary().orElseThrow().role())
                .contains(Role.FORWARD);
        assertThat(outcomes).extracting(ActionOutcome::type)
                .allMatch(type -> SHOT_TYPES.contains(type) || type == EventType.TACKLE);
    }

    @ParameterizedTest
    @CsvSource({"BALANCED, 0.5", "COUNTER_ATTACK, 0.5238"})
    void theSuccessOfARunFollowsThePaceAndTheAdvanceMultiplierOfTheStyleOfTheRunners(PlayStyle style,
            double expected) {
        CounterAttackResolver resolver = resolverWith(neutralVenue);
        MatchState state = counterAttackAt(MatchSide.HOME, ZoneRole.MIDFIELD);
        TeamOnPitch runners = TeamOnPitch.startingWith(TestTeams.team("Runners", style, 50));

        List<ActionOutcome> outcomes = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolve(state, Score.goalless(), runners, away))
                .toList();

        assertThat(share(outcomes, EventType.DRIBBLE_COMPLETED)).isCloseTo(expected, within(ONE_POINT));
    }

    @Test
    void theSuccessOfARunFollowsThePaceAndNotTheOtherAttributes() {
        CounterAttackResolver resolver = resolverWith(neutralVenue);
        MatchState state = counterAttackAt(MatchSide.HOME, ZoneRole.MIDFIELD);
        TeamOnPitch runners = teamWith("Runners", new PlayerAttributes(20, 20, 20, 20, 20, 80, 20, 20));
        TeamOnPitch chasers = teamWith("Chasers", new PlayerAttributes(90, 90, 90, 90, 90, 40, 90, 90));

        List<ActionOutcome> outcomes = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolve(state, Score.goalless(), runners, chasers))
                .toList();

        assertThat(share(outcomes, EventType.DRIBBLE_COMPLETED)).isCloseTo(80.0 / 120.0, within(ONE_POINT));
    }

    @Test
    void theSuccessOfARunIgnoresTheMenOfTheTeamsInTheZone() {
        CounterAttackResolver resolver = resolverWith(neutralVenue);
        MatchState state = counterAttackAt(MatchSide.HOME, ZoneRole.MIDFIELD);
        PlayerAttributes fast = new PlayerAttributes(50, 50, 50, 50, 50, 80, 50, 50);
        PlayerAttributes slow = new PlayerAttributes(50, 50, 50, 50, 50, 40, 50, 50);
        TeamOnPitch runners = TeamOnPitch.startingWith(TestTeams.teamOf("Runners",
                TestTeams.squad("Runners", "S", fast, 1, 4, 2, 4)));
        TeamOnPitch chasers = TeamOnPitch.startingWith(TestTeams.teamOf("Chasers",
                TestTeams.squad("Chasers", "S", slow, 1, 1, 5, 4)));

        List<ActionOutcome> outcomes = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolve(state, Score.goalless(), runners, chasers))
                .toList();

        assertThat(share(outcomes, EventType.DRIBBLE_COMPLETED)).isCloseTo(80.0 / 120.0, within(ONE_POINT));
    }

    @Test
    void theHomeTeamRunsWithTheHomeAdvantageAndTheAwayTeamAgainstIt() {
        CounterAttackResolver resolver = resolverWith(config);
        MatchState homeRuns = counterAttackAt(MatchSide.HOME, ZoneRole.MIDFIELD);
        MatchState awayRuns = counterAttackAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        double homeAdvantage = config.homeAdvantage();

        List<ActionOutcome> byHome = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolve(homeRuns, Score.goalless(), home, away))
                .toList();
        List<ActionOutcome> byAway = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolve(awayRuns, Score.goalless(), away, home))
                .toList();

        assertThat(share(byHome, EventType.DRIBBLE_COMPLETED))
                .isCloseTo(homeAdvantage / (homeAdvantage + 1.0), within(ONE_POINT));
        assertThat(share(byAway, EventType.DRIBBLE_COMPLETED))
                .isCloseTo(1.0 / (1.0 + homeAdvantage), within(ONE_POINT));
    }

    @Test
    void theSameSeedGivesTheSameRuns() {
        MatchState state = counterAttackAt(MatchSide.HOME, ZoneRole.DEFENCE);

        List<ActionOutcome> first = resolveMany(resolverWith(config, SEED), state, 200);
        List<ActionOutcome> second = resolveMany(resolverWith(config, SEED), state, 200);

        assertThat(first).containsExactlyElementsOf(second);
    }

    private ActionOutcome recoveryAt(MatchSide possessionBeforeLoss, ZoneRole recoveryZoneRole, EventType type,
            TeamOnPitch recoverers) {
        MatchSide recoveringSide = possessionBeforeLoss.opposite();
        Zone zone = layout.zones().stream()
                .filter(candidate -> layout.roleOf(candidate, recoveringSide) == recoveryZoneRole)
                .findFirst()
                .orElseThrow();
        Player recoverer = recoverers.players().get(1);
        MatchState next = MatchState.openPlay(recoveringSide, zone);
        return ActionOutcome.byPlayer(type, recoverer, recoveringSide, next, 10);
    }

    private MatchState counterAttackAt(MatchSide side, ZoneRole zoneRole) {
        Zone zone = layout.zones().stream()
                .filter(candidate -> layout.roleOf(candidate, side) == zoneRole)
                .findFirst()
                .orElseThrow();
        return new MatchState(side, zone, PlayPhase.COUNTER_ATTACK);
    }

    private List<ActionOutcome> resolveMany(CounterAttackResolver resolver, MatchState state, int count) {
        TeamOnPitch attackers = state.possession() == MatchSide.HOME ? home : away;
        TeamOnPitch defenders = state.possession() == MatchSide.HOME ? away : home;
        return IntStream.range(0, count)
                .mapToObj(index -> resolver.resolve(state, Score.goalless(), attackers, defenders))
                .toList();
    }

    private double share(List<ActionOutcome> outcomes, EventType type) {
        long matching = outcomes.stream().filter(outcome -> outcome.type() == type).count();
        return (double) matching / outcomes.size();
    }

    private TeamOnPitch teamWith(String name, PlayerAttributes attributes) {
        List<Player> starters = TestTeams.squad(name, "S", attributes, 1, 4, 4, 2);
        return TeamOnPitch.startingWith(TestTeams.teamOf(name, starters));
    }

    private CounterAttackResolver resolverWith(EngineConfig engineConfig) {
        return resolverWith(engineConfig, SEED);
    }

    private CounterAttackResolver resolverWith(EngineConfig engineConfig, long seed) {
        MatchRandom random = new MatchRandom(seed);
        ShotResolver shotResolver = TestResolvers.shotResolver(engineConfig, layout, random);
        return TestResolvers.counterAttackResolver(engineConfig, layout, random, shotResolver);
    }
}
