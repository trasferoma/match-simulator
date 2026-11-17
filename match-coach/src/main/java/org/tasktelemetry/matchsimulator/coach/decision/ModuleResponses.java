package org.tasktelemetry.matchsimulator.coach.decision;

import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.Role;

final class ModuleResponses {

    private final DonorSectors donors;
    private final SectorReinforcement reinforcement;
    private final FatigueRules freshness;

    ModuleResponses(DonorSectors donors, SectorReinforcement reinforcement, FatigueRules freshness) {
        this.donors = donors;
        this.reinforcement = reinforcement;
        this.freshness = freshness;
    }

    Optional<Intervention> answer(Response response, TeamNow team) {
        return switch (response.moduleChange()) {
            case NONE -> Optional.empty();
            case EXTRA_ATTACKER -> addAnAttacker(response, team);
            case EXTRA_DEFENDER -> addADefender(response, team);
        };
    }

    private Optional<Intervention> addAnAttacker(Response response, TeamNow team) {
        Role donor = donors.forExtraAttacker(team.lineup());
        Reinforcement plan = new Reinforcement(Role.FORWARD, donor, response.reason());
        Optional<Intervention> added = reinforcement.reinforce(plan, team);
        return added.or(() -> refreshTiredForwardWhileChasing(response, team));
    }

    private Optional<Intervention> refreshTiredForwardWhileChasing(Response response, TeamNow team) {
        if (response.reason() != ChangeReason.CHASE_GAME) {
            return Optional.empty();
        }
        return freshness.replaceTiredForward(response.reason(), team);
    }

    private Optional<Intervention> addADefender(Response response, TeamNow team) {
        Role donor = donors.forExtraDefender(team.lineup());
        Reinforcement plan = new Reinforcement(Role.DEFENDER, donor, response.reason());
        return reinforcement.reinforce(plan, team);
    }
}
