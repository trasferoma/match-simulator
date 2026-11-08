package org.tasktelemetry.matchsimulator.engine.resolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class PlayerPickerTest {

    private static final long SEED = 7L;
    private static final int TRIALS = 100_000;

    @ParameterizedTest
    @CsvSource({
            "OWN_BOX, DEFENDER",
            "DEFENCE, DEFENDER",
            "MIDFIELD, MIDFIELDER",
            "ATTACKING_THIRD, FORWARD",
            "OPPONENT_BOX, FORWARD"})
    void picksOnlyFromTheSectorOfTheZoneRole(ZoneRole zoneRole, Role expectedSector) {
        TeamOnPitch team = TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 60));
        PlayerPicker picker = new PlayerPicker(new MatchRandom(SEED));

        List<Player> picked = pickMany(picker, team, zoneRole, 500);

        assertThat(picked).allMatch(player -> player.role() == expectedSector);
        assertThat(picked.stream().distinct().count()).isGreaterThan(1);
    }

    @Test
    void aStrongerPlayerIsPickedMoreOftenInProportionToItsWeight() {
        Player strong = TestTeams.player("Strong", Role.FORWARD, TestTeams.uniform(80));
        Player weak = TestTeams.player("Weak", Role.FORWARD, TestTeams.uniform(20));
        TeamOnPitch team = TeamOnPitch.startingWith(teamWithForwards(strong, weak));
        PlayerPicker picker = new PlayerPicker(new MatchRandom(SEED));

        List<Player> picked = pickMany(picker, team, ZoneRole.OPPONENT_BOX, TRIALS);

        long strongPicks = picked.stream().filter(player -> player == strong).count();
        assertThat((double) strongPicks / TRIALS).isCloseTo(0.8, within(0.01));
    }

    @Test
    void theSameSeedPicksTheSamePlayers() {
        TeamOnPitch team = TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 60));

        List<Player> first = pickMany(new PlayerPicker(new MatchRandom(SEED)), team, ZoneRole.DEFENCE, 100);
        List<Player> second = pickMany(new PlayerPicker(new MatchRandom(SEED)), team, ZoneRole.DEFENCE, 100);

        assertThat(first).containsExactlyElementsOf(second);
    }

    private List<Player> pickMany(PlayerPicker picker, TeamOnPitch team, ZoneRole zoneRole, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> picker.pickFrom(team, zoneRole))
                .toList();
    }

    private Team teamWithForwards(Player first, Player second) {
        PlayerAttributes attributes = TestTeams.uniform(50);
        List<Player> starters = new ArrayList<>(TestTeams.squad("Home", "S", attributes, 1, 4, 4, 0));
        starters.add(first);
        starters.add(second);
        return TestTeams.teamOf("Home", starters);
    }
}
