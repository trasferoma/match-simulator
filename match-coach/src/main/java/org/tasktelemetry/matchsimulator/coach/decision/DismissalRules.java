package org.tasktelemetry.matchsimulator.coach.decision;

import java.util.Optional;

import org.tasktelemetry.matchsimulator.coach.reading.Dismissal;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.Role;

final class DismissalRules {

    private final DonorSectors donors;
    private final SectorReinforcement reinforcement;
    private final StyleSteps styleSteps;

    DismissalRules(DonorSectors donors, SectorReinforcement reinforcement, StyleSteps styleSteps) {
        this.donors = donors;
        this.reinforcement = reinforcement;
        this.styleSteps = styleSteps;
    }

    Optional<Intervention> rebalanceAfter(Dismissal dismissal, int lead, TeamNow team) {
        return switch (dismissal.sector()) {
            case DEFENDER -> replaceDefender(lead, team);
            case MIDFIELDER -> replaceMidfielder(lead, team);
            case FORWARD -> becomeMorePrudentUnlessBehind(lead, team);
            case GOALKEEPER -> Optional.empty();
        };
    }

    private Optional<Intervention> replaceDefender(int lead, TeamNow team) {
        Role donor = donors.forExtraDefender(team.lineup());
        Reinforcement plan = new Reinforcement(Role.DEFENDER, donor, ChangeReason.REBALANCE_AFTER_DISMISSAL);
        return reinforceOrBecomeMorePrudent(plan, lead, team);
    }

    private Optional<Intervention> replaceMidfielder(int lead, TeamNow team) {
        Role donor = donors.forExtraMidfielder(team.lineup());
        Reinforcement plan = new Reinforcement(Role.MIDFIELDER, donor, ChangeReason.REBALANCE_AFTER_DISMISSAL);
        return reinforceOrBecomeMorePrudent(plan, lead, team);
    }

    private Optional<Intervention> reinforceOrBecomeMorePrudent(Reinforcement plan, int lead, TeamNow team) {
        Optional<Intervention> reinforced = reinforcement.reinforce(plan, team);
        return reinforced.or(() -> becomeMorePrudentUnlessBehind(lead, team));
    }

    private Optional<Intervention> becomeMorePrudentUnlessBehind(int lead, TeamNow team) {
        if (lead < 0) {
            return Optional.empty();
        }
        return styleSteps.towardRegardlessOfRecentStyleChange(Direction.MORE_PRUDENT,
                ChangeReason.REBALANCE_AFTER_DISMISSAL, team);
    }
}
