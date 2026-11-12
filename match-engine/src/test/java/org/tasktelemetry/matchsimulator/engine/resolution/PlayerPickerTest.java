package org.tasktelemetry.matchsimulator.engine.resolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.ZonePresenceConfig;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class PlayerPickerTest {

    private static final long SEED = 7L;
    private static final int TRIALS = 100_000;
    private static final double ONE_POINT = 0.01;

    private final ZonePresence presence = new ZonePresence(ZonePresenceConfig.defaults());

    @ParameterizedTest
    @EnumSource(ZoneRole.class)
    void thePickFrequencyOfEachPlayerIsItsPresenceOverTheSumOfThePresences(ZoneRole zoneRole) {
        TeamOnPitch team = TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 60));
        PlayerPicker picker = pickerWith(SEED);

        List<Player> picked = pickMany(picker, team, zoneRole, TRIALS);

        Map<Player, Long> picks = countPicks(picked);
        double totalPresence = team.players().stream()
                .mapToDouble(player -> presence.presenceOf(team, player, zoneRole))
                .sum();
        for (Player player : team.players()) {
            double expected = presence.presenceOf(team, player, zoneRole) / totalPresence;
            double frequency = (double) picks.getOrDefault(player, 0L) / TRIALS;
            assertThat(frequency).as(player.name()).isCloseTo(expected, within(ONE_POINT));
        }
    }

    @Test
    void aFourFourTwoInMidfieldPicksEachMidfielderSixteenPointSevenPercent() {
        TeamOnPitch team = TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 60));
        Player midfielder = team.playersIn(Role.MIDFIELDER).getFirst();
        Player defender = team.playersIn(Role.DEFENDER).getFirst();
        Player forward = team.playersIn(Role.FORWARD).getFirst();

        Map<Player, Long> picks = countPicks(pickMany(pickerWith(SEED), team, ZoneRole.MIDFIELD, TRIALS));

        assertThat(frequencyOf(picks, midfielder)).isCloseTo(1.0 / 6.0, within(ONE_POINT));
        assertThat(frequencyOf(picks, defender)).isCloseTo(0.3 / 6.0, within(ONE_POINT));
        assertThat(frequencyOf(picks, forward)).isCloseTo(0.4 / 6.0, within(ONE_POINT));
    }

    @Test
    void insideASectorThePickIsUniformWhateverTheQualityOfThePlayers() {
        TeamOnPitch team = TeamOnPitch.startingWith(teamWithMidfieldersOfQuality(20, 40, 60, 90));
        List<Player> midfielders = team.playersIn(Role.MIDFIELDER);

        Map<Player, Long> picks = countPicks(pickMany(pickerWith(SEED), team, ZoneRole.MIDFIELD, TRIALS));

        for (Player midfielder : midfielders) {
            assertThat(frequencyOf(picks, midfielder)).as(midfielder.name())
                    .isCloseTo(1.0 / 6.0, within(ONE_POINT));
        }
    }

    @ParameterizedTest
    @EnumSource(ZoneRole.class)
    void neverPicksTheGoalkeeperNorAPlayerWithZeroPresence(ZoneRole zoneRole) {
        TeamOnPitch team = TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 60));

        List<Player> picked = pickMany(pickerWith(SEED), team, zoneRole, 5_000);

        assertThat(picked).noneMatch(player -> team.roleOf(player) == Role.GOALKEEPER);
        assertThat(picked).allMatch(player -> presence.presenceOf(team, player, zoneRole) > 0.0);
    }

    @Test
    void aPlayerOfAForwardSectorIsNeverPickedInTheOwnBox() {
        TeamOnPitch team = TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 60));

        List<Player> picked = pickMany(pickerWith(SEED), team, ZoneRole.OWN_BOX, 5_000);

        assertThat(picked).noneMatch(player -> team.roleOf(player) == Role.FORWARD);
    }

    @Test
    void aPlayerMovedToAnotherSectorIsPickedWithTheWeightOfTheNewSector() {
        TeamOnPitch complete = TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 60));
        Player moved = complete.playersIn(Role.DEFENDER).getFirst();
        TeamOnPitch team = complete.withRole(moved, Role.MIDFIELDER);
        double totalPresence = 3 * 0.3 + 5 * 1.0 + 2 * 0.4;

        Map<Player, Long> picks = countPicks(pickMany(pickerWith(SEED), team, ZoneRole.MIDFIELD, TRIALS));

        assertThat(frequencyOf(picks, moved)).isCloseTo(1.0 / totalPresence, within(ONE_POINT));
    }

    @Test
    void failsNamingTheTeamAndTheZoneRoleWhenTheTotalWeightIsZero() {
        ZonePresenceConfig onlyDefendersInOwnBox = onlyDefendersInTheOwnBox();
        TeamOnPitch team = withoutDefenders(TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 60)));
        PlayerPicker picker = pickerWith(onlyDefendersInOwnBox, SEED);

        assertThatThrownBy(() -> picker.pickFrom(team, ZoneRole.OWN_BOX))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Home")
                .hasMessageContaining("OWN_BOX");
    }

    @Test
    void pickOtherThanNeverReturnsTheExcludedPlayer() {
        TeamOnPitch team = TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 60));
        Player excluded = team.playersIn(Role.FORWARD).getFirst();
        PlayerPicker picker = pickerWith(SEED);

        List<Player> picked = IntStream.range(0, 5_000)
                .mapToObj(index -> picker.pickOtherThan(team, ZoneRole.OPPONENT_BOX, excluded))
                .toList();

        assertThat(picked).doesNotContain(excluded);
        assertThat(picked.stream().distinct().count()).isGreaterThan(1);
    }

    @Test
    void pickOtherThanFailsWhenNoOtherPlayerHasPositiveWeight() {
        ZonePresenceConfig onlyDefendersInOwnBox = onlyDefendersInTheOwnBox();
        TeamOnPitch team = TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 60));
        Player soleDefender = team.playersIn(Role.DEFENDER).getFirst();
        TeamOnPitch withOneDefender = keepingOnlyOneDefender(team, soleDefender);
        PlayerPicker picker = pickerWith(onlyDefendersInOwnBox, SEED);

        assertThatThrownBy(() -> picker.pickOtherThan(withOneDefender, ZoneRole.OWN_BOX, soleDefender))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OWN_BOX")
                .hasMessageContaining(soleDefender.name());
    }

    @Test
    void theSameSeedPicksTheSamePlayers() {
        TeamOnPitch team = TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 60));

        List<Player> first = pickMany(pickerWith(SEED), team, ZoneRole.DEFENCE, 100);
        List<Player> second = pickMany(pickerWith(SEED), team, ZoneRole.DEFENCE, 100);

        assertThat(first).containsExactlyElementsOf(second);
    }

    @Test
    void eachPickConsumesExactlyOneValueOfTheGenerator() {
        TeamOnPitch team = TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 60));
        MatchRandom pickerRandom = new MatchRandom(SEED);
        MatchRandom reference = new MatchRandom(SEED);
        PlayerPicker picker = new PlayerPicker(pickerRandom, presence);
        int picks = 50;

        pickMany(picker, team, ZoneRole.MIDFIELD, picks);
        IntStream.range(0, picks).forEach(index -> reference.nextDouble());

        assertThat(pickerRandom.nextDouble()).isEqualTo(reference.nextDouble());
    }

    private PlayerPicker pickerWith(long seed) {
        return pickerWith(ZonePresenceConfig.defaults(), seed);
    }

    private PlayerPicker pickerWith(ZonePresenceConfig config, long seed) {
        return new PlayerPicker(new MatchRandom(seed), new ZonePresence(config));
    }

    private List<Player> pickMany(PlayerPicker picker, TeamOnPitch team, ZoneRole zoneRole, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> picker.pickFrom(team, zoneRole))
                .toList();
    }

    private Map<Player, Long> countPicks(List<Player> picked) {
        return picked.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private double frequencyOf(Map<Player, Long> picks, Player player) {
        long count = picks.getOrDefault(player, 0L);
        return (double) count / TRIALS;
    }

    private Team teamWithMidfieldersOfQuality(int first, int second, int third, int fourth) {
        PlayerAttributes others = TestTeams.uniform(50);
        List<Player> starters = new ArrayList<>(TestTeams.squad("Home", "S", others, 1, 4, 0, 2));
        int[] ratings = {first, second, third, fourth};
        for (int index = 0; index < ratings.length; index++) {
            String name = "Home-M-" + index;
            starters.add(TestTeams.player(name, Role.MIDFIELDER, TestTeams.uniform(ratings[index])));
        }
        return TestTeams.teamOf("Home", starters);
    }

    private ZonePresenceConfig onlyDefendersInTheOwnBox() {
        ZonePresenceConfig defaults = ZonePresenceConfig.defaults();
        Map<ZoneRole, Map<Role, Double>> table = new EnumMap<>(defaults.presenceByZoneRole());
        Map<Role, Double> row = new EnumMap<>(Role.class);
        row.put(Role.DEFENDER, 1.0);
        row.put(Role.MIDFIELDER, 0.0);
        row.put(Role.FORWARD, 0.0);
        table.put(ZoneRole.OWN_BOX, row);
        return new ZonePresenceConfig(table, defaults.referenceFormation(), defaults.advantageFactorPerMan(),
                defaults.advantageCap(), defaults.representativeWeight(), defaults.passMinimumPlayers());
    }

    private TeamOnPitch withoutDefenders(TeamOnPitch team) {
        TeamOnPitch reduced = team;
        for (Player defender : team.playersIn(Role.DEFENDER)) {
            reduced = reduced.withoutPlayer(defender);
        }
        return reduced;
    }

    private TeamOnPitch keepingOnlyOneDefender(TeamOnPitch team, Player kept) {
        TeamOnPitch reduced = team;
        for (Player defender : team.playersIn(Role.DEFENDER)) {
            if (!defender.equals(kept)) {
                reduced = reduced.withoutPlayer(defender);
            }
        }
        return reduced;
    }
}
