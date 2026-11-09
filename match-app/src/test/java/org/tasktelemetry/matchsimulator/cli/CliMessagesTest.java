package org.tasktelemetry.matchsimulator.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.message.UserMessages;

class CliMessagesTest {

    private static final Object[] FOUR_ARGUMENTS = {"uno", "due", "tre", "quattro"};

    private final UserMessages italian = UserMessages.italian();

    @Test
    void everyCommandLineProblemHasItsItalianMessage() {
        for (CommandLineProblem problem : CommandLineProblem.values()) {
            assertThat(italian.has(problem)).as(problem.name()).isTrue();
        }
    }

    @Test
    void everyCliTextHasItsItalianMessage() {
        for (CliText text : CliText.values()) {
            assertThat(italian.has(text)).as(text.name()).isTrue();
        }
    }

    @Test
    void everyReportLabelHasItsItalianMessage() {
        for (ReportLabel label : ReportLabel.values()) {
            assertThat(italian.has(label)).as(label.name()).isTrue();
        }
    }

    @Test
    void everyCliMessageIsFilledWithoutLeavingPlaceholders() {
        for (CommandLineProblem problem : CommandLineProblem.values()) {
            assertThat(italian.text(problem, FOUR_ARGUMENTS)).as(problem.name()).doesNotContain("{");
        }
        for (CliText text : CliText.values()) {
            assertThat(italian.text(text, FOUR_ARGUMENTS)).as(text.name()).doesNotContain("{");
        }
        for (ReportLabel label : ReportLabel.values()) {
            assertThat(italian.text(label, FOUR_ARGUMENTS)).as(label.name()).doesNotContain("{");
        }
    }
}
