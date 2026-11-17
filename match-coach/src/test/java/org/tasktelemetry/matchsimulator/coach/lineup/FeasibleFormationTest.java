package org.tasktelemetry.matchsimulator.coach.lineup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.config.Formation;
import org.tasktelemetry.matchsimulator.coach.config.FormationCatalog;
import org.tasktelemetry.matchsimulator.coach.squad.SquadRule;
import org.tasktelemetry.matchsimulator.coach.squad.SquadRuleViolation;
import org.tasktelemetry.matchsimulator.engine.team.Player;

class FeasibleFormationTest {

    private final FeasibleFormations feasible = new FeasibleFormations(FormationCatalog.defaults());

    private List<String> labelsFeasibleWith(int goalkeepers, int defenders, int midfielders, int forwards) {
        List<Player> players = TestSquads.uniformPlayers("p", goalkeepers, defenders, midfielders, forwards, 50);
        return feasible.amongPlayers(players).stream()
                .map(Formation::label)
                .toList();
    }

    @Test
    void aRosterWithEnoughPlayersInEverySectorMakesEveryFormationFeasibleInCatalogOrder() {
        assertThat(labelsFeasibleWith(2, 6, 6, 4)).containsExactly("5-3-2", "4-5-1", "4-4-2", "3-5-2", "4-3-3");
    }

    @Test
    void aFormationIsFeasibleOnlyWhenEverySectorHasEnoughPlayers() {
        assertThat(labelsFeasibleWith(1, 4, 5, 2)).containsExactly("4-5-1", "4-4-2", "3-5-2");
    }

    @Test
    void tooFewDefendersExcludeTheFormationsThatNeedThem() {
        assertThat(labelsFeasibleWith(1, 3, 7, 2)).containsExactly("3-5-2");
    }

    @Test
    void tooFewMidfieldersExcludeTheFormationsThatNeedThem() {
        assertThat(labelsFeasibleWith(1, 5, 3, 3)).containsExactly("5-3-2", "4-3-3");
    }

    @Test
    void tooFewForwardsExcludeTheFormationsThatNeedThem() {
        assertThat(labelsFeasibleWith(1, 5, 5, 1)).containsExactly("4-5-1");
    }

    @Test
    void aRosterWithoutGoalkeepersMakesNoFormationFeasible() {
        assertThat(labelsFeasibleWith(0, 6, 6, 4)).isEmpty();
    }

    @Test
    void aRosterWithAtLeastOneFeasibleFormationIsAccepted() {
        List<Player> players = TestSquads.uniformPlayers("p", 1, 4, 3, 3, 50);

        List<Formation> accepted = feasible.requireAtLeastOne(players);

        assertThat(accepted).containsExactly(new Formation(4, 3, 3, 2));
    }

    @Test
    void aRosterWithTwoDefendersSevenMidfieldersAndOneForwardIsRejectedWithTheCatalog() {
        List<Player> players = TestSquads.uniformPlayers("p", 1, 2, 7, 1, 50);

        assertThatThrownBy(() -> feasible.requireAtLeastOne(players))
                .isInstanceOfSatisfying(SquadRuleViolation.class, violation -> {
                    assertThat(violation.rule()).isEqualTo(SquadRule.NO_FEASIBLE_FORMATION);
                    assertThat(violation.getMessage()).contains("5-3-2, 4-5-1, 4-4-2, 3-5-2, 4-3-3");
                    assertThat(violation.arguments())
                            .containsExactly(List.of("5-3-2", "4-5-1", "4-4-2", "3-5-2", "4-3-3"));
                });
    }

    @Test
    void theRejectionListsTheFormationsOfTheCatalogInUse() {
        FeasibleFormations custom = new FeasibleFormations(new FormationCatalog(List.of(new Formation(4, 4, 2, 0))));
        List<Player> players = TestSquads.uniformPlayers("p", 1, 2, 7, 1, 50);

        assertThatThrownBy(() -> custom.requireAtLeastOne(players))
                .isInstanceOf(SquadRuleViolation.class)
                .hasMessageContaining("4-4-2")
                .hasMessageNotContaining("5-3-2");
    }
}
