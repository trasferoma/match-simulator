package org.tasktelemetry.matchsimulator.teamfile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.squad.SquadRule;
import org.tasktelemetry.matchsimulator.message.UserMessages;

class TeamFileMessagesTest {

    private static final Object[] FOUR_ARGUMENTS = {"uno", "due", "tre", "quattro"};

    private final UserMessages italian = UserMessages.italian();

    @Test
    void everyTeamFileProblemHasItsItalianMessage() {
        for (TeamFileProblem problem : TeamFileProblem.values()) {
            assertThat(italian.has(problem)).as(problem.name()).isTrue();
        }
    }

    @Test
    void everyTeamFileErrorFrameHasItsItalianMessage() {
        for (TeamFileErrorFrame frame : TeamFileErrorFrame.values()) {
            assertThat(italian.has(frame)).as(frame.name()).isTrue();
        }
    }

    @Test
    void everySquadRuleHasItsItalianMessageFilledWithoutLeavingPlaceholders() {
        for (SquadRule rule : SquadRule.values()) {
            assertThat(italian.has(rule)).as(rule.name()).isTrue();
            assertThat(italian.text(rule, FOUR_ARGUMENTS)).as(rule.name()).doesNotContain("{");
        }
    }

    @Test
    void everyTeamFileMessageIsFilledWithoutLeavingPlaceholders() {
        for (TeamFileProblem problem : TeamFileProblem.values()) {
            assertThat(italian.text(problem, FOUR_ARGUMENTS)).as(problem.name()).doesNotContain("{");
        }
        for (TeamFileErrorFrame frame : TeamFileErrorFrame.values()) {
            assertThat(italian.text(frame, FOUR_ARGUMENTS)).as(frame.name()).doesNotContain("{");
        }
    }
}
