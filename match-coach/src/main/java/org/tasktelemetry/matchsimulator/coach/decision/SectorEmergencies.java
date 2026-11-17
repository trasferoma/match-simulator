package org.tasktelemetry.matchsimulator.coach.decision;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.tasktelemetry.matchsimulator.coach.reading.Situation;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Role;

final class SectorEmergencies {

    private static final List<Role> SECTORS = List.of(Role.DEFENDER, Role.MIDFIELDER, Role.FORWARD);
    private static final ChangeReason REASON = ChangeReason.REINFORCE_STRUGGLING_SECTOR;

    private final DonorSectors donors;
    private final SectorReinforcement reinforcement;
    private final StyleSteps styleSteps;

    SectorEmergencies(DonorSectors donors, SectorReinforcement reinforcement, StyleSteps styleSteps) {
        this.donors = donors;
        this.reinforcement = reinforcement;
        this.styleSteps = styleSteps;
    }

    Optional<Intervention> respondInOrdinaryPlay(Predicate<Situation> isConfirmed, DecisionMoment moment, int lead,
            TeamNow team) {
        return respond(isConfirmed, moment, StylePolicy.ANY_DIRECTION, lead, team);
    }

    Optional<Intervention> respondWhileTakingRisk(Predicate<Situation> isConfirmed, DecisionMoment moment, int lead,
            TeamNow team) {
        return respond(isConfirmed, moment, StylePolicy.NEVER_MORE_PRUDENT, lead, team);
    }

    private Optional<Intervention> respond(Predicate<Situation> isConfirmed, DecisionMoment moment,
            StylePolicy stylePolicy, int lead, TeamNow team) {
        List<Role> strained = strainedSectors(isConfirmed, moment.respondsToMildSectorStrain());
        return strained.stream()
                .map(sector -> respondForSector(sector, stylePolicy, lead, team))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private List<Role> strainedSectors(Predicate<Situation> isConfirmed, boolean includesMildStrain) {
        List<Role> inCrisis = SECTORS.stream()
                .filter(sector -> isInCrisis(sector, isConfirmed))
                .toList();
        if (!includesMildStrain) {
            return inCrisis;
        }
        List<Role> struggling = SECTORS.stream()
                .filter(sector -> isStruggling(sector, isConfirmed))
                .filter(sector -> !inCrisis.contains(sector))
                .toList();
        return Stream.concat(inCrisis.stream(), struggling.stream()).toList();
    }

    private boolean isInCrisis(Role sector, Predicate<Situation> isConfirmed) {
        Situation crisis = Situation.crisisOf(sector);
        return isConfirmed.test(crisis);
    }

    private boolean isStruggling(Role sector, Predicate<Situation> isConfirmed) {
        Situation struggling = Situation.strugglingOf(sector);
        return isConfirmed.test(struggling);
    }

    private Optional<Intervention> respondForSector(Role sector, StylePolicy stylePolicy, int lead, TeamNow team) {
        return switch (sector) {
            case DEFENDER -> shoreUpTheDefence(stylePolicy, team);
            case MIDFIELDER -> shoreUpTheMidfield(stylePolicy, team);
            case FORWARD -> sharpenTheAttackUnlessAhead(stylePolicy, lead, team);
            case GOALKEEPER -> Optional.empty();
        };
    }

    private Optional<Intervention> shoreUpTheDefence(StylePolicy stylePolicy, TeamNow team) {
        return styleFirst(PlayStyle.DEFENSIVE, stylePolicy, team, () -> addADefender(team));
    }

    private Optional<Intervention> shoreUpTheMidfield(StylePolicy stylePolicy, TeamNow team) {
        return styleFirst(PlayStyle.POSSESSION, stylePolicy, team, () -> addAMidfielder(team));
    }

    private Optional<Intervention> sharpenTheAttackUnlessAhead(StylePolicy stylePolicy, int lead, TeamNow team) {
        if (lead > 0) {
            return Optional.empty();
        }
        return styleFirst(PlayStyle.ATTACKING, stylePolicy, team, () -> addAnAttacker(team));
    }

    private Optional<Intervention> styleFirst(PlayStyle target, StylePolicy stylePolicy, TeamNow team,
            Supplier<Optional<Intervention>> module) {
        boolean styleCannotArrive = !styleSteps.canStepTowardStyle(target, team);
        boolean policyForbidsTheStep = !stylePolicy.allows(styleSteps.needsMorePrudentStyle(target, team));
        if (styleCannotArrive || policyForbidsTheStep) {
            return module.get();
        }
        return styleSteps.towardStyle(target, REASON, team);
    }

    private Optional<Intervention> addADefender(TeamNow team) {
        Role donor = donors.forStrugglingDefence(team.lineup());
        return addAPlayer(Role.DEFENDER, donor, team);
    }

    private Optional<Intervention> addAMidfielder(TeamNow team) {
        Role donor = donors.forExtraMidfielder(team.lineup());
        return addAPlayer(Role.MIDFIELDER, donor, team);
    }

    private Optional<Intervention> addAnAttacker(TeamNow team) {
        Role donor = donors.forStrugglingAttack();
        return addAPlayer(Role.FORWARD, donor, team);
    }

    private Optional<Intervention> addAPlayer(Role target, Role donor, TeamNow team) {
        Reinforcement plan = new Reinforcement(target, donor, REASON);
        return reinforcement.reinforce(plan, team);
    }

    private enum StylePolicy {
        ANY_DIRECTION,
        NEVER_MORE_PRUDENT;

        boolean allows(boolean stepIsMorePrudent) {
            return this == ANY_DIRECTION || !stepIsMorePrudent;
        }
    }
}
