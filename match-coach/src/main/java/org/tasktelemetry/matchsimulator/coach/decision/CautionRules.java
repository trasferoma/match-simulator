package org.tasktelemetry.matchsimulator.coach.decision;

import java.util.List;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.coach.reading.LineupReading;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

final class CautionRules {

    private final SubstitutionBudget budget;
    private final PlayerSelection selection;

    CautionRules(SubstitutionBudget budget, PlayerSelection selection) {
        this.budget = budget;
        this.selection = selection;
    }

    Optional<Intervention> replaceCautionedDefender(TeamNow team) {
        LineupReading lineup = team.lineup();
        List<Player> cautionedDefenders = lineup.cautionedPlayersIn(Role.DEFENDER);
        Optional<Player> entering = selection.bestReserveWithRole(lineup, Role.DEFENDER);
        Optional<Player> leaving = selection.weakestAmong(cautionedDefenders, team);
        boolean canReplace = leaving.isPresent() && entering.isPresent() && budget.allowsAnotherSubstitution(team);
        if (!canReplace) {
            return Optional.empty();
        }
        Intervention substitution = new Intervention.VoluntarySubstitution(team.side(), leaving.get(), entering.get(),
                ChangeReason.AVOID_SECOND_YELLOW);
        return Optional.of(substitution);
    }
}
