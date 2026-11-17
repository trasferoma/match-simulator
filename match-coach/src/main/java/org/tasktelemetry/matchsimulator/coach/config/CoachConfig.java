package org.tasktelemetry.matchsimulator.coach.config;

import java.util.Objects;

public record CoachConfig(
        AssessmentConfig assessment,
        BandResponses bandResponses,
        FormationCatalog formations,
        StylePrudenceScale styleScale,
        SectorLimits sectorLimits,
        int maximumBenchSize,
        TimingConfig timing,
        ResultTimingConfig resultTiming,
        FlowConfig flow,
        SectorStrainConfig sectorStrain,
        TacticalReadingConfig tacticalReading,
        SubstitutionConfig substitutions,
        FatigueResponseConfig fatigueResponse) {

    public CoachConfig {
        Objects.requireNonNull(assessment, "assessment configuration must not be null");
        Objects.requireNonNull(bandResponses, "band responses must not be null");
        Objects.requireNonNull(formations, "formation catalog must not be null");
        Objects.requireNonNull(styleScale, "style prudence scale must not be null");
        Objects.requireNonNull(sectorLimits, "sector limits must not be null");
        Objects.requireNonNull(timing, "timing configuration must not be null");
        Objects.requireNonNull(resultTiming, "result timing configuration must not be null");
        Objects.requireNonNull(flow, "flow configuration must not be null");
        Objects.requireNonNull(sectorStrain, "sector strain configuration must not be null");
        Objects.requireNonNull(tacticalReading, "tactical reading configuration must not be null");
        Objects.requireNonNull(substitutions, "substitution configuration must not be null");
        Objects.requireNonNull(fatigueResponse, "fatigue response configuration must not be null");
        ConfigGuards.requireAtLeastOne("maximum bench size", maximumBenchSize);
    }

    public static CoachConfig defaults() {
        AssessmentConfig assessment = AssessmentConfig.defaults();
        BandResponses bandResponses = BandResponses.defaults();
        FormationCatalog formations = FormationCatalog.defaults();
        StylePrudenceScale styleScale = StylePrudenceScale.defaults();
        SectorLimits sectorLimits = SectorLimits.defaults();
        int maximumBenchSize = 9;
        TimingConfig timing = TimingConfig.defaults();
        ResultTimingConfig resultTiming = ResultTimingConfig.defaults();
        FlowConfig flow = FlowConfig.defaults();
        SectorStrainConfig sectorStrain = SectorStrainConfig.defaults();
        TacticalReadingConfig tacticalReading = TacticalReadingConfig.defaults();
        SubstitutionConfig substitutions = SubstitutionConfig.defaults();
        FatigueResponseConfig fatigueResponse = FatigueResponseConfig.defaults();
        return new CoachConfig(assessment, bandResponses, formations, styleScale, sectorLimits, maximumBenchSize,
                timing, resultTiming, flow, sectorStrain, tacticalReading, substitutions, fatigueResponse);
    }
}
