package org.tasktelemetry.matchsimulator.coach.human;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.config.Formation;
import org.tasktelemetry.matchsimulator.coach.config.FormationCatalog;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;

class AvailableFormationsTest {

    private final AvailableFormations available = new AvailableFormations(FormationCatalog.defaults());

    @Test
    void withTenOutfieldPlayersTheFormationsAreThoseOfTheCatalogInItsOrder() {
        List<Deployment> formations = available.forOutfieldPlayers(10);

        assertThat(labels(formations)).containsExactly("5-3-2", "4-5-1", "4-4-2", "3-5-2", "4-3-3");
    }

    @Test
    void withNineOutfieldPlayersTheReducedCatalogHasTheNineFormationsInTheOrderOfTheSpecification() {
        List<Deployment> formations = available.forOutfieldPlayers(9);

        assertThat(labels(formations)).containsExactly("5-3-1", "5-2-2", "4-4-1", "4-3-2", "4-2-3", "3-5-1", "3-4-2",
                "3-3-3", "2-5-2");
    }

    @Test
    void withEightOutfieldPlayersTwoMenAreTakenOffInEveryWayLeavingAtLeastOneForEverySector() {
        List<Deployment> formations = available.forOutfieldPlayers(8);

        assertThat(labels(formations)).containsExactly("5-2-1", "5-1-2", "4-3-1", "4-2-2", "4-1-3", "3-4-1", "3-3-2",
                "3-2-3", "2-5-1", "2-4-2", "2-3-3", "1-5-2");
    }

    @Test
    void aFormationObtainedFromMoreThanOneFormationOfTheCatalogAppearsOnlyOnce() {
        List<Deployment> formations = available.forOutfieldPlayers(8);

        assertThat(formations).doesNotHaveDuplicates();
    }

    @Test
    void whenTheOnlyFormationThatCanBeObtainedIsTheCurrentOneItIsTheOnlyOneReturned() {
        Formation fourFourTwo = new Formation(4, 4, 2, 0);
        AvailableFormations single = new AvailableFormations(new FormationCatalog(List.of(fourFourTwo)));

        List<Deployment> formations = single.forOutfieldPlayers(3);

        assertThat(formations).containsExactly(new Deployment(1, 1, 1));
    }

    @Test
    void aCustomCatalogIsConvertedInItsOwnOrderWhenTheTeamIsComplete() {
        Formation threeFiveTwo = new Formation(3, 5, 2, 1);
        Formation fiveThreeTwo = new Formation(5, 3, 2, -2);
        AvailableFormations custom = new AvailableFormations(new FormationCatalog(List.of(threeFiveTwo, fiveThreeTwo)));

        assertThat(labels(custom.forOutfieldPlayers(10))).containsExactly("3-5-2", "5-3-2");
    }

    private static List<String> labels(List<Deployment> formations) {
        return formations.stream()
                .map(Deployment::label)
                .toList();
    }
}
