package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.config.LineupConfig;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class LineupRepairTest {

    private static final int BEHIND = -1;
    private static final int LEVEL = 0;
    private static final int AHEAD = 2;

    private final LineupConfig config = LineupConfig.defaults();
    private final LineupRepair repair = new LineupRepair(config);

    @Test
    void aGoalkeeperSentOffWithAReserveAndSubstitutionsLeftIsReplacedByTheReserveAtTheSlotOfWhoLeaves() {
        Team team = shape(4, 4, 2, true);
        TeamOnPitch afterTheExpulsion = pitch(team, "GK");

        RepairedLineup repaired = repair.repairAfterDismissal(afterTheExpulsion, Role.GOALKEEPER, LEVEL);

        Player reserve = find(team, "RG");
        assertThat(repaired.team().goalkeeper()).isEqualTo(reserve);
        assertThat(repaired.team().players()).hasSize(10);
        assertThat(repaired.team().substitutionsMade()).isEqualTo(1);
        assertThat(repaired.substitutions()).hasSize(1);
        Substitution substitution = repaired.substitutions().get(0);
        assertThat(substitution.entering()).isEqualTo(reserve);
        assertThat(repaired.team().players()).doesNotContain(substitution.leaving());
        int slot = afterTheExpulsion.players().indexOf(substitution.leaving());
        assertThat(repaired.team().players().get(slot)).isEqualTo(reserve);
    }

    @Test
    void theReserveGoalkeeperDoesNotEnterWithoutSubstitutionsLeft() {
        Team team = shape(4, 4, 2, true);
        TeamOnPitch exhausted = withSubstitutionsMade(pitch(team, "GK"), config.maximumSubstitutions());

        RepairedLineup repaired = repair.repairAfterDismissal(exhausted, Role.GOALKEEPER, LEVEL);

        assertThat(repaired.substitutions()).isEmpty();
        assertThat(repaired.team().players()).doesNotContain(find(team, "RG"));
        assertThat(repaired.team().players()).hasSize(10);
        assertThat(repaired.team().substitutionsMade()).isEqualTo(config.maximumSubstitutions());
    }

    @Test
    void withoutAReserveGoalkeeperTheOutfielderWithTheBestGoalkeepingGoesInGoal() {
        Team team = shapeWithGoalkeepingSpecialist();
        TeamOnPitch afterTheExpulsion = pitch(team, "GK");

        RepairedLineup repaired = repair.repairAfterDismissal(afterTheExpulsion, Role.GOALKEEPER, LEVEL);

        Player specialist = find(team, "M1");
        assertThat(repaired.substitutions()).isEmpty();
        assertThat(repaired.team().goalkeeper()).isEqualTo(specialist);
        assertThat(repaired.team().roleOf(specialist)).isEqualTo(Role.GOALKEEPER);
        assertThat(repaired.team().players()).hasSize(10);
        assertThat(repaired.team().playersIn(Role.MIDFIELDER)).hasSize(3).doesNotContain(specialist);
    }

    @Test
    void withTheSamePlayersTheBestGoalkeepingIsNotTheBestAverage() {
        Team team = shapeWithGoalkeepingSpecialist();
        TeamOnPitch afterTheExpulsion = pitch(team, "GK");

        RepairedLineup repaired = repair.repairAfterDismissal(afterTheExpulsion, Role.GOALKEEPER, LEVEL);

        Player highestAverage = find(team, "F2");
        assertThat(repaired.team().goalkeeper()).isNotEqualTo(highestAverage);
    }

    @Test
    void goalkeepingTiesGoToTheFirstInTheOrderOnThePitch() {
        List<Player> starters = new ArrayList<>();
        starters.add(player("GK", Role.GOALKEEPER, 65));
        starters.addAll(outfield("D", Role.DEFENDER, 4, 60));
        starters.add(new Player("MA", Role.MIDFIELDER, new PlayerAttributes(50, 50, 50, 50, 50, 50, 50, 90)));
        starters.add(new Player("MB", Role.MIDFIELDER, new PlayerAttributes(50, 50, 50, 50, 50, 50, 50, 90)));
        starters.add(player("MC", Role.MIDFIELDER, 52));
        starters.add(player("MD", Role.MIDFIELDER, 53));
        starters.addAll(outfield("F", Role.FORWARD, 2, 70));
        Team team = new Team("T", PlayStyle.BALANCED, starters, List.of());
        TeamOnPitch afterTheExpulsion = pitch(team, "GK");

        RepairedLineup repaired = repair.repairAfterDismissal(afterTheExpulsion, Role.GOALKEEPER, LEVEL);

        assertThat(repaired.team().goalkeeper()).isEqualTo(find(team, "MA"));
    }

    @Test
    void whenBehindWithMoreDefendersThanMidfieldersTheLowestAverageDefenderLeaves() {
        Team team = shape(4, 4, 2, true);
        TeamOnPitch afterTwoExpulsions = pitch(team, "GK", "M4");

        RepairedLineup repaired = repair.repairAfterDismissal(afterTwoExpulsions, Role.GOALKEEPER, BEHIND);

        assertThat(leaver(repaired)).isEqualTo(find(team, "D1"));
    }

    @Test
    void whenBehindWithDefenceAndMidfieldLevelTheMidfielderWithTheLowestAverageLeaves() {
        Team team = shape(4, 4, 2, true);
        TeamOnPitch afterTheExpulsion = pitch(team, "GK");

        RepairedLineup repaired = repair.repairAfterDismissal(afterTheExpulsion, Role.GOALKEEPER, BEHIND);

        assertThat(leaver(repaired)).isEqualTo(find(team, "M1"));
    }

    @Test
    void whenBehindWithTheAttackTheMostNumerousSectorTwoTwoThreeALowestAverageMidfielderLeavesNotAForward() {
        Team team = shape(3, 4, 3, true);
        TeamOnPitch twoTwoThree = pitch(team, "GK", "D3", "M3", "M4");

        RepairedLineup repaired = repair.repairAfterDismissal(twoTwoThree, Role.GOALKEEPER, BEHIND);

        assertThat(twoTwoThree.playersIn(Role.FORWARD)).hasSize(3);
        assertThat(leaver(repaired)).isEqualTo(find(team, "M1"));
    }

    @Test
    void whenBehindWithThreeThreeThreeALowestAverageMidfielderLeavesNotAForward() {
        Team team = shape(3, 4, 3, true);
        TeamOnPitch threeThreeThree = pitch(team, "GK", "M4");

        RepairedLineup repaired = repair.repairAfterDismissal(threeThreeThree, Role.GOALKEEPER, BEHIND);

        assertThat(threeThreeThree.playersIn(Role.FORWARD)).hasSize(3);
        assertThat(leaver(repaired)).isEqualTo(find(team, "M1"));
    }

    @Test
    void whenBehindWithOnlyOneDefenderAndOneMidfielderTheLowestAverageForwardLeaves() {
        Team team = shape(3, 4, 3, true);
        TeamOnPitch oneOneThree = pitch(team, "GK", "D2", "D3", "M2", "M3", "M4");

        RepairedLineup repaired = repair.repairAfterDismissal(oneOneThree, Role.GOALKEEPER, BEHIND);

        assertThat(leaver(repaired)).isEqualTo(find(team, "F1"));
    }

    @ParameterizedTest
    @ValueSource(ints = {LEVEL, AHEAD})
    void whenLevelOrAheadTheMostNumerousSectorLoses(int lead) {
        Team team = shape(5, 3, 2, true);
        TeamOnPitch afterTheExpulsion = pitch(team, "GK");

        RepairedLineup repaired = repair.repairAfterDismissal(afterTheExpulsion, Role.GOALKEEPER, lead);

        assertThat(leaver(repaired)).isEqualTo(find(team, "D1"));
    }

    @ParameterizedTest
    @ValueSource(ints = {LEVEL, AHEAD})
    void whenLevelOrAheadSectorsOfTheSameSizeAreTriedInTheOrderAttackMidfieldDefence(int lead) {
        Team team = shape(3, 4, 3, true);
        TeamOnPitch threeThreeThree = pitch(team, "GK", "M4");
        TeamOnPitch fourFourTwo = pitch(shape(4, 4, 2, true), "GK");
        TeamOnPitch twoTwoThree = pitch(team, "GK", "D3", "M3", "M4");

        RepairedLineup attackFirst = repair.repairAfterDismissal(threeThreeThree, Role.GOALKEEPER, lead);
        RepairedLineup midfieldBeforeDefence = repair.repairAfterDismissal(fourFourTwo, Role.GOALKEEPER, lead);
        RepairedLineup attackBeatsTheRest = repair.repairAfterDismissal(twoTwoThree, Role.GOALKEEPER, lead);

        assertThat(leaver(attackFirst)).isEqualTo(find(team, "F1"));
        assertThat(leaver(midfieldBeforeDefence).role()).isEqualTo(Role.MIDFIELDER);
        assertThat(leaver(attackBeatsTheRest)).isEqualTo(find(team, "F1"));
    }

    @Test
    void anOutfielderSentOffFromASectorThatKeepsPlayersChangesNothing() {
        Team team = shape(4, 4, 2, true);
        TeamOnPitch afterTheExpulsion = pitch(team, "D2");

        RepairedLineup repaired = repair.repairAfterDismissal(afterTheExpulsion, Role.DEFENDER, BEHIND);

        assertThat(repaired.team()).isEqualTo(afterTheExpulsion);
        assertThat(repaired.substitutions()).isEmpty();
    }

    @Test
    void anEmptiedDefenceIsFilledByTheMidfielderWithTheHighestAverage() {
        Team team = shape(4, 4, 2, true);
        TeamOnPitch noDefenders = pitch(team, "D1", "D2", "D3", "D4");

        RepairedLineup repaired = repair.repairAfterDismissal(noDefenders, Role.DEFENDER, LEVEL);

        Player mover = find(team, "M4");
        assertThat(repaired.team().playersIn(Role.DEFENDER)).containsExactly(mover);
        assertThat(repaired.team().playersIn(Role.MIDFIELDER)).hasSize(3);
        assertThat(repaired.substitutions()).isEmpty();
    }

    @Test
    void anEmptiedAttackIsFilledByTheMidfielderWithTheHighestAverage() {
        Team team = shape(4, 4, 2, true);
        TeamOnPitch noForwards = pitch(team, "F1", "F2");

        RepairedLineup repaired = repair.repairAfterDismissal(noForwards, Role.FORWARD, LEVEL);

        assertThat(repaired.team().playersIn(Role.FORWARD)).containsExactly(find(team, "M4"));
        assertThat(repaired.team().playersIn(Role.MIDFIELDER)).hasSize(3);
    }

    @Test
    void anEmptiedMidfieldIsFilledFromTheMoreNumerousAdjacentSector() {
        Team team = shape(4, 4, 2, true);
        TeamOnPitch noMidfielders = pitch(team, "M1", "M2", "M3", "M4");

        RepairedLineup repaired = repair.repairAfterDismissal(noMidfielders, Role.MIDFIELDER, LEVEL);

        assertThat(repaired.team().playersIn(Role.MIDFIELDER)).containsExactly(find(team, "D4"));
        assertThat(repaired.team().playersIn(Role.DEFENDER)).hasSize(3);
        assertThat(repaired.team().playersIn(Role.FORWARD)).hasSize(2);
    }

    @Test
    void anEmptiedMidfieldWithEqualAdjacentSectorsIsFilledFromTheDefence() {
        Team team = shape(3, 4, 3, true);
        TeamOnPitch noMidfielders = pitch(team, "M1", "M2", "M3", "M4");

        RepairedLineup repaired = repair.repairAfterDismissal(noMidfielders, Role.MIDFIELDER, LEVEL);

        assertThat(repaired.team().playersIn(Role.MIDFIELDER)).containsExactly(find(team, "D3"));
        assertThat(repaired.team().playersIn(Role.FORWARD)).hasSize(3);
    }

    @Test
    void anEmptiedMidfieldIsFilledFromTheAttackWhenTheAttackIsMoreNumerous() {
        Team team = shape(2, 4, 4, true);
        TeamOnPitch noMidfielders = pitch(team, "M1", "M2", "M3", "M4");

        RepairedLineup repaired = repair.repairAfterDismissal(noMidfielders, Role.MIDFIELDER, LEVEL);

        assertThat(repaired.team().playersIn(Role.MIDFIELDER)).containsExactly(find(team, "F4"));
        assertThat(repaired.team().playersIn(Role.FORWARD)).hasSize(3);
    }

    @Test
    void whoMovesIsTheHighestAverageAndATieGoesToTheFirstInTheOrderOnThePitch() {
        List<Player> starters = new ArrayList<>();
        starters.add(player("GK", Role.GOALKEEPER, 65));
        starters.addAll(outfield("D", Role.DEFENDER, 4, 60));
        starters.add(player("MA", Role.MIDFIELDER, 55));
        starters.add(player("MB", Role.MIDFIELDER, 70));
        starters.add(player("MC", Role.MIDFIELDER, 70));
        starters.add(player("MD", Role.MIDFIELDER, 55));
        starters.addAll(outfield("F", Role.FORWARD, 2, 70));
        Team team = new Team("T", PlayStyle.BALANCED, starters, List.of());
        TeamOnPitch noForwards = pitch(team, "F1", "F2");

        RepairedLineup repaired = repair.repairAfterDismissal(noForwards, Role.FORWARD, LEVEL);

        assertThat(repaired.team().playersIn(Role.FORWARD)).containsExactly(find(team, "MB"));
    }

    @Test
    void theLowestAverageThatLeavesIsTheFirstInTheOrderOnThePitchWhenAverageTie() {
        List<Player> starters = new ArrayList<>();
        starters.add(player("GK", Role.GOALKEEPER, 65));
        starters.addAll(outfield("D", Role.DEFENDER, 4, 60));
        starters.add(player("MA", Role.MIDFIELDER, 40));
        starters.add(player("MB", Role.MIDFIELDER, 40));
        starters.add(player("MC", Role.MIDFIELDER, 70));
        starters.add(player("MD", Role.MIDFIELDER, 70));
        starters.addAll(outfield("F", Role.FORWARD, 2, 70));
        List<Player> bench = List.of(player("RG", Role.GOALKEEPER, 40));
        Team team = new Team("T", PlayStyle.BALANCED, starters, bench);
        TeamOnPitch afterTheExpulsion = pitch(team, "GK");

        RepairedLineup repaired = repair.repairAfterDismissal(afterTheExpulsion, Role.GOALKEEPER, LEVEL);

        assertThat(leaver(repaired)).isEqualTo(find(team, "MA"));
    }

    @Test
    void theRepairLeavesTheOriginalTeamUntouched() {
        Team team = shape(4, 4, 2, true);
        TeamOnPitch afterTheExpulsion = pitch(team, "GK");
        List<Player> before = afterTheExpulsion.players();

        repair.repairAfterDismissal(afterTheExpulsion, Role.GOALKEEPER, LEVEL);

        assertThat(afterTheExpulsion.players()).containsExactlyElementsOf(before);
        assertThat(afterTheExpulsion.substitutionsMade()).isZero();
    }

    private Player leaver(RepairedLineup repaired) {
        assertThat(repaired.substitutions()).hasSize(1);
        return repaired.substitutions().get(0).leaving();
    }

    private TeamOnPitch withSubstitutionsMade(TeamOnPitch team, int substitutions) {
        return new TeamOnPitch(team.team(), team.players(), team.reassigned(), team.cautioned(), substitutions);
    }

    private TeamOnPitch pitch(Team team, String... leaving) {
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        for (String name : leaving) {
            onPitch = onPitch.withoutPlayer(find(team, name));
        }
        return onPitch;
    }

    private Player find(Team team, String name) {
        List<Player> squad = new ArrayList<>(team.starters());
        squad.addAll(team.bench());
        return squad.stream()
                .filter(player -> player.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private Team shape(int defenders, int midfielders, int forwards, boolean reserveGoalkeeper) {
        List<Player> starters = new ArrayList<>();
        starters.add(player("GK", Role.GOALKEEPER, 65));
        starters.addAll(outfield("D", Role.DEFENDER, defenders, 60));
        starters.addAll(outfield("M", Role.MIDFIELDER, midfielders, 50));
        starters.addAll(outfield("F", Role.FORWARD, forwards, 70));
        List<Player> bench = new ArrayList<>();
        bench.add(player("BD", Role.DEFENDER, 55));
        if (reserveGoalkeeper) {
            bench.add(player("RG", Role.GOALKEEPER, 40));
        }
        return new Team("T", PlayStyle.BALANCED, starters, bench);
    }

    private Team shapeWithGoalkeepingSpecialist() {
        List<Player> starters = new ArrayList<>();
        starters.add(player("GK", Role.GOALKEEPER, 65));
        starters.addAll(outfield("D", Role.DEFENDER, 4, 60));
        starters.add(new Player("M1", Role.MIDFIELDER, new PlayerAttributes(50, 50, 50, 50, 50, 50, 50, 95)));
        starters.addAll(outfield("M", Role.MIDFIELDER, 3, 51).stream().map(this::renumber).toList());
        starters.addAll(outfield("F", Role.FORWARD, 2, 70));
        return new Team("T", PlayStyle.BALANCED, starters, List.of(player("BD", Role.DEFENDER, 55)));
    }

    private Player renumber(Player player) {
        String shifted = "M" + (Integer.parseInt(player.name().substring(1)) + 1);
        return new Player(shifted, player.role(), player.attributes());
    }

    private List<Player> outfield(String prefix, Role role, int count, int firstRating) {
        List<Player> players = new ArrayList<>();
        for (int number = 1; number <= count; number++) {
            players.add(player(prefix + number, role, firstRating + number - 1));
        }
        return players;
    }

    private Player player(String name, Role role, int rating) {
        PlayerAttributes attributes = new PlayerAttributes(rating, rating, rating, rating, rating, rating, rating,
                rating);
        return new Player(name, role, attributes);
    }
}
