package org.tasktelemetry.matchsimulator.coach.config;

import java.util.List;
import java.util.Objects;

public record FormationCatalog(List<Formation> formations) {

    public FormationCatalog {
        Objects.requireNonNull(formations, "formations must not be null");
        formations = List.copyOf(formations);
        requireNotEmpty(formations);
    }

    public static FormationCatalog defaults() {
        Formation fiveThreeTwo = new Formation(5, 3, 2, -2);
        Formation fourFiveOne = new Formation(4, 5, 1, -1);
        Formation fourFourTwo = new Formation(4, 4, 2, 0);
        Formation threeFiveTwo = new Formation(3, 5, 2, 1);
        Formation fourThreeThree = new Formation(4, 3, 3, 2);
        List<Formation> formations = List.of(fiveThreeTwo, fourFiveOne, fourFourTwo, threeFiveTwo, fourThreeThree);
        return new FormationCatalog(formations);
    }

    private static void requireNotEmpty(List<Formation> formations) {
        if (formations.isEmpty()) {
            throw new IllegalArgumentException("the formation catalog must not be empty");
        }
    }
}
