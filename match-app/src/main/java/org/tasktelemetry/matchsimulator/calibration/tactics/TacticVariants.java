package org.tasktelemetry.matchsimulator.calibration.tactics;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.tasktelemetry.matchsimulator.coach.config.Formation;
import org.tasktelemetry.matchsimulator.coach.config.FormationCatalog;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;

public record TacticVariants(TacticVariant reference, List<TacticVariant> all) {

    private static final PlayStyle REFERENCE_STYLE = PlayStyle.BALANCED;
    private static final String REFERENCE_FORMATION_LABEL = "4-4-2";

    public TacticVariants {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(all, "variants must not be null");
        all = List.copyOf(all);
    }

    public static TacticVariants from(FormationCatalog catalog) {
        Objects.requireNonNull(catalog, "formation catalog must not be null");
        Formation referenceFormation = findReferenceFormation(catalog);
        TacticVariant reference = new TacticVariant(REFERENCE_STYLE, referenceFormation);
        List<TacticVariant> referenceOnly = List.of(reference);
        List<TacticVariant> styleVariants = listStyleVariants(referenceFormation);
        List<TacticVariant> formationVariants = listFormationVariants(catalog, referenceFormation);
        List<TacticVariant> all = Stream.of(referenceOnly, styleVariants, formationVariants)
                .flatMap(List::stream)
                .toList();
        return new TacticVariants(reference, all);
    }

    private static Formation findReferenceFormation(FormationCatalog catalog) {
        return catalog.formations().stream()
                .filter(formation -> formation.label().equals(REFERENCE_FORMATION_LABEL))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "the formation catalog has no reference formation " + REFERENCE_FORMATION_LABEL));
    }

    private static List<TacticVariant> listStyleVariants(Formation referenceFormation) {
        return Arrays.stream(PlayStyle.values())
                .filter(style -> style != REFERENCE_STYLE)
                .map(style -> new TacticVariant(style, referenceFormation))
                .toList();
    }

    private static List<TacticVariant> listFormationVariants(FormationCatalog catalog, Formation referenceFormation) {
        return catalog.formations().stream()
                .filter(formation -> !formation.equals(referenceFormation))
                .map(formation -> new TacticVariant(REFERENCE_STYLE, formation))
                .toList();
    }
}
