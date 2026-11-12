package org.tasktelemetry.matchsimulator.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class TeamOnPitchTest {

    private final Team team = TestTeams.uniformTeam("Home", 60);

    @Test
    void startsWithTheEleven() {
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);

        assertThat(onPitch.players()).containsExactlyElementsOf(team.starters());
    }

    @Test
    void listsThePlayersOfARoleInTheStartersOrder() {
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);

        assertThat(onPitch.playersIn(Role.GOALKEEPER)).hasSize(1);
        assertThat(onPitch.playersIn(Role.DEFENDER)).hasSize(4).allMatch(player -> player.role() == Role.DEFENDER);
        assertThat(onPitch.playersIn(Role.MIDFIELDER)).hasSize(4);
        assertThat(onPitch.playersIn(Role.FORWARD)).hasSize(2);
        Player firstDefender = onPitch.playersIn(Role.DEFENDER).get(0);
        assertThat(team.starters()).filteredOn(player -> player.role() == Role.DEFENDER).startsWith(firstDefender);
    }

    @Test
    void theGoalkeeperIsTheStarterWithTheGoalkeeperRole() {
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);

        assertThat(onPitch.goalkeeper().role()).isEqualTo(Role.GOALKEEPER);
        assertThat(team.starters()).contains(onPitch.goalkeeper());
    }

    @Test
    void withoutAGoalkeeperOnThePitchThereIsNoGoalkeeperToAsk() {
        List<Player> outfield = team.starters().stream()
                .filter(player -> player.role() != Role.GOALKEEPER)
                .toList();
        TeamOnPitch onPitch = new TeamOnPitch(team, outfield);

        assertThatThrownBy(onPitch::goalkeeper)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no goalkeeper on the pitch");
    }

    @Test
    void rejectsMissingValues() {
        assertThatThrownBy(() -> new TeamOnPitch(null, team.starters())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TeamOnPitch(team, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void aPlayerSentOffLeavesThePitchWithoutAReplacementAndKeepsTheOrderOfTheOthers() {
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        Player sentOff = onPitch.playersIn(Role.DEFENDER).get(1);

        TeamOnPitch reduced = onPitch.withoutPlayer(sentOff);

        assertThat(reduced.players()).hasSize(10).doesNotContain(sentOff);
        assertThat(reduced.players()).containsExactlyElementsOf(
                team.starters().stream().filter(player -> !player.equals(sentOff)).toList());
        assertThat(reduced.substitutionsMade()).isZero();
        assertThat(onPitch.players()).hasSize(11);
    }

    @Test
    void aPlayerMovedToAnotherSectorCountsInTheNewSectorOnly() {
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        Player midfielder = onPitch.playersIn(Role.MIDFIELDER).get(0);

        TeamOnPitch moved = onPitch.withRole(midfielder, Role.DEFENDER);

        assertThat(moved.roleOf(midfielder)).isEqualTo(Role.DEFENDER);
        assertThat(moved.playersIn(Role.DEFENDER)).hasSize(5).contains(midfielder);
        assertThat(moved.playersIn(Role.MIDFIELDER)).hasSize(3).doesNotContain(midfielder);
        assertThat(moved.players()).containsExactlyElementsOf(onPitch.players());
    }

    @Test
    void aPlayerIsOutOfPositionWhenHisRoleOnThePitchIsNotTheOneOfHisRegistration() {
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        Player midfielder = onPitch.playersIn(Role.MIDFIELDER).get(0);
        Player forward = onPitch.playersIn(Role.FORWARD).get(0);

        TeamOnPitch moved = onPitch.withRole(midfielder, Role.DEFENDER);

        assertThat(moved.isOutOfPosition(midfielder)).isTrue();
        assertThat(moved.isOutOfPosition(forward)).isFalse();
        assertThat(onPitch.isOutOfPosition(midfielder)).isFalse();
        assertThat(moved.withRole(midfielder, Role.MIDFIELDER).isOutOfPosition(midfielder)).isFalse();
    }

    @Test
    void aSubstituteTakesTheSlotOfWhoLeavesAndCountsAsAChange() {
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        Player leaving = onPitch.playersIn(Role.MIDFIELDER).get(2);
        Player entering = team.bench().stream().filter(player -> player.role() == Role.GOALKEEPER).findFirst()
                .orElseThrow();

        TeamOnPitch changed = onPitch.withSubstitution(leaving, entering, Role.GOALKEEPER);

        int slot = onPitch.players().indexOf(leaving);
        assertThat(changed.players().get(slot)).isEqualTo(entering);
        assertThat(changed.players()).hasSize(11).doesNotContain(leaving);
        assertThat(changed.substitutionsMade()).isEqualTo(1);
        assertThat(changed.playersIn(Role.GOALKEEPER)).hasSize(2);
        assertThat(changed.availableBench()).doesNotContain(entering);
    }

    @Test
    void aSubstituteWhoEnteredAndThenLeftIsNoLongerOnTheBench() {
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        Player leaving = onPitch.playersIn(Role.DEFENDER).get(0);
        Player entering = team.bench().stream().filter(player -> player.role() == Role.DEFENDER).findFirst()
                .orElseThrow();
        TeamOnPitch changed = onPitch.withSubstitution(leaving, entering, Role.DEFENDER);

        TeamOnPitch afterLeavingAgain = changed.withoutPlayer(entering);

        assertThat(afterLeavingAgain.availableBench()).doesNotContain(entering);
        assertThat(afterLeavingAgain.availableBench()).hasSize(team.bench().size() - 1);
    }

    @Test
    void aSubstituteWhoWasSubstitutedInTurnDoesNotComeBackEither() {
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        Player first = team.bench().stream().filter(player -> player.role() == Role.MIDFIELDER).findFirst()
                .orElseThrow();
        Player second = team.bench().stream().filter(player -> player.role() == Role.FORWARD).findFirst()
                .orElseThrow();
        TeamOnPitch changed = onPitch.withSubstitution(onPitch.playersIn(Role.MIDFIELDER).get(0), first,
                Role.MIDFIELDER);

        TeamOnPitch changedAgain = changed.withSubstitution(first, second, Role.MIDFIELDER);

        assertThat(changedAgain.availableBench()).doesNotContain(first, second);
        assertThat(changedAgain.substitutionsMade()).isEqualTo(2);
    }

    @Test
    void aPlayerWhoLeftTheStartersIsNotAddedToTheBench() {
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);

        TeamOnPitch reduced = onPitch.withoutPlayer(onPitch.players().get(3));

        assertThat(reduced.availableBench()).containsExactlyElementsOf(team.bench());
    }

    @Test
    void theGoalkeeperIsTheFirstPlayerCountingAsGoalkeeperEvenWhenHisRoleIsAnother() {
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        Player midfielder = onPitch.playersIn(Role.MIDFIELDER).get(0);
        TeamOnPitch withoutKeeper = onPitch.withoutPlayer(onPitch.goalkeeper());

        TeamOnPitch covered = withoutKeeper.withRole(midfielder, Role.GOALKEEPER);

        assertThat(covered.goalkeeper()).isEqualTo(midfielder);
    }

    @Test
    void aCautionedPlayerIsRememberedUntilHeLeavesThePitch() {
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        Player booked = onPitch.playersIn(Role.FORWARD).get(0);
        Player other = onPitch.playersIn(Role.FORWARD).get(1);

        TeamOnPitch cautioned = onPitch.withCautioned(booked);

        assertThat(cautioned.isCautioned(booked)).isTrue();
        assertThat(cautioned.isCautioned(other)).isFalse();
        assertThat(onPitch.isCautioned(booked)).isFalse();
        assertThat(cautioned.withoutPlayer(booked).cautioned()).isEmpty();
    }

    @Test
    void theBenchAvailableIsTheBenchWithoutWhoAlreadyCameOn() {
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        Player entering = team.bench().get(0);
        Player leaving = onPitch.playersIn(Role.FORWARD).get(0);

        TeamOnPitch changed = onPitch.withSubstitution(leaving, entering, entering.role());

        assertThat(onPitch.availableBench()).containsExactlyElementsOf(team.bench());
        assertThat(changed.availableBench()).hasSize(team.bench().size() - 1).doesNotContain(entering);
    }

    @Test
    void refusesToChangeSomeoneWhoIsNotOnThePitch() {
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        Player benchPlayer = team.bench().get(0);

        assertThatThrownBy(() -> onPitch.withoutPlayer(benchPlayer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not on the pitch");
        assertThatThrownBy(() -> onPitch.withRole(benchPlayer, Role.DEFENDER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> onPitch.withCautioned(benchPlayer)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesANegativeNumberOfSubstitutions() {
        assertThatThrownBy(() -> new TeamOnPitch(team, team.starters(), Map.of(), List.of(), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
