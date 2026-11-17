package org.tasktelemetry.matchsimulator.coach.lineup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.squad.Squad;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class TeamStrengthTest {

    private static final double EXACT = 1e-9;

    private final TeamStrength strength = new TeamStrength(new RosterRanking());

    private List<Player> twelveOutfieldPlayersAndTwoGoalkeepers() {
        int[] movements = {90, 80, 70, 60, 50, 40, 30, 20, 10, 5, 4, 3};
        List<Player> players = new ArrayList<>();
        players.add(TestSquads.goalkeeper("weak-keeper", 70, 100));
        players.add(TestSquads.goalkeeper("best-keeper", 90, 100));
        for (int index = 0; index < movements.length; index++) {
            players.add(TestSquads.outfield("o" + index, Role.MIDFIELDER, movements[index]));
        }
        return players;
    }

    @Test
    void theStrengthIsTheMeanOfTheTenBestMovementAveragesAndTheBestGoalkeepingIgnoringGoalkeepersMovement() {
        Squad squad = TestSquads.squad("A", twelveOutfieldPlayersAndTwoGoalkeepers());

        double result = strength.of(squad);

        assertThat(result).isCloseTo((455.0 + 90.0) / 11.0, within(EXACT));
    }

    @Test
    void theOrderOfTheRosterDoesNotChangeTheStrength() {
        List<Player> players = twelveOutfieldPlayersAndTwoGoalkeepers();
        Collections.reverse(players);
        Squad squad = TestSquads.squad("A", players);

        assertThat(strength.of(squad)).isCloseTo((455.0 + 90.0) / 11.0, within(EXACT));
    }

    @Test
    void outfieldPlayersBeyondTheTenBestDoNotCount() {
        List<Player> players = twelveOutfieldPlayersAndTwoGoalkeepers();
        players.add(TestSquads.outfield("extra-weak", Role.FORWARD, 1));
        Squad squad = TestSquads.squad("A", players);

        assertThat(strength.of(squad)).isCloseTo((455.0 + 90.0) / 11.0, within(EXACT));
    }

    @Test
    void aUniformSquadHasTheStrengthOfItsRating() {
        Squad squad = TestSquads.fullSquad("A", 62);

        assertThat(strength.of(squad)).isEqualTo(62.0);
    }

    @Test
    void movementAveragesAreNotRoundedBeforeTheMean() {
        List<Player> players = new ArrayList<>();
        players.add(TestSquads.goalkeeper("keeper", 66));
        players.addAll(TestSquads.uniformPlayers("o", 0, 10, 0, 0, 40));
        Squad squad = TestSquads.squad("A", players);

        assertThat(strength.of(squad)).isCloseTo((400.0 + 66.0) / 11.0, within(EXACT));
    }

    @Test
    void aSquadWithFewerThanTenOutfieldPlayersHasNoStrength() {
        List<Player> players = TestSquads.uniformPlayers("p", 2, 3, 3, 3, 50);
        Squad squad = TestSquads.squad("A", players);

        assertThatThrownBy(() -> strength.of(squad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a squad needs at least 10 outfield players to measure its strength, found 9");
    }
}
