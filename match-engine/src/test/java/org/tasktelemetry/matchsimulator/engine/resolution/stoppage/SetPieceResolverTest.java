package org.tasktelemetry.matchsimulator.engine.resolution.stoppage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.TestResolvers;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.SetPieceConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.resolution.ActionOutcome;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.CrossResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ShotResolver;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.state.SetPieceKind;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class SetPieceResolverTest {

    private static final long SEED = 5L;

    private final PitchLayout layout = PitchLayout.standard();
    private final SetPieceConfig config = SetPieceConfig.defaults();
    private final TeamOnPitch away = TeamOnPitch.startingWith(TestTeams.uniformTeam("Away", 60));

    @Test
    void aGoalKickIsTakenByTheGoalkeeperOfTheDefendingSideFromItsOwnBox() {
        Zone ownBox = layout.ownBoxZone(MatchSide.AWAY);
        MatchState state = MatchState.forSetPiece(MatchSide.AWAY, ownBox, SetPieceKind.GOAL_KICK);

        ActionOutcome outcome = resolver(SEED).resolve(state, away);

        assertThat(outcome.type()).isEqualTo(EventType.GOAL_KICK);
        assertThat(outcome.participants()).isEqualTo(Participants.of(away.goalkeeper()));
        assertThat(outcome.detail()).isEqualTo(EventDetail.none());
    }

    @Test
    void afterAGoalKickPlayRestartsInOpenPlayFromTheSameZoneWithTheSameSide() {
        Zone ownBox = layout.ownBoxZone(MatchSide.AWAY);
        MatchState state = MatchState.forSetPiece(MatchSide.AWAY, ownBox, SetPieceKind.GOAL_KICK);

        ActionOutcome outcome = resolver(SEED).resolve(state, away);

        assertThat(outcome.next()).isEqualTo(MatchState.openPlay(MatchSide.AWAY, ownBox));
    }

    @Test
    void aGoalKickLastsWithinTheConfiguredRangeAndUsesTheWholeRange() {
        Zone ownBox = layout.ownBoxZone(MatchSide.AWAY);
        MatchState state = MatchState.forSetPiece(MatchSide.AWAY, ownBox, SetPieceKind.GOAL_KICK);
        SetPieceResolver resolver = resolver(SEED);

        List<ActionOutcome> outcomes = IntStream.range(0, 2_000)
                .mapToObj(index -> resolver.resolve(state, away))
                .toList();

        assertThat(outcomes).extracting(ActionOutcome::durationSeconds)
                .allMatch(seconds -> config.goalKickDuration().contains(seconds))
                .contains(config.goalKickDuration().minSeconds(), config.goalKickDuration().maxSeconds());
    }

    @Test
    void aKickOffAfterAGoalIsAnEventWithoutPlayersAndWithoutDuration() {
        MatchState state = MatchState.forSetPiece(MatchSide.AWAY, layout.kickOffZone(), SetPieceKind.KICK_OFF);

        ActionOutcome outcome = resolver(SEED).resolve(state, away);

        assertThat(outcome.type()).isEqualTo(EventType.KICK_OFF_AFTER_GOAL);
        assertThat(outcome.participants()).isEqualTo(Participants.nobody());
        assertThat(outcome.durationSeconds()).isZero();
        assertThat(outcome.detail()).isEqualTo(EventDetail.none());
    }

    @Test
    void afterTheKickOffPlayRestartsInOpenPlayFromTheCentreWithTheSideThatConceded() {
        MatchState state = MatchState.forSetPiece(MatchSide.AWAY, layout.kickOffZone(), SetPieceKind.KICK_OFF);

        ActionOutcome outcome = resolver(SEED).resolve(state, away);

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
void aCornerIsTakenByAMidfielderOfTheAttackingSideFromTheOpponentBox() {
    MatchState state = cornerFor(MatchSide.AWAY);

    List<ActionOutcome> outcomes = resolveMany(state, 300);

    assertThat(outcomes).allSatisfy(outcome -> {
        Player taker = outcome.participants().primary().orElseThrow();
        assertThat(outcome.type()).isEqualTo(EventType.CORNER_KICK);
        assertThat(taker.role()).isEqualTo(Role.MIDFIELDER);
        assertThat(away.players()).contains(taker);
        assertThat(outcome.participants().secondary()).isEmpty();
        assertThat(outcome.detail()).isEqualTo(EventDetail.none());
    });
}

@Test
void aCornerStaysInTheSetPieceAndPutsTheCrossInFlightFromItsTaker() {
    MatchState state = cornerFor(MatchSide.AWAY);

    ActionOutcome outcome = resolver(SEED).resolve(state, away);

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
            .mapToObj(index -> resolver.resolve(state, away))
            .toList();
}

private List<Integer> durations(SetPieceResolver resolver, MatchState state, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> resolver.resolve(state, away).durationSeconds())
                .toList();
    }

    private SetPieceResolver resolver(long seed) {
        MatchRandom random = new MatchRandom(seed);
        EngineConfig engineConfig = EngineConfig.defaults();
        ShotResolver shotResolver = TestResolvers.shotResolver(engineConfig, layout, random);
        CrossResolver crossResolver = TestResolvers.crossResolver(engineConfig, layout, random, shotResolver);
        return new SetPieceResolver(config, random, crossResolver);
    }
}
