package org.tasktelemetry.matchsimulator.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class TeamEnergyTest {

    private final Team team = TestTeams.uniformTeam("Home", 60);
    private final List<Player> players = team.starters();

    @Test
    void everyPlayerStartsWithFullEnergyInTheOrderOfTheList() {
        TeamEnergy energy = TeamEnergy.fullFor(players);

        assertThat(energy.energyByPlayer().keySet()).containsExactlyElementsOf(players);
        assertThat(energy.energyByPlayer().values()).containsOnly(100.0);
    }

    @Test
    void anUpdateChangesOnlyThePlayerAndKeepsTheOrder() {
        TeamEnergy full = TeamEnergy.fullFor(players);

        TeamEnergy tired = full.withEnergy(players.get(3), 42.5);

        assertThat(tired.energyOf(players.get(3))).isEqualTo(42.5);
        assertThat(tired.energyOf(players.get(4))).isEqualTo(100.0);
        assertThat(tired.energyByPlayer().keySet()).containsExactlyElementsOf(players);
        assertThat(full.energyOf(players.get(3))).isEqualTo(100.0);
    }

    @Test
    void aSubstituteTakesThePlaceOfWhoLeavesWithFullEnergy() {
        Player leaving = players.get(5);
        Player entering = team.bench().get(2);
        TeamEnergy tired = TeamEnergy.fullFor(players).withEnergy(leaving, 10.0);

        TeamEnergy changed = tired.withSubstitution(leaving, entering);

        assertThat(changed.energyByPlayer().keySet()).hasSize(players.size()).contains(entering)
                .doesNotContain(leaving);
        assertThat(changed.energyOf(entering)).isEqualTo(100.0);
        assertThat(List.copyOf(changed.energyByPlayer().keySet()).get(5)).isEqualTo(entering);
    }

    @Test
    void aPlayerWhoLeavesLosesHisEnergy() {
        Player leaving = players.get(5);

        TeamEnergy remaining = TeamEnergy.fullFor(players).without(leaving);

        assertThat(remaining.energyByPlayer()).hasSize(players.size() - 1).doesNotContainKey(leaving);
    }

    @Test
    void askingForAPlayerNotOnThePitchIsRejected() {
        TeamEnergy energy = TeamEnergy.fullFor(players);
        Player benched = team.bench().get(0);

        assertThatThrownBy(() -> energy.energyOf(benched)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not on the pitch");
        assertThatThrownBy(() -> energy.without(benched)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> energy.withSubstitution(benched, players.get(0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anEnergyOutsideZeroAndOneHundredIsRejected() {
        Player player = players.get(0);
        TeamEnergy full = TeamEnergy.fullFor(players);

        assertThatThrownBy(() -> full.withEnergy(player, -0.1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must be between");
        assertThatThrownBy(() -> full.withEnergy(player, 100.1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must be between");
        assertThatThrownBy(() -> full.withEnergy(player, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theMapIsACopyThatCannotBeChanged() {
        Map<Player, Double> source = new LinkedHashMap<>();
        source.put(players.get(0), 80.0);

        TeamEnergy energy = new TeamEnergy(source);
        source.put(players.get(1), 10.0);

        assertThat(energy.energyByPlayer()).hasSize(1);
        assertThatThrownBy(() -> energy.energyByPlayer().put(players.get(1), 10.0))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new TeamEnergy(null)).isInstanceOf(NullPointerException.class);
    }
}
