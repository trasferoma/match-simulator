package org.tasktelemetry.matchsimulator.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

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
}
