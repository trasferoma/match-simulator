package org.tasktelemetry.matchsimulator.engine.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.tasktelemetry.matchsimulator.engine.team.TeamFixtures.lineup;
import static org.tasktelemetry.matchsimulator.engine.team.TeamFixtures.player;
import static org.tasktelemetry.matchsimulator.engine.team.TeamFixtures.standardBench;
import static org.tasktelemetry.matchsimulator.engine.team.TeamFixtures.standardStarters;
import static org.tasktelemetry.matchsimulator.engine.team.TeamFixtures.teamWithBench;
import static org.tasktelemetry.matchsimulator.engine.team.TeamFixtures.teamWithStarters;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class TeamTest {

    @Test
    void acceptsAValidTeamAndKeepsStartersAndBenchInOrder() {
        List<Player> starters = standardStarters();
        List<Player> bench = standardBench();

        Team team = new Team("Home", PlayStyle.ATTACKING, starters, bench);

        assertThat(team.starters()).containsExactlyElementsOf(starters);
        assertThat(team.bench()).containsExactlyElementsOf(bench);
        assertThat(team.style()).isEqualTo(PlayStyle.ATTACKING);
    }

    @Test
    void acceptsAnEmptyBench() {
        Team team = teamWithBench(List.of());

        assertThat(team.bench()).isEmpty();
    }

    @Test
    void rejectsFewerThanElevenStarters() {
        List<Player> tenStarters = lineup("H", 1, 4, 3, 2);

        assertThatThrownBy(() -> teamWithStarters(tenStarters))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 11 starters, found 10");
    }

    @Test
    void rejectsMoreThanElevenStarters() {
        List<Player> twelveStarters = lineup("H", 1, 4, 4, 3);

        assertThatThrownBy(() -> teamWithStarters(twelveStarters))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 11 starters, found 12");
    }

    @Test
    void rejectsStartersWithoutAGoalkeeper() {
        List<Player> noGoalkeeper = lineup("H", 0, 5, 4, 2);

        assertThatThrownBy(() -> teamWithStarters(noGoalkeeper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one goalkeeper among the starters, found 0");
    }

    @Test
    void rejectsStartersWithTwoGoalkeepers() {
        List<Player> twoGoalkeepers = lineup("H", 2, 3, 4, 2);

        assertThatThrownBy(() -> teamWithStarters(twoGoalkeepers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one goalkeeper among the starters, found 2");
    }

    @Test
    void rejectsStartersWithoutDefenders() {
        List<Player> noDefenders = lineup("H", 1, 0, 6, 4);

        assertThatThrownBy(() -> teamWithStarters(noDefenders))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one defender among the starters");
    }

    @Test
    void rejectsStartersWithoutMidfielders() {
        List<Player> noMidfielders = lineup("H", 1, 6, 0, 4);

        assertThatThrownBy(() -> teamWithStarters(noMidfielders))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one midfielder among the starters");
    }

    @Test
    void rejectsStartersWithoutForwards() {
        List<Player> noForwards = lineup("H", 1, 5, 5, 0);

        assertThatThrownBy(() -> teamWithStarters(noForwards))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one forward among the starters");
    }

    @Test
    void rejectsANameRepeatedAmongTheStarters() {
        List<Player> starters = new ArrayList<>(standardStarters());
        String repeatedName = starters.get(0).name();
        starters.set(1, player(repeatedName, Role.DEFENDER));

        assertThatThrownBy(() -> teamWithStarters(starters))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique among starters and bench, repeated: " + repeatedName);
    }

    @Test
    void rejectsANameRepeatedBetweenStarterAndBench() {
        String starterName = standardStarters().get(3).name();
        List<Player> bench = List.of(player(starterName, Role.DEFENDER));

        assertThatThrownBy(() -> teamWithBench(bench))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique among starters and bench, repeated: " + starterName);
    }

    @Test
    void rejectsANameRepeatedWithinTheBench() {
        List<Player> bench = List.of(player("Spare", Role.DEFENDER), player("Spare", Role.FORWARD));

        assertThatThrownBy(() -> teamWithBench(bench))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repeated: Spare");
    }

    @Test
    void rejectsABlankTeamName() {
        assertThatThrownBy(() -> new Team(" ", PlayStyle.BALANCED, standardStarters(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("team name must not be blank");
    }

    @Test
    void rejectsMissingParts() {
        List<Player> starters = standardStarters();

        assertThatThrownBy(() -> new Team(null, PlayStyle.BALANCED, starters, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Team("Home", null, starters, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Team("Home", PlayStyle.BALANCED, null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Team("Home", PlayStyle.BALANCED, starters, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void isNotAffectedByLaterChangesToTheListsItWasBuiltFrom() {
        List<Player> starters = new ArrayList<>(standardStarters());
        List<Player> bench = new ArrayList<>(standardBench());
        Team team = new Team("Home", PlayStyle.BALANCED, starters, bench);

        starters.clear();
        bench.clear();

        assertThat(team.starters()).hasSize(11);
        assertThat(team.bench()).hasSize(7);
    }

    @Test
    void rejectsAPlayerWithABlankName() {
        PlayerAttributes attributes = new PlayerAttributes(50, 50, 50, 50, 50, 50, 50, 50);

        assertThatThrownBy(() -> new Player("  ", Role.FORWARD, attributes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("player name must not be blank");
    }
}
