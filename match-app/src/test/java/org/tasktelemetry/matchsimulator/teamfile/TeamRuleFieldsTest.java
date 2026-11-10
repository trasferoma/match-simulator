package org.tasktelemetry.matchsimulator.teamfile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.team.TeamRule;

class TeamRuleFieldsTest {

    private final TeamRuleFields fields = new TeamRuleFields();

    @Test
    void everyEngineRuleHasItsField() {
        for (TeamRule rule : TeamRule.values()) {
            assertThat(fields.fieldOf(rule)).as(rule.name()).isNotBlank();
        }
    }

    @Test
    void theRulesOnTheStartersPointToTheStarters() {
        assertThat(fields.fieldOf(TeamRule.STARTERS_COUNT)).isEqualTo("starters");
        assertThat(fields.fieldOf(TeamRule.ONE_GOALKEEPER)).isEqualTo("starters");
        assertThat(fields.fieldOf(TeamRule.ROLE_MISSING)).isEqualTo("starters");
    }

    @Test
    void aRepeatedNamePointsToStartersAndBench() {
        assertThat(fields.fieldOf(TeamRule.PLAYER_NAME_REPEATED)).isEqualTo("starters, bench");
    }

    @Test
    void theTeamNameRulePointsToTheName() {
        assertThat(fields.fieldOf(TeamRule.TEAM_NAME_BLANK)).isEqualTo("name");
    }
}
