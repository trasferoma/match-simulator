package org.tasktelemetry.matchsimulator.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.narration.StyleAbbreviation;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.TeamRule;

class UserMessagesTest {

    private static final Object[] FOUR_ARGUMENTS = {"uno", "due", "tre", "quattro"};

    private final UserMessages italian = UserMessages.italian();

    @Test
    void everyEngineRuleHasItsItalianMessage() {
        for (TeamRule rule : TeamRule.values()) {
            assertThat(italian.has(rule)).as(rule.name()).isTrue();
        }
    }

    @Test
    void everyEngineRuleMessageIsFilledWithoutLeavingPlaceholders() {
        for (TeamRule rule : TeamRule.values()) {
            String text = italian.text(rule, FOUR_ARGUMENTS);

            assertThat(text).as(rule.name()).isNotBlank().doesNotContain("{");
        }
    }

    @Test
    void everySectorAndEveryPlayStyleHasItsItalianWord() {
        assertThat(Role.values()).allSatisfy(role -> assertThat(italian.text(role)).isNotBlank());
        assertThat(PlayStyle.values()).allSatisfy(style -> assertThat(italian.text(style)).isNotBlank());
    }

    @Test
    void everyPlayStyleHasItsAbbreviationForTheFieldLine() {
        assertThat(PlayStyle.values()).allSatisfy(style -> {
            String text = italian.text(StyleAbbreviation.of(style));

            assertThat(text).isNotBlank().endsWith(".");
        });
    }

    @Test
    void theMessageFileIsReadAsUtf8() {
        String text = italian.text(TeamRule.PLAYER_NAME_BLANK);

        assertThat(text).contains("può");
    }

    @Test
    void aMessageNeedingMoreArgumentsThanGivenIsRefused() {
        UserMessages messages = new UserMessages(Map.of("Sample.TWO", "{0} e {1}"));

        assertThatThrownBy(() -> messages.text(Sample.TWO, "uno")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anArgumentThatLooksLikeAPlaceholderIsNotFilledAgain() throws IOException {
        UserMessages messages = UserMessages.read(new StringReader("Sample.TWO={0} e {1}"));

        assertThat(messages.text(Sample.TWO, "{1}", "due")).isEqualTo("{1} e due");
    }

    @Test
    void aMissingMessageIsReportedWithItsKey() {
        UserMessages empty = new UserMessages(Map.of());

        assertThatThrownBy(() -> empty.text(Sample.TWO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Sample.TWO");
    }

    private enum Sample {
        TWO
    }
}
