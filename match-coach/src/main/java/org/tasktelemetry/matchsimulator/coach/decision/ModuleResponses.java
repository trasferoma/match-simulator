package org.tasktelemetry.matchsimulator.coach.decision;

import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.Role;

final class ModuleResponses {

    private final DonorSectors donors;
    private final SectorReinforcement reinforcement;

    ModuleResponses(DonorSectors donors, SectorReinforcement reinforcement) {
        this.donors = donors;
        this.reinforcement = reinforcement;
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
        return reinforcement.reinforce(plan, team);
    }

    private Optional<Intervention> addADefender(Response response, TeamNow team) {
        Role donor = donors.forExtraDefender(team.lineup());
        Reinforcement plan = new Reinforcement(Role.DEFENDER, donor, response.reason());
        return reinforcement.reinforce(plan, team);
    }
}
