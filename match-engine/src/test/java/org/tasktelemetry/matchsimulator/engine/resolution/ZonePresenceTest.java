package org.tasktelemetry.matchsimulator.engine.resolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.ZonePresenceConfig;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class ZonePresenceTest {

    private static final double EXACT = 1e-9;
    private static final int KEEPER_INDEX = 0;
    private static final int FIRST_DEFENDER_INDEX = 1;
    private static final int FIRST_MIDFIELDER_INDEX = 5;
    private static final int FIRST_FORWARD_INDEX = 9;

    private final ZonePresenceConfig config = ZonePresenceConfig.defaults();
    private final ZonePresence presence = new ZonePresence(config);

    @Test
    void theRepresentativeWeightIsTheConfiguredOne() {
        assertThat(presence.representativeWeight()).isEqualTo(config.representativeWeight());
    }

    @ParameterizedTest
    @EnumSource(ZoneRole.class)
    void theGoalkeeperHasNoPresenceInAnyZone(ZoneRole zoneRole) {
        TeamOnPitch team = fourFourTwo("Home");
        Player keeper = team.players().get(KEEPER_INDEX);

        assertThat(presence.presenceOf(team, keeper, zoneRole)).isEqualTo(0.0);
    }

    @Test
    void thePresenceOfAPlayerIsTheTableValueOfTheirRoleInThatZone() {
        TeamOnPitch team = fourFourTwo("Home");
        Player defender = team.players().get(FIRST_DEFENDER_INDEX);
        Player midfielder = team.players().get(FIRST_MIDFIELDER_INDEX);
        Player forward = team.players().get(FIRST_FORWARD_INDEX);

        assertThat(presence.presenceOf(team, defender, ZoneRole.DEFENCE)).isEqualTo(0.9);
        assertThat(presence.presenceOf(team, midfielder, ZoneRole.ATTACKING_THIRD)).isEqualTo(0.5);
        assertThat(presence.presenceOf(team, forward, ZoneRole.OWN_BOX)).isEqualTo(0.0);
    }

    @Test
    void aPlayerMovedToAnotherSectorCountsInTheNewOne() {
        TeamOnPitch team = fourFourTwo("Home");
        Player defender = team.players().get(FIRST_DEFENDER_INDEX);
        TeamOnPitch moved = team.withRole(defender, Role.FORWARD);

        assertThat(presence.presenceOf(moved, defender, ZoneRole.ATTACKING_THIRD)).isEqualTo(0.9);
        assertThat(presence.menInZone(moved, ZoneRole.ATTACKING_THIRD)).isCloseTo(5.0, within(EXACT));
    }

    @Test
    void thePlayersPresentInAZoneAreThoseOfAPositiveSectorPresenceInTheOrderOnThePitch() {
        TeamOnPitch team = fourFourTwo("Home");
        List<Player> expectedInOwnBox = team.players().subList(FIRST_DEFENDER_INDEX, FIRST_FORWARD_INDEX);

        List<Player> inOwnBox = presence.playersPresentIn(team, ZoneRole.OWN_BOX);

        assertThat(inOwnBox).containsExactlyElementsOf(expectedInOwnBox);
        assertThat(presence.playersPresentIn(team, ZoneRole.OPPONENT_BOX)).hasSize(10);
    }

    @Test
    void theMenInAZoneAreTheSumOfThePlayersTimesTheirSectorPresenceWithoutTheGoalkeeper() {
        TeamOnPitch team = fourFourTwo("Home");

        assertThat(presence.menInZone(team, ZoneRole.OWN_BOX)).isCloseTo(5.2, within(EXACT));
        assertThat(presence.menInZone(team, ZoneRole.DEFENCE)).isCloseTo(5.8, within(EXACT));
        assertThat(presence.menInZone(team, ZoneRole.MIDFIELD)).isCloseTo(6.0, within(EXACT));
        assertThat(presence.menInZone(team, ZoneRole.ATTACKING_THIRD)).isCloseTo(4.2, within(EXACT));
        assertThat(presence.menInZone(team, ZoneRole.OPPONENT_BOX)).isCloseTo(3.4, within(EXACT));
    }

    @Test
    void theMenOfOneTeamAreReadFromTheRowOfTheZoneRoleGivenByItsOwnPerspective() {
        TeamOnPitch team = fourFourTwo("Home");

        double inDefence = presence.menInZone(team, ZoneRole.DEFENCE);
        double inAttackingThird = presence.menInZone(team, ZoneRole.ATTACKING_THIRD);

        assertThat(inDefence).isNotEqualTo(inAttackingThird);
    }

    @Test
    void thePlayStyleDoesNotChangeTheMenInTheZoneNorTheFactor() {
        TeamOnPitch balanced = fourFourTwo("Home");
        TeamOnPitch attacking = TeamOnPitch.startingWith(TestTeams.team("Away", PlayStyle.ATTACKING, 50));
        TeamOnPitch defensive = TeamOnPitch.startingWith(TestTeams.team("Other", PlayStyle.DEFENSIVE, 50));

        assertThat(presence.menInZone(attacking, ZoneRole.MIDFIELD))
                .isEqualTo(presence.menInZone(balanced, ZoneRole.MIDFIELD));
        assertThat(presence.superiorityFactor(attacking, ZoneRole.MIDFIELD, defensive, ZoneRole.MIDFIELD))
                .isEqualTo(1.0);
    }

    @Test
    void theMenWithoutAPlayerLeaveOutThatPlayerContribution() {
        TeamOnPitch team = fourFourTwo("Home");
        Player midfielder = team.players().get(FIRST_MIDFIELDER_INDEX);
        Player keeper = team.players().get(KEEPER_INDEX);

        assertThat(presence.menInZoneWithout(team, ZoneRole.MIDFIELD, midfielder)).isCloseTo(5.0, within(EXACT));
        assertThat(presence.menInZoneWithout(team, ZoneRole.MIDFIELD, keeper)).isCloseTo(6.0, within(EXACT));
    }

    @ParameterizedTest
    @EnumSource(ZoneRole.class)
    void aFourFourTwoAgainstAFourFourTwoHasExactlyZeroAdvantageAndFactorOne(ZoneRole attackingRole) {
        TeamOnPitch home = fourFourTwo("Home");
        TeamOnPitch away = fourFourTwo("Away");

        for (ZoneRole defendingRole : ZoneRole.values()) {
            assertThat(presence.advantage(home, attackingRole, away, defendingRole)).isEqualTo(0.0);
            assertThat(presence.superiorityFactor(home, attackingRole, away, defendingRole)).isEqualTo(1.0);
            assertThat(presence.advantage(away, attackingRole, home, defendingRole)).isEqualTo(0.0);
            assertThat(presence.superiorityFactor(away, attackingRole, home, defendingRole)).isEqualTo(1.0);
        }
    }

    @Test
    void aMidfielderTakenFromAFourFourTwoGivesExactlyOneOverTheFactorPerManAtMidfield() {
        TeamOnPitch complete = fourFourTwo("Home");
        Player midfielder = complete.players().get(FIRST_MIDFIELDER_INDEX);
        TeamOnPitch tenMen = complete.withoutPlayer(midfielder);
        TeamOnPitch opponents = fourFourTwo("Away");

        double factor = presence.superiorityFactor(tenMen, ZoneRole.MIDFIELD, opponents, ZoneRole.MIDFIELD);
        double opponentsFactor = presence.superiorityFactor(opponents, ZoneRole.MIDFIELD, tenMen, ZoneRole.MIDFIELD);

        assertThat(factor).isCloseTo(1.0 / 1.25, within(EXACT));
        assertThat(opponentsFactor).isCloseTo(1.25, within(EXACT));
    }

    @Test
    void theFactorIsTheFactorPerManRaisedToTheAdvantageInsideTheCap() {
        TeamOnPitch crowded = teamOf("Home", 1, 1, 8);
        TeamOnPitch standard = fourFourTwo("Away");

        double advantage = presence.advantage(crowded, ZoneRole.ATTACKING_THIRD, standard, ZoneRole.DEFENCE);
        double factor = presence.superiorityFactor(crowded, ZoneRole.ATTACKING_THIRD, standard, ZoneRole.DEFENCE);

        assertThat(advantage).isCloseTo(3.6, within(EXACT));
        assertThat(factor).isCloseTo(Math.pow(1.25, 3.6), within(EXACT));
    }

    @Test
    void anAdvantageBeyondTheCapIsLimitedInBothDirections() {
        TeamOnPitch crowded = teamOf("Home", 1, 1, 8);
        TeamOnPitch otherCrowded = teamOf("Away", 1, 1, 8);

        double favourable = presence.advantage(crowded, ZoneRole.OPPONENT_BOX, otherCrowded, ZoneRole.OWN_BOX);
        double unfavourable = presence.advantage(crowded, ZoneRole.OWN_BOX, otherCrowded, ZoneRole.OPPONENT_BOX);
        double cappedFactor = presence.superiorityFactor(crowded, ZoneRole.OPPONENT_BOX, otherCrowded,
                ZoneRole.OWN_BOX);

        assertThat(favourable).isEqualTo(config.advantageCap());
        assertThat(unfavourable).isEqualTo(-config.advantageCap());
        assertThat(cappedFactor).isCloseTo(Math.pow(1.25, 4.0), within(EXACT));
    }

    @Test
    void theAverageIsWeightedOnThePresenceWithoutTheGoalkeeperNorPlayersWithZeroPresence() {
        TeamOnPitch team = teamWithPassingBySector(100, 40, 60, 80);

        double inOwnBox = presence.averageAttribute(team, ZoneRole.OWN_BOX, PlayerAttributes::passing);
        double inMidfield = presence.averageAttribute(team, ZoneRole.MIDFIELD, PlayerAttributes::passing);

        assertThat(inOwnBox).isCloseTo((4 * 1.0 * 40 + 4 * 0.3 * 60) / (4 * 1.0 + 4 * 0.3), within(EXACT));
        assertThat(inMidfield).isCloseTo((4 * 0.3 * 40 + 4 * 1.0 * 60 + 2 * 0.4 * 80) / 6.0, within(EXACT));
    }

    @Test
    void theAverageOfAUniformTeamIsTheUniformValue() {
        TeamOnPitch team = fourFourTwo("Home");

        double average = presence.averageAttribute(team, ZoneRole.DEFENCE, PlayerAttributes::tackling);

        assertThat(average).isCloseTo(50.0, within(EXACT));
    }

    @Test
    void theAverageFailsWhenNoPlayerHasPresenceInTheZone() {
        TeamOnPitch onlyKeeperAndForwards = keeperAndForwardsOnly();

        assertThatThrownBy(() -> presence.averageAttribute(onlyKeeperAndForwards, ZoneRole.OWN_BOX,
                PlayerAttributes::passing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OWN_BOX");
    }

    private TeamOnPitch keeperAndForwardsOnly() {
        Team team = TestTeams.team("Home", PlayStyle.BALANCED, 50);
        TeamOnPitch reduced = TeamOnPitch.startingWith(team);
        for (Player player : team.starters()) {
            if (player.role() != Role.GOALKEEPER && player.role() != Role.FORWARD) {
                reduced = reduced.withoutPlayer(player);
            }
        }
        return reduced;
    }

    private TeamOnPitch fourFourTwo(String name) {
        Team team = TestTeams.uniformTeam(name, 50);
        return TeamOnPitch.startingWith(team);
    }

    private TeamOnPitch teamOf(String name, int defenders, int midfielders, int forwards) {
        PlayerAttributes attributes = TestTeams.uniform(50);
        List<Player> starters = TestTeams.squad(name, "S", attributes, 1, defenders, midfielders, forwards);
        Team team = TestTeams.teamOf(name, starters);
        return TeamOnPitch.startingWith(team);
    }

    private TeamOnPitch teamWithPassingBySector(int keeper, int defenders, int midfielders, int forwards) {
        List<Player> starters = new ArrayList<>();
        starters.addAll(TestTeams.squad("Home", "S", passing(keeper), 1, 0, 0, 0));
        starters.addAll(TestTeams.squad("Home", "S", passing(defenders), 0, 4, 0, 0));
        starters.addAll(TestTeams.squad("Home", "S", passing(midfielders), 0, 0, 4, 0));
        starters.addAll(TestTeams.squad("Home", "S", passing(forwards), 0, 0, 0, 2));
        Team team = TestTeams.teamOf("Home", starters);
        return TeamOnPitch.startingWith(team);
    }

    private PlayerAttributes passing(int passing) {
        return TestTeams.withDuelAttributes(passing, 50, 50, 50);
    }
}
