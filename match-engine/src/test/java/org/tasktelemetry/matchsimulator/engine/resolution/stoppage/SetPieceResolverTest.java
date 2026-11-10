package org.tasktelemetry.matchsimulator.engine.resolution.stoppage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.TestResolvers;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.SetPieceConfig;
import org.tasktelemetry.matchsimulator.engine.config.ShotConfig;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.event.ShotProfile;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.resolution.ActionOutcome;
import org.tasktelemetry.matchsimulator.engine.resolution.PlayerPicker;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.CrossResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ShotResolver;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.state.SetPieceKind;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class SetPieceResolverTest {

    private static final long SEED = 5L;
    private static final Score SCORE = Score.goalless();
    private static final Set<EventType> SHOT_TYPES = EnumSet.of(EventType.GOAL, EventType.SHOT_SAVED,
            EventType.SHOT_OFF_TARGET, EventType.SHOT_BLOCKED);

    private final PitchLayout layout = PitchLayout.standard();
    private final SetPieceConfig config = SetPieceConfig.defaults();
    private final TeamOnPitch away = TeamOnPitch.startingWith(TestTeams.uniformTeam("Away", 60));
    private final TeamOnPitch home = TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 60));

    @Test
    void aGoalKickIsTakenByTheGoalkeeperOfTheDefendingSideFromItsOwnBox() {
        Zone ownBox = layout.ownBoxZone(MatchSide.AWAY);
        MatchState state = MatchState.forSetPiece(MatchSide.AWAY, ownBox, SetPieceKind.GOAL_KICK);

        ActionOutcome outcome = resolver(SEED).resolve(state, SCORE, away, home);

        assertThat(outcome.type()).isEqualTo(EventType.GOAL_KICK);
        assertThat(outcome.participants()).isEqualTo(Participants.of(away.goalkeeper()));
        assertThat(outcome.detail()).isEqualTo(EventDetail.none());
    }

    @Test
    void afterAGoalKickPlayRestartsInOpenPlayFromTheSameZoneWithTheSameSide() {
        Zone ownBox = layout.ownBoxZone(MatchSide.AWAY);
        MatchState state = MatchState.forSetPiece(MatchSide.AWAY, ownBox, SetPieceKind.GOAL_KICK);

        ActionOutcome outcome = resolver(SEED).resolve(state, SCORE, away, home);

        assertThat(outcome.next()).isEqualTo(MatchState.openPlay(MatchSide.AWAY, ownBox));
    }

    @Test
    void aGoalKickLastsWithinTheConfiguredRangeAndUsesTheWholeRange() {
        Zone ownBox = layout.ownBoxZone(MatchSide.AWAY);
        MatchState state = MatchState.forSetPiece(MatchSide.AWAY, ownBox, SetPieceKind.GOAL_KICK);
        SetPieceResolver resolver = resolver(SEED);

        List<ActionOutcome> outcomes = IntStream.range(0, 2_000)
                .mapToObj(index -> resolver.resolve(state, SCORE, away, home))
                .toList();

        assertThat(outcomes).extracting(ActionOutcome::durationSeconds)
                .allMatch(seconds -> config.goalKickDuration().contains(seconds))
                .contains(config.goalKickDuration().minSeconds(), config.goalKickDuration().maxSeconds());
    }

    @Test
    void aKickOffAfterAGoalIsAnEventWithoutPlayersAndWithoutDuration() {
        MatchState state = MatchState.forSetPiece(MatchSide.AWAY, layout.kickOffZone(), SetPieceKind.KICK_OFF);

        ActionOutcome outcome = resolver(SEED).resolve(state, SCORE, away, home);

        assertThat(outcome.type()).isEqualTo(EventType.KICK_OFF_AFTER_GOAL);
        assertThat(outcome.participants()).isEqualTo(Participants.nobody());
        assertThat(outcome.durationSeconds()).isZero();
        assertThat(outcome.detail()).isEqualTo(EventDetail.none());
    }

    @Test
    void afterTheKickOffPlayRestartsInOpenPlayFromTheCentreWithTheSideThatConceded() {
        MatchState state = MatchState.forSetPiece(MatchSide.AWAY, layout.kickOffZone(), SetPieceKind.KICK_OFF);

        ActionOutcome outcome = resolver(SEED).resolve(state, SCORE, away, home);

        assertThat(outcome.next()).isEqualTo(MatchState.openPlay(MatchSide.AWAY, layout.kickOffZone()));
    }

    @Test
    void theSameSeedGivesTheSameGoalKicks() {
        Zone ownBox = layout.ownBoxZone(MatchSide.AWAY);
        MatchState state = MatchState.forSetPiece(MatchSide.AWAY, ownBox, SetPieceKind.GOAL_KICK);

        List<Integer> first = durations(resolver(SEED), state, 100);
        List<Integer> second = durations(resolver(SEED), state, 100);

        assertThat(first).containsExactlyElementsOf(second);
    }

@Test
void aCornerIsTakenByAnOutfieldPlayerOfAnyLineOfTheAttackingSideFromTheOpponentBox() {
    MatchState state = cornerFor(MatchSide.AWAY);

    List<ActionOutcome> outcomes = resolveMany(state, 300);

    assertThat(outcomes).allSatisfy(outcome -> {
        Player taker = outcome.participants().primary().orElseThrow();
        assertThat(outcome.type()).isEqualTo(EventType.CORNER_KICK);
        assertThat(taker.role()).isNotEqualTo(Role.GOALKEEPER);
        assertThat(away.players()).contains(taker);
        assertThat(outcome.participants().secondary()).isEmpty();
        assertThat(outcome.detail()).isEqualTo(EventDetail.none());
    });
    assertThat(outcomes).extracting(outcome -> outcome.participants().primary().orElseThrow().role())
            .contains(Role.DEFENDER, Role.MIDFIELDER, Role.FORWARD);
}

@Test
void aCornerStaysInTheSetPieceAndPutsTheCrossInFlightFromItsTaker() {
    MatchState state = cornerFor(MatchSide.AWAY);

    ActionOutcome outcome = resolver(SEED).resolve(state, SCORE, away, home);

    Player taker = outcome.participants().primary().orElseThrow();
    assertThat(outcome.next().possession()).isEqualTo(MatchSide.AWAY);
    assertThat(outcome.next().zone()).isEqualTo(state.zone());
    assertThat(outcome.next().phase()).isEqualTo(PlayPhase.SET_PIECE);
    assertThat(outcome.next().hasCrossInFlight()).isTrue();
    assertThat(outcome.next().crosser()).isEqualTo(taker);
}

@Test
void aCornerLastsWithinTheConfiguredRangeAndUsesTheWholeRange() {
    MatchState state = cornerFor(MatchSide.AWAY);

    List<ActionOutcome> outcomes = resolveMany(state, 2_000);

    assertThat(outcomes).extracting(ActionOutcome::durationSeconds)
            .allMatch(seconds -> config.cornerDuration().contains(seconds))
            .contains(config.cornerDuration().minSeconds(), config.cornerDuration().maxSeconds());
}

private MatchState cornerFor(MatchSide side) {
    Zone opponentBox = layout.ownBoxZone(side.opposite());
    return MatchState.forSetPiece(side, opponentBox, SetPieceKind.CORNER);
}

private List<ActionOutcome> resolveMany(MatchState state, int count) {
    SetPieceResolver resolver = resolver(SEED);
    return IntStream.range(0, count)
            .mapToObj(index -> resolver.resolve(state, SCORE, away, home))
            .toList();
}

private List<Integer> durations(SetPieceResolver resolver, MatchState state, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> resolver.resolve(state, SCORE, away, home).durationSeconds())
                .toList();
    }

    @ParameterizedTest
    @EnumSource(value = ZoneRole.class, names = {"OWN_BOX", "DEFENCE", "MIDFIELD"})
    void aFreeKickOutsideTheAttackingThirdIsATakenRestartWithTheSameSideFromTheSameZone(ZoneRole zoneRole) {
        MatchState state = freeKickAt(zoneRole);

        List<ActionOutcome> outcomes = resolveMany(state, 300);

        assertThat(outcomes).allSatisfy(outcome -> {
            Player taker = outcome.participants().primary().orElseThrow();
            assertThat(outcome.type()).isEqualTo(EventType.FREE_KICK);
            assertThat(away.players()).contains(taker);
            assertThat(taker.role()).isEqualTo(Role.MIDFIELDER);
            assertThat(outcome.next()).isEqualTo(MatchState.openPlay(MatchSide.AWAY, state.zone()));
            assertThat(outcome.detail()).isEqualTo(EventDetail.none());
        });
    }

    @Test
    void aFreeKickLastsWithinTheConfiguredRangeAndUsesTheWholeRange() {
        MatchState state = freeKickAt(ZoneRole.MIDFIELD);

        List<ActionOutcome> outcomes = resolveMany(state, 2_000);

        assertThat(outcomes).extracting(ActionOutcome::durationSeconds)
                .allMatch(seconds -> config.freeKickDuration().contains(seconds))
                .contains(config.freeKickDuration().minSeconds(), config.freeKickDuration().maxSeconds());
    }

    @Test
    void aFreeKickFromTheAttackingThirdIsADirectShotOrACrossInTheConfiguredShares() {
        MatchState state = freeKickAt(ZoneRole.ATTACKING_THIRD);

        List<ActionOutcome> outcomes = resolveMany(state, 20_000);

        long crosses = outcomes.stream().filter(outcome -> outcome.type() == EventType.FREE_KICK_CROSS).count();
        long shots = outcomes.stream().filter(outcome -> SHOT_TYPES.contains(outcome.type())).count();
        assertThat(crosses + shots).isEqualTo(outcomes.size());
        assertThat((double) shots / outcomes.size()).isCloseTo(config.directFreeKickShare(), within(0.01));
    }

    @Test
    void aDirectFreeKickIsAShotOfTheDirectFreeKickKindInTheSetPiecePhase() {
        SetPieceConfig alwaysDirect = withDirectShare(1.0);
        MatchState state = freeKickAt(ZoneRole.ATTACKING_THIRD);

        List<ActionOutcome> outcomes = resolveMany(resolverWith(alwaysDirect), state, 500);

        assertThat(outcomes).allSatisfy(outcome -> {
            assertThat(SHOT_TYPES).contains(outcome.type());
            assertThat(profileOf(outcome).kind()).isEqualTo(ShotKind.DIRECT_FREE_KICK);
            assertThat(profileOf(outcome).penalty()).isFalse();
        });
    }

    @Test
    void aDirectFreeKickSpendsTheFreeKickDurationOnTopOfTheDurationOfTheShot() {
        SetPieceConfig alwaysDirect = withDirectShare(1.0);
        MatchState state = freeKickAt(ZoneRole.ATTACKING_THIRD);
        int minimumWait = config.freeKickDuration().minSeconds();
        int maximumWait = config.freeKickDuration().maxSeconds();
        ShotConfig shotConfig = ShotConfig.defaults();

        List<ActionOutcome> outcomes = resolveMany(resolverWith(alwaysDirect), state, 2_000);

        List<ActionOutcome> misses = outcomes.stream().filter(outcome -> outcome.type() != EventType.GOAL).toList();
        assertThat(outcomes).allSatisfy(outcome ->
                assertThat(outcome.durationSeconds()).isGreaterThanOrEqualTo(minimumWait + 1));
        assertThat(misses).isNotEmpty().allSatisfy(outcome ->
                assertThat(outcome.durationSeconds()).isBetween(minimumWait + shotConfig.shotDuration().minSeconds(),
                        maximumWait + shotConfig.shotDuration().maxSeconds()));
        assertThat(misses).extracting(ActionOutcome::durationSeconds)
                .contains(minimumWait + shotConfig.shotDuration().minSeconds());
    }

    @Test
    void aFreeKickCrossPutsTheCrossInFlightIntoTheOpponentBoxFromACrosserOfAnyLine() {
        SetPieceConfig alwaysCross = withDirectShare(0.0);
        MatchState state = freeKickAt(ZoneRole.ATTACKING_THIRD);
        Zone opponentBox = layout.ownBoxZone(MatchSide.HOME);

        List<ActionOutcome> outcomes = resolveMany(resolverWith(alwaysCross), state, 300);

        assertThat(outcomes).allSatisfy(outcome -> {
            Player taker = outcome.participants().primary().orElseThrow();
            assertThat(outcome.type()).isEqualTo(EventType.FREE_KICK_CROSS);
            assertThat(taker.role()).isNotEqualTo(Role.GOALKEEPER);
            assertThat(outcome.next().possession()).isEqualTo(MatchSide.AWAY);
            assertThat(outcome.next().zone()).isEqualTo(opponentBox);
            assertThat(outcome.next().phase()).isEqualTo(PlayPhase.SET_PIECE);
            assertThat(outcome.next().setPiece()).isEqualTo(SetPieceKind.FREE_KICK);
            assertThat(outcome.next().crosser()).isEqualTo(taker);
            assertThat(config.freeKickDuration().contains(outcome.durationSeconds())).isTrue();
        });
    }

    @Test
    void aPenaltyIsTakenByTheBestShooterOfTheFouledSideAndIsNeverBlocked() {
        Player bestShooter = away.players().get(5);
        TeamOnPitch withAStar = replacePlayer(away, bestShooter, withShooting(bestShooter, 95));
        Zone opponentBox = layout.ownBoxZone(MatchSide.HOME);
        MatchState state = MatchState.forSetPiece(MatchSide.AWAY, opponentBox, SetPieceKind.PENALTY);
        SetPieceResolver resolver = resolver(SEED);

        List<ActionOutcome> outcomes = IntStream.range(0, 2_000)
                .mapToObj(index -> resolver.resolve(state, SCORE, withAStar, home))
                .toList();

        assertThat(outcomes).allSatisfy(outcome -> {
            assertThat(profileOf(outcome).penalty()).isTrue();
            assertThat(outcome.participants().primary()).contains(withAStar.players().get(5));
        });
        assertThat(outcomes).extracting(ActionOutcome::type)
                .doesNotContain(EventType.SHOT_BLOCKED)
                .contains(EventType.GOAL, EventType.SHOT_SAVED, EventType.SHOT_OFF_TARGET);
    }

    @Test
    void aPenaltyScoredRestartsWithTheKickOffOfTheSideThatConceded() {
        Zone opponentBox = layout.ownBoxZone(MatchSide.HOME);
        MatchState state = MatchState.forSetPiece(MatchSide.AWAY, opponentBox, SetPieceKind.PENALTY);

        List<ActionOutcome> goals = resolveMany(state, 500).stream()
                .filter(outcome -> outcome.type() == EventType.GOAL)
                .toList();

        assertThat(goals).isNotEmpty().allSatisfy(goal -> assertThat(goal.next())
                .isEqualTo(MatchState.forSetPiece(MatchSide.HOME, layout.kickOffZone(), SetPieceKind.KICK_OFF)));
    }

    private MatchState freeKickAt(ZoneRole zoneRole) {
        Zone zone = layout.zones().stream()
                .filter(candidate -> layout.roleOf(candidate, MatchSide.AWAY) == zoneRole)
                .findFirst()
                .orElseThrow();
        return MatchState.forSetPiece(MatchSide.AWAY, zone, SetPieceKind.FREE_KICK);
    }

    private SetPieceConfig withDirectShare(double share) {
        return new SetPieceConfig(config.goalKickDuration(), config.cornerDuration(), config.freeKickDuration(),
                share);
    }

    private ShotProfile profileOf(ActionOutcome outcome) {
        return switch (outcome.detail()) {
            case EventDetail.Goal goal -> goal.profile();
            case EventDetail.Save save -> save.profile();
            case EventDetail.Shot shot -> shot.profile();
            default -> throw new IllegalStateException("not a shot detail: " + outcome.detail());
        };
    }

    private Player withShooting(Player player, int shooting) {
        PlayerAttributes attributes = TestTeams.withShootingAndGoalkeeping(shooting, 50);
        return TestTeams.player(player.name(), player.role(), attributes);
    }

    private TeamOnPitch replacePlayer(TeamOnPitch team, Player replaced, Player replacement) {
        List<Player> players = team.players().stream()
                .map(player -> player.equals(replaced) ? replacement : player)
                .toList();
        return new TeamOnPitch(team.team(), players);
    }

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void anOffsideFreeKickIsTakenByAMidfielderOfTheDefendingSideInTheSetPiecePhase(MatchSide defendingSide) {
        Zone defence = layout.defenceZone(defendingSide);
        MatchState state = MatchState.forSetPiece(defendingSide, defence, SetPieceKind.OFFSIDE_FREE_KICK);
        TeamOnPitch restarting = teamOf(defendingSide);

        ActionOutcome outcome = resolver(SEED).resolve(state, SCORE, restarting, teamOf(defendingSide.opposite()));

        assertThat(outcome.type()).isEqualTo(EventType.OFFSIDE_FREE_KICK);
        Player taker = outcome.participants().primary().orElseThrow();
        assertThat(taker.role()).isEqualTo(Role.MIDFIELDER);
        assertThat(restarting.players()).contains(taker);
        assertThat(outcome.detail()).isEqualTo(EventDetail.none());
    }

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void afterAnOffsideFreeKickPlayRestartsInOpenPlayFromTheDefenceZoneWithTheSameSide(MatchSide defendingSide) {
        Zone defence = layout.defenceZone(defendingSide);
        MatchState state = MatchState.forSetPiece(defendingSide, defence, SetPieceKind.OFFSIDE_FREE_KICK);

        ActionOutcome outcome = resolver(SEED).resolve(state, SCORE, teamOf(defendingSide),
                teamOf(defendingSide.opposite()));

        assertThat(outcome.next()).isEqualTo(MatchState.openPlay(defendingSide, defence));
    }

    @Test
    void anOffsideFreeKickLastsWithinTheFreeKickRangeAndUsesTheWholeRange() {
        Zone defence = layout.defenceZone(MatchSide.AWAY);
        MatchState state = MatchState.forSetPiece(MatchSide.AWAY, defence, SetPieceKind.OFFSIDE_FREE_KICK);
        SetPieceResolver resolver = resolver(SEED);

        List<Integer> durations = resolveMany(resolver, state, 2_000).stream()
                .map(ActionOutcome::durationSeconds)
                .toList();

        assertThat(durations).allMatch(config.freeKickDuration()::contains);
        assertThat(durations).contains(config.freeKickDuration().minSeconds(), config.freeKickDuration().maxSeconds());
    }

    private TeamOnPitch teamOf(MatchSide side) {
        return side == MatchSide.HOME ? home : away;
    }

    private List<ActionOutcome> resolveMany(SetPieceResolver resolver, MatchState state, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> resolver.resolve(state, SCORE, away, home))
                .toList();
    }

    private SetPieceResolver resolverWith(SetPieceConfig setPieceConfig) {
        MatchRandom random = new MatchRandom(SEED);
        EngineConfig engineConfig = EngineConfig.defaults();
        ShotResolver shotResolver = TestResolvers.shotResolver(engineConfig, layout, random);
        CrossResolver crossResolver = TestResolvers.crossResolver(engineConfig, layout, random, shotResolver);
        return new SetPieceResolver(setPieceConfig, layout, random, new PlayerPicker(random), crossResolver,
                shotResolver);
    }

    private SetPieceResolver resolver(long seed) {
        MatchRandom random = new MatchRandom(seed);
        EngineConfig engineConfig = EngineConfig.defaults();
        ShotResolver shotResolver = TestResolvers.shotResolver(engineConfig, layout, random);
        CrossResolver crossResolver = TestResolvers.crossResolver(engineConfig, layout, random, shotResolver);
        return new SetPieceResolver(config, layout, random, new PlayerPicker(random), crossResolver, shotResolver);
    }
}
