package org.tasktelemetry.matchsimulator.teamfile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.squad.SquadRule;

class SquadRuleFieldsTest {

    private final SquadRuleFields fields = new SquadRuleFields();

    @Test
    void everySquadRuleHasItsField() {
        for (SquadRule rule : SquadRule.values()) {
            assertThat(fields.fieldOf(rule)).as(rule.name()).isNotBlank();
        }
    }

    @Test
    void theRulesOnThePlayersPointToThePlayers() {
        assertThat(fields.fieldOf(SquadRule.PLAYER_NAME_REPEATED)).isEqualTo("players");
        assertThat(fields.fieldOf(SquadRule.GOALKEEPER_MISSING)).isEqualTo("players");
        assertThat(fields.fieldOf(SquadRule.NO_FEASIBLE_FORMATION)).isEqualTo("players");
    }

    @Test
    void theRulesOnTheNamesPointToTheirName() {
        assertThat(fields.fieldOf(SquadRule.SQUAD_NAME_BLANK)).isEqualTo("name");
        assertThat(fields.fieldOf(SquadRule.COACH_NAME_BLANK)).isEqualTo("coach.name");
    }

    @Test
    void theTacticalReadingRulePointsToTheAttributeOfTheCoach() {
        assertThat(fields.fieldOf(SquadRule.TACTICAL_READING_OUT_OF_SCALE))
                .isEqualTo("coach.attributes.tacticalReading");
    }
}
