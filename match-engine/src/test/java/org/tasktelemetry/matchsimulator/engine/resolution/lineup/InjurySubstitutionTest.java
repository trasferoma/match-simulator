package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.LineupConfig;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class InjurySubstitutionTest {

    private static final int STARTER_RATING = 60;

    private final LineupConfig config = LineupConfig.defaults();
    private final InjurySubstitution substitution = new InjurySubstitution(config);

    @Test
    void theFirstBenchPlayerOfTheSameRoleReplacesTheInjuredEvenWhenAnotherOneIsBetter() {
        Team team = teamWithBench(bench(player("BM", Role.MIDFIELDER, 90), player("BD1", Role.DEFENDER, 50),
                player("BD2", Role.DEFENDER, 95)));
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);

        Optional<Player> replacement = substitution.chooseReplacement(onPitch, find(team, "D2"));

        assertThat(replacement).contains(find(team, "BD1"));
    }

    @Test
    void withoutABenchPlayerOfTheSameRoleTheOutfielderWithTheHighestAverageReplacesTheInjured() {
        Team team = teamWithBench(bench(player("BM1", Role.MIDFIELDER, 60), player("BF", Role.FORWARD, 80),
                player("BM2", Role.MIDFIELDER, 70), player("BG", Role.GOALKEEPER, 99)));
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);

        Optional<Player> replacement = substitution.chooseReplacement(onPitch, find(team, "D2"));

        assertThat(replacement).contains(find(team, "BF"));
    }

    @Test
    void whenTheHighestAveragesTieTheFirstOnTheBenchWins() {
        Team team = teamWithBench(bench(player("BM1", Role.MIDFIELDER, 60), player("BF1", Role.FORWARD, 75),
                player("BF2", Role.FORWARD, 75)));
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);

        Optional<Player> replacement = substitution.chooseReplacement(onPitch, find(team, "D2"));

        assertThat(replacement).contains(find(team, "BF1"));
    }

    @Test
    void theReserveGoalkeeperNeverReplacesAnOutfielder() {
        Team team = teamWithBench(bench(player("BG", Role.GOALKEEPER, 99)));
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);

        Optional<Player> replacement = substitution.chooseReplacement(onPitch, find(team, "M1"));

        assertThat(replacement).isEmpty();
    }

    @Test
    void anEmptyBenchLeavesTheTeamInInferiority() {
        Team team = teamWithBench(List.of());
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);

        assertThat(substitution.chooseReplacement(onPitch, find(team, "F1"))).isEmpty();
    }

    @Test
    void anInjuredGoalkeeperIsReplacedByTheReserveGoalkeeperBeforeAnyOutfielder() {
        Team team = teamWithBench(bench(player("BF", Role.FORWARD, 99), player("BG", Role.GOALKEEPER, 40)));
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);

        Optional<Player> replacement = substitution.chooseReplacement(onPitch, find(team, "GK"));

        assertThat(replacement).contains(find(team, "BG"));
    }

    @Test
    void anInjuredGoalkeeperWithoutAReserveIsReplacedByTheBestOutfielderOnTheBench() {
        Team team = teamWithBench(bench(player("BM", Role.MIDFIELDER, 65), player("BD", Role.DEFENDER, 70)));
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);

        Optional<Player> replacement = substitution.chooseReplacement(onPitch, find(team, "GK"));

        assertThat(replacement).contains(find(team, "BD"));
    }

    @Test
    void theRoleOnThePitchCountsNotTheNaturalRole() {
        Team team = teamWithBench(bench(player("BM", Role.MIDFIELDER, 60), player("BD", Role.DEFENDER, 55)));
        Player midfielderPlayingAsDefender = find(team, "M1");
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team).withRole(midfielderPlayingAsDefender, Role.DEFENDER);

        Optional<Player> replacement = substitution.chooseReplacement(onPitch, midfielderPlayingAsDefender);

        assertThat(replacement).contains(find(team, "BD"));
    }

    @Test
    void noSubstituteIsChosenOnceTheLimitIsReached() {
        Team team = teamWithBench(bench(player("BD", Role.DEFENDER, 70)));
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        TeamOnPitch exhausted = withSubstitutionsMade(onPitch, config.maximumSubstitutions());
        TeamOnPitch oneLeft = withSubstitutionsMade(onPitch, config.maximumSubstitutions() - 1);

        assertThat(substitution.chooseReplacement(exhausted, find(team, "D2"))).isEmpty();
        assertThat(substitution.chooseReplacement(oneLeft, find(team, "D2"))).contains(find(team, "BD"));
    }

    @Test
    void aSubstituteWhoAlreadyEnteredAndLeftCannotComeBack() {
        Team team = teamWithBench(bench(player("BD1", Role.DEFENDER, 70), player("BD2", Role.DEFENDER, 65)));
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        TeamOnPitch afterFirst = substitution.substitute(onPitch, find(team, "D1"), find(team, "BD1"));

        Optional<Player> replacement = substitution.chooseReplacement(afterFirst, find(team, "BD1"));

        assertThat(replacement).contains(find(team, "BD2"));
        TeamOnPitch afterSecond = substitution.substitute(afterFirst, find(team, "BD1"), find(team, "BD2"));
        assertThat(substitution.chooseReplacement(afterSecond, find(team, "D2"))).isEmpty();
    }

    @Test
    void theReplacementTakesTheSlotAndTheRoleOfTheInjuredOutfielder() {
        Team team = teamWithBench(bench(player("BF", Role.FORWARD, 80)));
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        Player injured = find(team, "D2");

        TeamOnPitch changed = substitution.substitute(onPitch, injured, find(team, "BF"));

        int slot = onPitch.players().indexOf(injured);
        assertThat(changed.players().get(slot)).isEqualTo(find(team, "BF"));
        assertThat(changed.roleOf(find(team, "BF"))).isEqualTo(Role.DEFENDER);
        assertThat(changed.playersIn(Role.DEFENDER)).hasSize(4);
        assertThat(changed.players()).hasSize(11);
        assertThat(changed.substitutionsMade()).isEqualTo(1);
    }

    @Test
    void theReserveGoalkeeperEntersAsGoalkeeper() {
        Team team = teamWithBench(bench(player("BG", Role.GOALKEEPER, 40)));
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);

        TeamOnPitch changed = substitution.substitute(onPitch, find(team, "GK"), find(team, "BG"));

        assertThat(changed.goalkeeper()).isEqualTo(find(team, "BG"));
        assertThat(changed.players()).hasSize(11);
    }

    @Test
    void anOutfielderWhoReplacesTheGoalkeeperEntersInHisOwnRoleLeavingTheGoalUncovered() {
        Team team = teamWithBench(bench(player("BM", Role.MIDFIELDER, 80)));
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);

        TeamOnPitch changed = substitution.substitute(onPitch, find(team, "GK"), find(team, "BM"));

        assertThat(changed.roleOf(find(team, "BM"))).isEqualTo(Role.MIDFIELDER);
        assertThat(changed.playersIn(Role.GOALKEEPER)).isEmpty();
        assertThat(changed.players()).hasSize(11);
    }

    private TeamOnPitch withSubstitutionsMade(TeamOnPitch team, int substitutions) {
        return new TeamOnPitch(team.team(), team.players(), team.reassigned(), team.cautioned(), substitutions);
    }

    private List<Player> bench(Player... players) {
        return List.of(players);
    }

    private Team teamWithBench(List<Player> bench) {
        List<Player> starters = new ArrayList<>();
        starters.add(player("GK", Role.GOALKEEPER, STARTER_RATING));
        starters.addAll(outfield("D", Role.DEFENDER, 4));
        starters.addAll(outfield("M", Role.MIDFIELDER, 4));
        starters.addAll(outfield("F", Role.FORWARD, 2));
        return new Team("T", PlayStyle.BALANCED, starters, bench);
    }

    private List<Player> outfield(String prefix, Role role, int count) {
        List<Player> players = new ArrayList<>();
        for (int number = 1; number <= count; number++) {
            players.add(player(prefix + number, role, STARTER_RATING));
        }
        return players;
    }

    private Player player(String name, Role role, int rating) {
        return TestTeams.player(name, role, TestTeams.uniform(rating));
    }

    private Player find(Team team, String name) {
        List<Player> squad = new ArrayList<>(team.starters());
        squad.addAll(team.bench());
        return squad.stream()
                .filter(player -> player.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
