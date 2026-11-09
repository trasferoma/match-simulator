package org.tasktelemetry.matchsimulator.engine.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.tasktelemetry.matchsimulator.engine.team.TeamFixtures.lineup;
import static org.tasktelemetry.matchsimulator.engine.team.TeamFixtures.player;
import static org.tasktelemetry.matchsimulator.engine.team.TeamFixtures.standardBench;
import static org.tasktelemetry.matchsimulator.engine.team.TeamFixtures.standardStarters;
import static org.tasktelemetry.matchsimulator.engine.team.TeamFixtures.teamWithStarters;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class TeamRuleViolationTest {

    @Test
    void aWrongNumberOfStartersNamesTheRuleWithExpectedAndFoundCounts() {
        List<Player> tenStarters = lineup("H", 1, 4, 3, 2);

        assertViolation(() -> teamWithStarters(tenStarters), TeamRule.STARTERS_COUNT, 11, 10);
    }

    @Test
    void twoGoalkeepersNameTheRuleWithTheFoundCount() {
        List<Player> twoGoalkeepers = lineup("H", 2, 3, 4, 2);

        assertViolation(() -> teamWithStarters(twoGoalkeepers), TeamRule.ONE_GOALKEEPER, 2L);
    }

    @Test
    void aMissingDepartmentNamesTheRuleWithTheMissingRole() {
        List<Player> noDefenders = lineup("H", 1, 0, 5, 5);

        assertViolation(() -> teamWithStarters(noDefenders), TeamRule.ROLE_MISSING, Role.DEFENDER);
    }

    @Test
    void aRepeatedPlayerNameNamesTheRuleWithTheName() {
        List<Player> starters = standardStarters();
        List<Player> bench = new ArrayList<>(standardBench());
        Player repeated = player(starters.get(0).name(), Role.FORWARD);
        bench.set(0, repeated);

        assertViolation(() -> new Team("Home", PlayStyle.BALANCED, starters, bench), TeamRule.PLAYER_NAME_REPEATED,
                repeated.name());
    }

    @Test
    void aBlankTeamNameNamesTheRule() {
        List<Player> starters = standardStarters();

        assertViolation(() -> new Team(" ", PlayStyle.BALANCED, starters, List.of()), TeamRule.TEAM_NAME_BLANK);
    }

    @Test
    void aBlankPlayerNameNamesTheRule() {
        assertViolation(() -> player(" ", Role.DEFENDER), TeamRule.PLAYER_NAME_BLANK);
    }

    @Test
    void anAttributeOutOfScaleNamesTheRuleWithAttributeBoundsAndValue() {
        assertViolation(() -> new PlayerAttributes(50, 50, 50, 50, 50, 101, 50, 50), TeamRule.ATTRIBUTE_OUT_OF_SCALE,
                "pace", PlayerAttributes.MIN_VALUE, PlayerAttributes.MAX_VALUE, 101);
    }

    private void assertViolation(Runnable action, TeamRule rule, Object... arguments) {
        assertThatThrownBy(action::run)
                .isInstanceOf(IllegalArgumentException.class)
                .isInstanceOfSatisfying(TeamRuleViolation.class, violation -> {
                    assertThat(violation.rule()).isEqualTo(rule);
                    assertThat(violation.arguments()).containsExactly(arguments);
                });
    }
}
