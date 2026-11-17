package org.tasktelemetry.matchsimulator.coach.decision;

import java.util.Optional;

import org.tasktelemetry.matchsimulator.coach.config.SectorLimits;
import org.tasktelemetry.matchsimulator.coach.reading.LineupReading;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

final class SectorReinforcement {

    private final SectorLimits limits;
    private final SubstitutionBudget budget;
    private final PlayerSelection selection;

    SectorReinforcement(SectorLimits limits, SubstitutionBudget budget, PlayerSelection selection) {
        this.limits = limits;
        this.budget = budget;
        this.selection = selection;
    }

    Optional<Intervention> reinforce(Reinforcement plan, TeamNow team) {
        if (!staysWithinLimits(plan, team.lineup())) {
            return Optional.empty();
        }
        Optional<Intervention> substitution = substituteForTheExtraPlayer(plan, team);
        return substitution.or(() -> moveTheExtraPlayer(plan, team));
    }

    private boolean staysWithinLimits(Reinforcement plan, LineupReading lineup) {
        int targetNow = lineup.countIn(plan.target());
        int donorNow = lineup.countIn(plan.donor());
        boolean targetHasRoom = limits.limitOf(plan.target()).allowsAnother(targetNow);
        boolean donorCanSpareOne = limits.limitOf(plan.donor()).canSpareOne(donorNow);
        return targetHasRoom && donorCanSpareOne;
    }

    private Optional<Intervention> substituteForTheExtraPlayer(Reinforcement plan, TeamNow team) {
        if (!budget.allowsAnotherSubstitution(team)) {
            return Optional.empty();
        }
        LineupReading lineup = team.lineup();
        Optional<Player> entering = selection.bestReserveWithRole(lineup, plan.target());
        Optional<Player> leaving = selection.weakestPlayingIn(team, plan.donor());
        if (entering.isEmpty() || leaving.isEmpty()) {
            return Optional.empty();
        }
        Intervention substitution = new Intervention.VoluntarySubstitution(team.side(), leaving.get(),
                entering.get(), plan.reason());
        return Optional.of(substitution);
    }

    private Optional<Intervention> moveTheExtraPlayer(Reinforcement plan, TeamNow team) {
        Optional<Player> mover = chooseWhoMoves(plan, team.lineup());
        return mover.map(player -> sectorMove(player, plan, team));
    }

    private Optional<Player> chooseWhoMoves(Reinforcement plan, LineupReading lineup) {
        Role donor = plan.donor();
        Optional<Player> returningToHisRole = selection.strongestPlayingInWithRole(lineup, donor, plan.target());
        if (returningToHisRole.isPresent()) {
            return returningToHisRole;
        }
        return selection.strongestPlayingIn(lineup, donor);
    }

    private Intervention sectorMove(Player player, Reinforcement plan, TeamNow team) {
        return new Intervention.SectorMove(team.side(), player, plan.target(), plan.reason());
    }
}
