package org.tasktelemetry.matchsimulator.engine.intervention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class InterventionTest {

    @ParameterizedTest
    @EnumSource(value = ChangeReason.class, names = {"INJURY", "RESERVE_GOALKEEPER", "EMPTIED_SECTOR",
            "MAKESHIFT_GOALKEEPER"})
    void aForcedReasonIsNotAllowedOnAnIntervention(ChangeReason forced) {
        assertThat(forced.isForced()).isTrue();

        assertThatThrownBy(() -> new Intervention.StyleChange(MatchSide.HOME, PlayStyle.DEFENSIVE, forced))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(forced.name());
    }

    @Test
    void everyOtherReasonIsVoluntaryAndAccepted() {
        ChangeReason[] voluntary = Arrays.stream(ChangeReason.values())
                .filter(reason -> !reason.isForced())
                .toArray(ChangeReason[]::new);

        assertThat(voluntary).isNotEmpty();
        for (ChangeReason reason : voluntary) {
            Intervention change = new Intervention.StyleChange(MatchSide.AWAY, PlayStyle.ATTACKING, reason);
            assertThat(change.reason()).isEqualTo(reason);
            assertThat(change.side()).isEqualTo(MatchSide.AWAY);
        }
    }

    @Test
    void aStyleChangeRefusesMissingValues() {
        assertThatThrownBy(() -> new Intervention.StyleChange(null, PlayStyle.ATTACKING, ChangeReason.CHASE_GAME))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Intervention.StyleChange(MatchSide.HOME, null, ChangeReason.CHASE_GAME))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Intervention.StyleChange(MatchSide.HOME, PlayStyle.ATTACKING, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void executedAndPendingResultsAreNotRefused() {
        assertThat(InterventionResult.executed()).isInstanceOf(InterventionResult.Executed.class);
        assertThat(InterventionResult.pending()).isInstanceOf(InterventionResult.Pending.class);
        assertThat(InterventionResult.executed().isRefused()).isFalse();
        assertThat(InterventionResult.pending().isRefused()).isFalse();
    }

    @Test
    void aRefusedResultCarriesItsReason() {
        InterventionResult result = InterventionResult.refused(RefusalReason.STYLE_ALREADY_IN_USE);

        assertThat(result.isRefused()).isTrue();
        assertThat(result).isInstanceOfSatisfying(InterventionResult.Refused.class,
                refused -> assertThat(refused.reason()).isEqualTo(RefusalReason.STYLE_ALREADY_IN_USE));
    }

    @Test
    void aRefusedResultRefusesAMissingReason() {
        assertThatThrownBy(() -> InterventionResult.refused(null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ChangeReason.class, names = {"INJURY", "RESERVE_GOALKEEPER", "EMPTIED_SECTOR",
            "MAKESHIFT_GOALKEEPER"})
    void aForcedReasonIsNotAllowedOnAVoluntarySubstitution(ChangeReason forced) {
        assertThatThrownBy(() -> new Intervention.VoluntarySubstitution(MatchSide.HOME, player("A"), player("B"),
                forced)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(forced.name());
    }

    @Test
    void aVoluntarySubstitutionRefusesMissingValues() {
        Player leaving = player("A");
        Player entering = player("B");
        ChangeReason reason = ChangeReason.PROTECT_LEAD;

        assertThatThrownBy(() -> new Intervention.VoluntarySubstitution(null, leaving, entering, reason))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Intervention.VoluntarySubstitution(MatchSide.HOME, null, entering, reason))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Intervention.VoluntarySubstitution(MatchSide.HOME, leaving, null, reason))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Intervention.VoluntarySubstitution(MatchSide.HOME, leaving, entering, null))
                .isInstanceOf(NullPointerException.class);
    }

    private Player player(String name) {
        return new Player(name, Role.MIDFIELDER, TestTeams.uniform(50));
    }
}
