package org.tasktelemetry.matchsimulator.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class MatchStateTest {

    private final Zone zone = PitchLayout.standard().kickOffZone();

    @Test
    void movingToOpenPlayKeepsPossessionAndZone() {
        MatchState setPiece = MatchState.forSetPiece(MatchSide.AWAY, zone, SetPieceKind.GOAL_KICK);

        MatchState openPlay = setPiece.inOpenPlay();

        assertThat(openPlay).isEqualTo(new MatchState(MatchSide.AWAY, zone, PlayPhase.BUILD_UP));
    }

    @Test
    void aSetPieceStateKnowsItsKindAndAnOpenPlayStateHasNone() {
        MatchState setPiece = MatchState.forSetPiece(MatchSide.HOME, zone, SetPieceKind.KICK_OFF);
        MatchState openPlay = MatchState.openPlay(MatchSide.HOME, zone);

        assertThat(setPiece.phase()).isEqualTo(PlayPhase.SET_PIECE);
        assertThat(setPiece.setPiece()).isEqualTo(SetPieceKind.KICK_OFF);
        assertThat(openPlay.phase()).isEqualTo(PlayPhase.BUILD_UP);
        assertThat(openPlay.setPiece()).isNull();
    }

    @ParameterizedTest
    @EnumSource(SetPieceKind.class)
    void onlyTheSetPieceOfAPenaltyIsAwaitingAPenalty(SetPieceKind kind) {
        MatchState state = MatchState.forSetPiece(MatchSide.HOME, zone, kind);

        assertThat(state.isAwaitingPenalty()).isEqualTo(kind == SetPieceKind.PENALTY);
    }

    @Test
    void anOpenPlayStateIsNotAwaitingAPenalty() {
        assertThat(MatchState.openPlay(MatchSide.HOME, zone).isAwaitingPenalty()).isFalse();
    }

    @Test
    void rejectsASetPieceWithoutKindAndAKindOutsideASetPiece() {
        assertThatThrownBy(() -> new MatchState(MatchSide.HOME, zone, PlayPhase.SET_PIECE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs the kind of set piece");
        assertThatThrownBy(() -> new MatchState(MatchSide.HOME, zone, PlayPhase.BUILD_UP, SetPieceKind.GOAL_KICK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no set piece");
    }

@Test
void aStateWithACrossInFlightKnowsItsCrosserAndKeepsPhaseAndSetPiece() {
    Player crosser = TestTeams.player("Rossi", Role.MIDFIELDER, TestTeams.uniform(60));
    MatchState corner = MatchState.forSetPiece(MatchSide.HOME, zone, SetPieceKind.CORNER);

    MatchState inFlight = corner.withCrossInFlightFrom(crosser);

    assertThat(inFlight.hasCrossInFlight()).isTrue();
    assertThat(inFlight.crosser()).isEqualTo(crosser);
    assertThat(inFlight.phase()).isEqualTo(PlayPhase.SET_PIECE);
    assertThat(inFlight.setPiece()).isEqualTo(SetPieceKind.CORNER);
    assertThat(corner.hasCrossInFlight()).isFalse();
}

@Test
void movingToOpenPlayDropsTheCrossInFlight() {
    Player crosser = TestTeams.player("Rossi", Role.MIDFIELDER, TestTeams.uniform(60));
    MatchState inFlight = MatchState.openPlay(MatchSide.HOME, zone).withCrossInFlightFrom(crosser);

    MatchState afterwards = inFlight.inOpenPlay();

    assertThat(afterwards.hasCrossInFlight()).isFalse();
}

@Test
void rejectsACrossInFlightWithoutCrosser() {
    MatchState openPlay = MatchState.openPlay(MatchSide.HOME, zone);

    assertThatThrownBy(() -> openPlay.withCrossInFlightFrom(null)).isInstanceOf(NullPointerException.class);
}

@Test
void rejectsMissingValues() {
        assertThatThrownBy(() -> new MatchState(null, zone, PlayPhase.BUILD_UP))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MatchState(MatchSide.HOME, null, PlayPhase.BUILD_UP))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MatchState(MatchSide.HOME, zone, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aCounterAttackKeepsPossessionAndZoneAndHasNoSetPiece() {
        MatchState openPlay = MatchState.openPlay(MatchSide.AWAY, zone);

        MatchState counterAttack = openPlay.inCounterAttack();

        assertThat(counterAttack).isEqualTo(new MatchState(MatchSide.AWAY, zone, PlayPhase.COUNTER_ATTACK));
        assertThat(counterAttack.setPiece()).isNull();
        assertThat(counterAttack.hasCrossInFlight()).isFalse();
    }

    @Test
    void aCrossCanBeInFlightFromOpenBuildUpPlayFromACornerAndFromAFreeKick() {
        Player crosser = TestTeams.player("Rossi", Role.MIDFIELDER, TestTeams.uniform(60));

        MatchState fromBuildUp = new MatchState(MatchSide.HOME, zone, PlayPhase.BUILD_UP, null, crosser);
        MatchState fromCorner = new MatchState(MatchSide.HOME, zone, PlayPhase.SET_PIECE, SetPieceKind.CORNER,
                crosser);
        MatchState fromFreeKick = new MatchState(MatchSide.HOME, zone, PlayPhase.SET_PIECE, SetPieceKind.FREE_KICK,
                crosser);

        assertThat(fromBuildUp.crosser()).isEqualTo(crosser);
        assertThat(fromCorner.crosser()).isEqualTo(crosser);
        assertThat(fromFreeKick.crosser()).isEqualTo(crosser);
    }

    @Test
    void rejectsACrossInFlightDuringACounterAttack() {
        Player crosser = TestTeams.player("Rossi", Role.MIDFIELDER, TestTeams.uniform(60));

        assertThatThrownBy(() -> new MatchState(MatchSide.HOME, zone, PlayPhase.COUNTER_ATTACK, null, crosser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a cross can be in flight only from open build-up play, a corner or a free kick");
        assertThatThrownBy(() -> MatchState.openPlay(MatchSide.HOME, zone).inCounterAttack()
                .withCrossInFlightFrom(crosser))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsACrossInFlightFromASetPieceThatIsNotACorner() {
        Player crosser = TestTeams.player("Rossi", Role.MIDFIELDER, TestTeams.uniform(60));

        assertThatThrownBy(() -> new MatchState(MatchSide.HOME, zone, PlayPhase.SET_PIECE, SetPieceKind.GOAL_KICK,
                crosser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a cross can be in flight only from open build-up play, a corner or a free kick");
        assertThatThrownBy(() -> MatchState.forSetPiece(MatchSide.HOME, zone, SetPieceKind.KICK_OFF)
                .withCrossInFlightFrom(crosser))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anOpenPlayStateRemembersTheLastPasserAndMovingKeepsHim() {
        Player passer = TestTeams.player("Rossi", Role.MIDFIELDER, TestTeams.uniform(60));
        Zone other = PitchLayout.standard().zones().getFirst();

        MatchState afterPass = MatchState.openPlay(MatchSide.HOME, zone).withLastPasser(passer);

        assertThat(afterPass.hasLastPasser()).isTrue();
        assertThat(afterPass.lastPasser()).isEqualTo(passer);
        assertThat(afterPass.movedTo(other).lastPasser()).isEqualTo(passer);
        assertThat(afterPass.movedTo(other).zone()).isEqualTo(other);
        assertThat(MatchState.openPlay(MatchSide.HOME, zone).hasLastPasser()).isFalse();
    }

    @Test
    void openingPlayAgainOrStartingACrossForgetsTheLastPasser() {
        Player passer = TestTeams.player("Rossi", Role.MIDFIELDER, TestTeams.uniform(60));
        MatchState afterPass = MatchState.openPlay(MatchSide.HOME, zone).withLastPasser(passer);

        assertThat(afterPass.inOpenPlay().hasLastPasser()).isFalse();
        assertThat(afterPass.withCrossInFlightFrom(passer).hasLastPasser()).isFalse();
    }

    @Test
    void withoutTheLastPasserTheStateIsTheSameButForHim() {
        Player passer = TestTeams.player("Rossi", Role.MIDFIELDER, TestTeams.uniform(60));
        MatchState afterPass = MatchState.openPlay(MatchSide.HOME, zone).withLastPasser(passer);

        MatchState forgotten = afterPass.withoutLastPasser();

        assertThat(forgotten.hasLastPasser()).isFalse();
        assertThat(forgotten).isEqualTo(MatchState.openPlay(MatchSide.HOME, zone));
    }

    @Test
    void rejectsALastPasserOutsideOpenBuildUpPlay() {
        Player passer = TestTeams.player("Rossi", Role.MIDFIELDER, TestTeams.uniform(60));

        assertThatThrownBy(() -> MatchState.forSetPiece(MatchSide.HOME, zone, SetPieceKind.CORNER)
                .withLastPasser(passer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a last passer can be remembered only in open build-up play");
        assertThatThrownBy(() -> MatchState.openPlay(MatchSide.HOME, zone).inCounterAttack()
                .withLastPasser(passer))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MatchState.openPlay(MatchSide.HOME, zone).withCrossInFlightFrom(passer)
                .withLastPasser(passer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANullLastPasser() {
        assertThatThrownBy(() -> MatchState.openPlay(MatchSide.HOME, zone).withLastPasser(null))
                .isInstanceOf(NullPointerException.class);
    }
}
