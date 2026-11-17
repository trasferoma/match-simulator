package org.tasktelemetry.matchsimulator.coach.decision;

import org.tasktelemetry.matchsimulator.coach.config.SectorLimits;
import org.tasktelemetry.matchsimulator.coach.reading.LineupReading;
import org.tasktelemetry.matchsimulator.engine.team.Role;

final class DonorSectors {

    private final SectorLimits limits;

    DonorSectors(SectorLimits limits) {
        this.limits = limits;
    }

    Role forExtraAttacker(LineupReading lineup) {
        int midfielders = lineup.countIn(Role.MIDFIELDER);
        int defenders = lineup.countIn(Role.DEFENDER);
        return midfielders > defenders ? Role.MIDFIELDER : Role.DEFENDER;
    }

    Role forExtraDefender(LineupReading lineup) {
        return attackOrElse(lineup, Role.MIDFIELDER);
    }

    Role forExtraMidfielder(LineupReading lineup) {
        return attackOrElse(lineup, Role.DEFENDER);
    }

    Role forStrugglingDefence(LineupReading lineup) {
        int midfielders = lineup.countIn(Role.MIDFIELDER);
        int forwards = lineup.countIn(Role.FORWARD);
        return forwards >= midfielders ? Role.FORWARD : Role.MIDFIELDER;
    }

    Role forStrugglingAttack() {
        return Role.MIDFIELDER;
    }

    private Role attackOrElse(LineupReading lineup, Role alternative) {
        int forwards = lineup.countIn(Role.FORWARD);
        boolean attackCanSpareOne = limits.forwards().canSpareOne(forwards);
        return attackCanSpareOne ? Role.FORWARD : alternative;
    }
}
