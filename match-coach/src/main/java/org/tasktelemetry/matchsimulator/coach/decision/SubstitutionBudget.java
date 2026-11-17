package org.tasktelemetry.matchsimulator.coach.decision;

import org.tasktelemetry.matchsimulator.coach.config.ResultTimingConfig;
import org.tasktelemetry.matchsimulator.coach.config.SubstitutionConfig;

final class SubstitutionBudget {

    private final SubstitutionConfig substitutions;
    private final int reserveHoldMinute;

    SubstitutionBudget(SubstitutionConfig substitutions, ResultTimingConfig resultTiming) {
        this.substitutions = substitutions;
        this.reserveHoldMinute = resultTiming.reserveHoldMinute();
    }

    boolean allowsAnotherSubstitution(TeamNow team) {
        int maximum = substitutions.maximumSubstitutions();
        int left = team.lineup().bench().substitutionsLeft(maximum);
        int keptInReserve = substitutionsKeptInReserveAt(team.minute());
        return left > keptInReserve;
    }

    private int substitutionsKeptInReserveAt(int minute) {
        if (minute >= reserveHoldMinute) {
            return 0;
        }
        return substitutions.reservedSubstitutions();
    }
}
