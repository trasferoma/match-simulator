package org.tasktelemetry.matchsimulator.teamfile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class TeamFileTermsTest {

    private static final List<String> ROLE_TERMS = List.of("portiere", "difensore", "centrocampista", "attaccante");

    private final TeamFileTerms terms = new TeamFileTerms();

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

    @Test
    void everyRoleOfTheEngineHasExactlyOneItalianTerm() {
        EnumSet<Role> translatedRoles = EnumSet.noneOf(Role.class);
        ROLE_TERMS.forEach(term -> translatedRoles.add(terms.role(term).orElseThrow()));

        assertThat(translatedRoles).isEqualTo(EnumSet.allOf(Role.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Portiere", "GOALKEEPER", "goalkeeper", "difensore ", ""})
    void aRoleThatIsNotExactlyAnItalianLowercaseTermIsNotTranslated(String term) {
        assertThat(terms.role(term)).isEmpty();
    }

    @Test
    void theAllowedRolesAreListedInTheOrderOfTheSpecification() {
        assertThat(terms.allowedRoles()).isEqualTo("portiere, difensore, centrocampista, attaccante");
    }
}
