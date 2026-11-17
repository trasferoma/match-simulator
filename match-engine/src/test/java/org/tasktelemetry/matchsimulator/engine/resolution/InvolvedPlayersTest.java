package org.tasktelemetry.matchsimulator.engine.resolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class InvolvedPlayersTest {

    private final Player first = TestTeams.player("First", Role.MIDFIELDER, TestTeams.uniform(50));
    private final Player second = TestTeams.player("Second", Role.DEFENDER, TestTeams.uniform(50));

    @Test
    void nobodyIsInvolvedByDefault() {
        assertThat(InvolvedPlayers.none().involvements()).isEmpty();
    }

    @Test
    void thePlayersAreKeptInTheOrderOfInsertion() {
        InvolvedPlayers involved = InvolvedPlayers.of(first, MatchSide.HOME).with(second, MatchSide.AWAY);

        assertThat(involved.involvements()).containsExactly(
                new InvolvedPlayers.Involvement(first, MatchSide.HOME),
                new InvolvedPlayers.Involvement(second, MatchSide.AWAY));
    }

    @Test
    void aPlayerAddedTwiceIsInvolvedOnce() {
        InvolvedPlayers involved = InvolvedPlayers.of(first, MatchSide.HOME).with(first, MatchSide.HOME);

        assertThat(involved.involvements()).hasSize(1);
    }

    @Test
    void thePlayerIsKnownTogetherWithTheSide() {
        InvolvedPlayers involved = InvolvedPlayers.of(first, MatchSide.HOME);

        assertThat(involved.contains(first, MatchSide.HOME)).isTrue();
        assertThat(involved.contains(first, MatchSide.AWAY)).isFalse();
        assertThat(involved.contains(second, MatchSide.HOME)).isFalse();
    }

    @Test
    void aDuelInvolvesTheAttackerOnHisSideAndTheDefenderOnTheOtherOne() {
        InvolvedPlayers involved = InvolvedPlayers.ofDuel(first, MatchSide.AWAY, second);

        assertThat(involved.involvements()).containsExactly(
                new InvolvedPlayers.Involvement(first, MatchSide.AWAY),
                new InvolvedPlayers.Involvement(second, MatchSide.HOME));
    }

    @Test
    void theListIsNotModifiableAndAddingDoesNotChangeTheOriginal() {
        InvolvedPlayers original = InvolvedPlayers.of(first, MatchSide.HOME);

        InvolvedPlayers extended = original.with(second, MatchSide.HOME);

        assertThat(original.involvements()).hasSize(1);
        assertThat(extended.involvements()).hasSize(2);
        assertThatThrownBy(() -> original.involvements().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void theSamePlayerTwiceInTheConstructorIsRejected() {
        InvolvedPlayers.Involvement involvement = new InvolvedPlayers.Involvement(first, MatchSide.HOME);

        assertThatThrownBy(() -> new InvolvedPlayers(List.of(involvement, involvement)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only once");
    }

    @Test
    void aMissingPlayerOrSideIsRejected() {
        assertThatThrownBy(() -> InvolvedPlayers.of(null, MatchSide.HOME)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> InvolvedPlayers.of(first, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InvolvedPlayers(null)).isInstanceOf(NullPointerException.class);
    }
}
