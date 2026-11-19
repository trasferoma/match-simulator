package org.tasktelemetry.matchsimulator.engine.intervention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    @Test
    void aFormationChangeRefusesAnEmptyListOfShifts() {
        assertThatThrownBy(() -> new Intervention.FormationChange(MatchSide.HOME, List.of(),
                ChangeReason.MANAGER_DECISION)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aFormationChangeRefusesTheSamePlayerShiftedTwice() {
        Player player = player("A");
        List<Intervention.SectorShift> shifts = List.of(new Intervention.SectorShift(player, Role.FORWARD),
                new Intervention.SectorShift(player, Role.DEFENDER));

        assertThatThrownBy(() -> new Intervention.FormationChange(MatchSide.HOME, shifts,
                ChangeReason.MANAGER_DECISION)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ChangeReason.class, names = {"INJURY", "RESERVE_GOALKEEPER", "EMPTIED_SECTOR",
            "MAKESHIFT_GOALKEEPER"})
    void aForcedReasonIsNotAllowedOnAFormationChange(ChangeReason forced) {
        List<Intervention.SectorShift> shifts = List.of(new Intervention.SectorShift(player("A"), Role.FORWARD));

        assertThatThrownBy(() -> new Intervention.FormationChange(MatchSide.HOME, shifts, forced))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(forced.name());
    }

    @Test
    void aFormationChangeAndAShiftRefuseMissingValues() {
        Player player = player("A");
        List<Intervention.SectorShift> shifts = List.of(new Intervention.SectorShift(player, Role.FORWARD));
        ChangeReason reason = ChangeReason.MANAGER_DECISION;

        assertThatThrownBy(() -> new Intervention.FormationChange(null, shifts, reason))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Intervention.FormationChange(MatchSide.HOME, null, reason))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Intervention.FormationChange(MatchSide.HOME, shifts, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Intervention.SectorShift(null, Role.FORWARD))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Intervention.SectorShift(player, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aFormationChangeKeepsACopyOfItsShifts() {
        List<Intervention.SectorShift> shifts = new ArrayList<>();
        shifts.add(new Intervention.SectorShift(player("A"), Role.FORWARD));
        Intervention.FormationChange change = new Intervention.FormationChange(MatchSide.HOME, shifts,
                ChangeReason.MANAGER_DECISION);

        shifts.clear();

        assertThat(change.shifts()).hasSize(1);
    }

    private Player player(String name) {
        return new Player(name, Role.MIDFIELDER, TestTeams.uniform(50));
    }
}
