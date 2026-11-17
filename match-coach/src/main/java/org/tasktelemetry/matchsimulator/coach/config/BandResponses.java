package org.tasktelemetry.matchsimulator.coach.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;

public record BandResponses(Map<AssessmentBand, BandResponse> responseByBand) {

    public BandResponses {
        Objects.requireNonNull(responseByBand, "band responses must not be null");
        responseByBand = completeCopyOf(responseByBand);
    }

    public static BandResponses defaults() {
        BandResponse muchWeaker = new BandResponse(-2, PlayStyle.DEFENSIVE);
        BandResponse weaker = new BandResponse(-1, PlayStyle.COUNTER_ATTACK);
        BandResponse even = new BandResponse(0, PlayStyle.BALANCED);
        BandResponse stronger = new BandResponse(1, PlayStyle.POSSESSION);
        BandResponse muchStronger = new BandResponse(2, PlayStyle.ATTACKING);
        Map<AssessmentBand, BandResponse> responseByBand = new EnumMap<>(AssessmentBand.class);
        responseByBand.put(AssessmentBand.MUCH_WEAKER, muchWeaker);
        responseByBand.put(AssessmentBand.WEAKER, weaker);
        responseByBand.put(AssessmentBand.EVEN, even);
        responseByBand.put(AssessmentBand.STRONGER, stronger);
        responseByBand.put(AssessmentBand.MUCH_STRONGER, muchStronger);
        return new BandResponses(responseByBand);
    }

    public BandResponse responseOf(AssessmentBand band) {
        return responseByBand.get(band);
    }

    private static Map<AssessmentBand, BandResponse> completeCopyOf(Map<AssessmentBand, BandResponse> values) {
        Map<AssessmentBand, BandResponse> copy = new EnumMap<>(AssessmentBand.class);
        copy.putAll(values);
        for (AssessmentBand band : AssessmentBand.values()) {
            if (copy.get(band) == null) {
                throw new IllegalArgumentException("band responses need a value for " + band);
            }
        }
        return Collections.unmodifiableMap(copy);
    }
}
