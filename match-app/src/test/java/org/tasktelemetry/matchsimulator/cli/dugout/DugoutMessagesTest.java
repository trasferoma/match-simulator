package org.tasktelemetry.matchsimulator.cli.dugout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.human.PlayerStatus;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.message.UserMessages;

class DugoutMessagesTest {

    private static final Object[] NINE_ARGUMENTS = {"1", "2", "3", "4", "5", "6", "7", "8", "9"};

    private final UserMessages italian = UserMessages.italian();

    @Test
    void everyDugoutLabelHasItsItalianMessage() {
        for (DugoutLabel label : DugoutLabel.values()) {
            assertThat(italian.has(label)).as(label.name()).isTrue();
        }
    }

    @Test
    void everyDugoutProblemHasItsItalianMessage() {
        for (DugoutProblem problem : DugoutProblem.values()) {
            assertThat(italian.has(problem)).as(problem.name()).isTrue();
        }
    }

    @Test
    void everyRefusalReasonHasItsItalianMessage() {
        for (RefusalReason reason : RefusalReason.values()) {
            assertThat(italian.has(reason)).as(reason.name()).isTrue();
        }
    }

    @Test
    void everyFatigueBandAndEveryUnavailableStatusHasItsItalianMessage() {
        for (FatigueBand band : FatigueBand.values()) {
            assertThat(italian.has(band)).as(band.name()).isTrue();
        }
        Arrays.stream(PlayerStatus.values())
                .filter(PlayerStatus::isUnavailable)
                .forEach(status -> assertThat(italian.has(status)).as(status.name()).isTrue());
    }

    @Test
    void everyDugoutMessageIsFilledWithoutLeavingPlaceholders() {
        for (DugoutLabel label : DugoutLabel.values()) {
            assertThat(italian.text(label, NINE_ARGUMENTS)).as(label.name()).doesNotContain("{");
        }
        for (DugoutProblem problem : DugoutProblem.values()) {
            assertThat(italian.text(problem, NINE_ARGUMENTS)).as(problem.name()).doesNotContain("{");
        }
        for (RefusalReason reason : RefusalReason.values()) {
            assertThat(italian.text(reason, NINE_ARGUMENTS)).as(reason.name()).doesNotContain("{");
        }
    }

    @Test
    void thePromptKeepsItsTrailingSpace() {
        assertThat(italian.text(DugoutLabel.PROMPT)).isEqualTo("> ");
    }
}
