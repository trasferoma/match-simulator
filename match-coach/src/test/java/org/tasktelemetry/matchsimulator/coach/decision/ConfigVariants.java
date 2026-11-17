package org.tasktelemetry.matchsimulator.coach.decision;

import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.config.SectorLimits;
import org.tasktelemetry.matchsimulator.coach.config.SubstitutionConfig;

final class ConfigVariants {

    private ConfigVariants() {
    }

    static CoachConfig withSubstitutions(int maximum, int reserved) {
        CoachConfig base = CoachConfig.defaults();
        SubstitutionConfig substitutions = new SubstitutionConfig(maximum, reserved);
        return new CoachConfig(base.assessment(), base.bandResponses(), base.formations(), base.styleScale(),
                base.sectorLimits(), base.maximumBenchSize(), base.timing(), base.resultTiming(), base.flow(),
                base.sectorStrain(), base.tacticalReading(), substitutions);
    }

    static CoachConfig withSectorLimits(SectorLimits limits) {
        CoachConfig base = CoachConfig.defaults();
        return new CoachConfig(base.assessment(), base.bandResponses(), base.formations(), base.styleScale(),
                limits, base.maximumBenchSize(), base.timing(), base.resultTiming(), base.flow(),
                base.sectorStrain(), base.tacticalReading(), base.substitutions());
    }
}
