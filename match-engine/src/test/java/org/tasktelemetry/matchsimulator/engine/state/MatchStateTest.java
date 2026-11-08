package org.tasktelemetry.matchsimulator.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
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
}
