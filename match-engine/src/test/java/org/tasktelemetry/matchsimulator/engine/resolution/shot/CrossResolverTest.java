package org.tasktelemetry.matchsimulator.engine.resolution.shot;

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
import org.tasktelemetry.matchsimulator.engine.TestResolvers;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.CrossConfig;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.ActionOutcome;
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
    private static final Set<EventType> SHOT_TYPES = EnumSet.of(EventType.GOAL, EventType.SHOT_SAVED,
            EventType.SHOT_OFF_TARGET, EventType.SHOT_BLOCKED);

    private final PitchLayout layout = PitchLayout.standard();
    private final EngineConfig engineConfig = EngineConfig.defaults();
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
    void thePassingOfTheCrosserDecidesTheProbabilityThatTheCrossReachesTheBox() {
        CrossResolver resolver = resolverWith(config);
        Player average = playerWith(50, 50);
        Player better = playerWith(80, 50);

        double base = resolver.crossDuel(average).attackerWinProbability();
        double raised = resolver.crossDuel(better).attackerWinProbability();

        assertThat(raised).isGreaterThan(base);
        assertThat(raised).isCloseTo(80.0 / (80.0 + config.crossDifficulty()), within(EXACT));
    }

    @ParameterizedTest
    @CsvSource({"90, 10", "30, 90"})
    void theFrequencyOfCompletedCrossesFollowsThePassingNotTheAerialOfTheCrosser(int passing, int aerial) {
        TeamOnPitch crossers = teamWith("Crossers", playerAttributes(passing, aerial));
        MatchState state = openPlayOnTheAttackingThird(MatchSide.HOME);
        CrossResolver resolver = resolverWith(config);
        double expected = passing / (passing + config.crossDifficulty());

        List<ActionOutcome> outcomes = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolveCross(state, crossers))
                .toList();

        assertThat(share(outcomes, EventType.CROSS_COMPLETED)).isCloseTo(expected, within(ONE_POINT));
        assertThat(outcomes).extracting(ActionOutcome::type)
                .containsOnly(EventType.CROSS_COMPLETED, EventType.CROSS_FAILED);
    }

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void aCompletedCrossPutsTheBallInTheBoxOfTheDefendersWithTheCrosserInFlight(MatchSide side) {
        MatchState state = openPlayOnTheAttackingThird(side);
        TeamOnPitch attackers = teamOf(side);
        Zone defendersBox = layout.ownBoxZone(side.opposite());

        List<ActionOutcome> completed = crosses(state, attackers, EventType.CROSS_COMPLETED);

        assertThat(completed).isNotEmpty().allSatisfy(outcome -> {
            Player crosser = outcome.participants().primary().orElseThrow();
            assertThat(crosser.role()).isEqualTo(Role.MIDFIELDER);
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

        List<ActionOutcome> failed = crosses(state, attackers, EventType.CROSS_FAILED);

        assertThat(failed).isNotEmpty().allSatisfy(outcome -> {
            assertThat(attackers.players()).contains(outcome.participants().primary().orElseThrow());
            assertThat(outcome.next()).isEqualTo(MatchState.openPlay(side.opposite(), defendersBox));
            assertThat(config.crossDuration().contains(outcome.durationSeconds())).isTrue();
        });
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
    void aWonAerialDuelIsAHeaderByAForwardOfTheAttackersWithTheCrosserNamedUnlessItIsASave() {
        MatchState inFlight = crossInFlight(MatchSide.HOME, home);
        Player crosser = inFlight.crosser();
        CrossResolver resolver = resolverWith(config);

        List<ActionOutcome> outcomes = IntStream.range(0, TRIALS)
                .mapToObj(index -> resolver.resolveAerialDuel(inFlight, Score.goalless(), home, away))
                .filter(outcome -> SHOT_TYPES.contains(outcome.type()))
                .toList();

        assertThat(outcomes).isNotEmpty().allSatisfy(outcome -> {
            Player header = outcome.participants().primary().orElseThrow();
            assertThat(header.role()).isEqualTo(Role.FORWARD);
            assertThat(header).isNotEqualTo(crosser);
            assertThat(shotKindOf(outcome.detail())).isEqualTo(ShotKind.HEADER);
        });
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
            assertThat(defender.role()).isEqualTo(Role.DEFENDER);
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

    private List<ActionOutcome> crosses(MatchState state, TeamOnPitch attackers, EventType type) {
        CrossResolver resolver = resolverWith(config);
        return IntStream.range(0, 2_000)
                .mapToObj(index -> resolver.resolveCross(state, attackers))
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
                .mapToObj(index -> resolver.resolveCross(state, home))
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

    private Player playerWith(int passing, int aerial) {
        return TestTeams.player("P", Role.MIDFIELDER, playerAttributes(passing, aerial));
    }

    private CrossResolver resolverWith(CrossConfig crossConfig) {
        return resolverWith(crossConfig, SEED);
    }

    private CrossResolver resolverWith(CrossConfig crossConfig, long seed) {
        EngineConfig custom = new EngineConfig(engineConfig.buildUpAndMidfieldDuration(),
                engineConfig.attackingThirdDuration(), engineConfig.dribbleProbability(),
                engineConfig.minimumStoppageMinutes(), engineConfig.shot(), engineConfig.setPiece(), crossConfig,
                engineConfig.counterAttack(), engineConfig.playStyles(), engineConfig.foul(), engineConfig.lineup(),
                engineConfig.homeAdvantage());
        MatchRandom random = new MatchRandom(seed);
        ShotResolver shotResolver = TestResolvers.shotResolver(custom, layout, random);
        return TestResolvers.crossResolver(custom, layout, random, shotResolver);
    }
}
