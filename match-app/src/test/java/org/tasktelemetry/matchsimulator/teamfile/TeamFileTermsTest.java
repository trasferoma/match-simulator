package org.tasktelemetry.matchsimulator.teamfile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class TeamFileTermsTest {

    private static final List<String> STYLE_TERMS =
            List.of("equilibrato", "offensivo", "difensivo", "possesso", "contropiede");
    private static final List<String> ROLE_TERMS = List.of("portiere", "difensore", "centrocampista", "attaccante");

    private final TeamFileTerms terms = new TeamFileTerms();

    @ParameterizedTest
    @CsvSource({
        "equilibrato, BALANCED",
        "offensivo, ATTACKING",
        "difensivo, DEFENSIVE",
        "possesso, POSSESSION",
        "contropiede, COUNTER_ATTACK"
    })
    void everyItalianStyleIsTranslatedToItsConstant(String term, PlayStyle expected) {
        assertThat(terms.style(term)).contains(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "portiere, GOALKEEPER",
        "difensore, DEFENDER",
        "centrocampista, MIDFIELDER",
        "attaccante, FORWARD"
    })
    void everyItalianRoleIsTranslatedToItsConstant(String term, Role expected) {
        assertThat(terms.role(term)).contains(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "GOALKEEPER, portiere",
        "DEFENDER, difensore",
        "MIDFIELDER, centrocampista",
        "FORWARD, attaccante"
    })
    void everyRoleConstantIsWrittenBackAsItsItalianTerm(Role role, String expected) {
        assertThat(terms.termOf(role)).isEqualTo(expected);
    }

    @Test
    void everyConstantOfTheEngineHasExactlyOneItalianTerm() {
        EnumSet<PlayStyle> translatedStyles = EnumSet.noneOf(PlayStyle.class);
        STYLE_TERMS.forEach(term -> translatedStyles.add(terms.style(term).orElseThrow()));
        EnumSet<Role> translatedRoles = EnumSet.noneOf(Role.class);
        ROLE_TERMS.forEach(term -> translatedRoles.add(terms.role(term).orElseThrow()));

        assertThat(translatedStyles).isEqualTo(EnumSet.allOf(PlayStyle.class));
        assertThat(translatedRoles).isEqualTo(EnumSet.allOf(Role.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Contropiede", "COUNTER_ATTACK", "counter_attack", "balanced", " offensivo", ""})
    void aStyleThatIsNotExactlyAnItalianLowercaseTermIsNotTranslated(String term) {
        assertThat(terms.style(term)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Portiere", "GOALKEEPER", "goalkeeper", "difensore ", ""})
    void aRoleThatIsNotExactlyAnItalianLowercaseTermIsNotTranslated(String term) {
        assertThat(terms.role(term)).isEmpty();
    }

    @Test
    void theAllowedValuesAreListedInTheOrderOfTheSpecification() {
        assertThat(terms.allowedStyles()).isEqualTo("equilibrato, offensivo, difensivo, possesso, contropiede");
        assertThat(terms.allowedRoles()).isEqualTo("portiere, difensore, centrocampista, attaccante");
        assertThat(Stream.of(terms.allowedStyles().split(", "))).containsExactlyElementsOf(STYLE_TERMS);
    }
}
