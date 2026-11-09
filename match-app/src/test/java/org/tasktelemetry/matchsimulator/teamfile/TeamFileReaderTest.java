package org.tasktelemetry.matchsimulator.teamfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class TeamFileReaderTest {

    private static final String STYLES = "equilibrato, offensivo, difensivo, possesso, contropiede";
    private static final String ROLES = "portiere, difensore, centrocampista, attaccante";

    @TempDir
    Path directory;

    private final TeamFileReader reader = TeamFileReader.italian();

    @Test
    void theHomeExampleIsReadWithStartersAndBenchInFileOrder() {
        Team team = reader.read(TeamFileFixture.homeExample());

        assertThat(team.name()).isEqualTo("Atletico Mare");
        assertThat(team.style()).isEqualTo(PlayStyle.ATTACKING);
        assertThat(team.starters()).hasSize(11);
        assertThat(team.bench()).hasSize(7);
        assertThat(team.starters().get(0).name()).isEqualTo("Marco Bianchi");
        assertThat(team.starters().get(0).role()).isEqualTo(Role.GOALKEEPER);
        assertThat(team.starters().get(0).attributes().goalkeeping()).isEqualTo(84);
        assertThat(team.bench().get(0).role()).isEqualTo(Role.GOALKEEPER);
        assertThat(team.bench().get(6).name()).isEqualTo("Emanuele Sala");
    }

    @Test
    void theAwayExampleIsReadWithItsOwnStyle() {
        Team team = reader.read(TeamFileFixture.awayExample());

        assertThat(team.name()).isEqualTo("Sporting Collina");
        assertThat(team.style()).isEqualTo(PlayStyle.COUNTER_ATTACK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "style", "starters", "bench"})
    void aMissingTopLevelFieldIsRefusedNamingFileAndField(String field) {
        ObjectNode team = TeamFileFixture.validTeam();
        team.remove(field);
        Path file = TeamFileFixture.write(directory, team);

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOfSatisfying(InvalidTeamFileException.class, exception -> {
                    assertThat(exception.file()).isEqualTo(file);
                    assertThat(exception.field()).contains(field);
                })
                .hasMessageContaining(file.toString())
                .hasMessageContaining("campo " + field)
                .hasMessageContaining("il campo manca");
    }

    @Test
    void aMissingPlayerNameIsRefusedNamingThePlayerField() {
        ObjectNode team = TeamFileFixture.validTeam();
        ((ObjectNode) team.get("bench").get(2)).remove("name");

        assertRefused(team, "bench[2].name", "il campo manca");
    }

    @Test
    void anUnknownRoleIsRefusedListingTheAllowedRoles() {
        ObjectNode team = TeamFileFixture.validTeam();
        ((ObjectNode) team.get("starters").get(3)).put("role", "ala");

        assertRefused(team, "starters[3].role", "\"ala\"", ROLES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Portiere", "goalkeeper", "GOALKEEPER", ""})
    void aRoleThatIsNotAnItalianLowercaseTermIsRefusedListingTheAllowedRoles(String role) {
        ObjectNode team = TeamFileFixture.validTeam();
        ((ObjectNode) team.get("starters").get(0)).put("role", role);

        assertThatThrownBy(() -> reader.read(TeamFileFixture.write(directory, team)))
                .isInstanceOf(InvalidTeamFileException.class)
                .hasMessageContaining("starters[0].role");
    }

    @Test
    void aCapitalisedRoleNamesTheRoleFieldAndListsTheAllowedValues() {
        ObjectNode team = TeamFileFixture.validTeam();
        ((ObjectNode) team.get("starters").get(0)).put("role", "Portiere");

        assertRefused(team, "starters[0].role", "\"Portiere\"", ROLES);
    }

    @Test
    void anEnglishConstantAsRoleIsRefusedListingTheAllowedRoles() {
        ObjectNode team = TeamFileFixture.validTeam();
        ((ObjectNode) team.get("starters").get(0)).put("role", "goalkeeper");

        assertRefused(team, "starters[0].role", "\"goalkeeper\"", ROLES);
    }

    @Test
    void aCapitalisedStyleIsRefusedListingTheAllowedStyles() {
        ObjectNode team = TeamFileFixture.validTeam();
        team.put("style", "Contropiede");

        assertRefused(team, "style", "\"Contropiede\"", STYLES);
    }

    @Test
    void anEnglishConstantAsStyleIsRefusedListingTheAllowedStyles() {
        ObjectNode team = TeamFileFixture.validTeam();
        team.put("style", "COUNTER_ATTACK");

        assertRefused(team, "style", "\"COUNTER_ATTACK\"", STYLES);
    }

    @Test
    void twoGoalkeepersAmongTheStartersAreRefusedWithTheViolatedRule() {
        ObjectNode team = TeamFileFixture.validTeam();
        ((ObjectNode) team.get("starters").get(1)).put("role", "portiere");

        assertRefused(team, "starters, bench", "esattamente un portiere", "ne ha 2");
    }

    @Test
    void aDepartmentWithoutStartersIsRefusedWithTheViolatedRule() {
        ObjectNode team = TeamFileFixture.validTeam();
        for (int index = 1; index <= 4; index++) {
            ((ObjectNode) team.get("starters").get(index)).put("role", "centrocampista");
        }

        assertRefused(team, "starters, bench", "almeno un difensore");
    }

    @Test
    void aNameRepeatedBetweenStartersAndBenchIsRefusedNamingThePlayer() {
        ObjectNode team = TeamFileFixture.validTeam();
        ((ObjectNode) team.get("bench").get(0)).put("name", "Marco Bianchi");

        assertRefused(team, "starters, bench", "ripetuto: Marco Bianchi");
    }

    @Test
    void aMissingAttributeIsRefusedNamingThePlayerAndTheAttribute() {
        ObjectNode team = TeamFileFixture.validTeam();
        ((ObjectNode) team.get("starters").get(4).get("attributes")).remove("pace");

        assertRefused(team, "starters[4].attributes.pace", "il campo manca");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101, -5})
    void anAttributeOutOfScaleIsRefusedNamingThePlayerAndTheAttribute(int value) {
        ObjectNode team = TeamFileFixture.validTeam();
        ((ObjectNode) team.get("bench").get(1).get("attributes")).put("passing", value);

        assertRefused(team, "bench[1].attributes", "l'attributo passing", "compreso tra 1 e 100", "vale " + value);
    }

    @Test
    void anAttributeThatIsNotAnIntegerIsRefusedNamingThePlayerAndTheAttribute() {
        ObjectNode team = TeamFileFixture.validTeam();
        ((ObjectNode) team.get("starters").get(2).get("attributes")).put("shooting", 70.5);

        assertRefused(team, "starters[2].attributes.shooting", "il tipo atteso");
    }

    @Test
    void anAttributeTooBigForAnIntegerIsRefusedNamingThePlayerAndTheAttribute() {
        ObjectNode team = TeamFileFixture.validTeam();
        ((ObjectNode) team.get("starters").get(0).get("attributes")).put("passing", 99_999_999_999L);

        assertRefused(team, "starters[0].attributes.passing", "il tipo atteso");
    }

    @Test
    void anAttributeWrittenAsTextIsRefusedNamingThePlayerAndTheAttribute() {
        ObjectNode team = TeamFileFixture.validTeam();
        ((ObjectNode) team.get("starters").get(2).get("attributes")).put("shooting", "alto");

        assertRefused(team, "starters[2].attributes.shooting", "il tipo atteso");
    }

    @Test
    void aBlankTeamNameIsRefusedNamingTheField() {
        ObjectNode team = TeamFileFixture.validTeam();
        team.put("name", "  ");

        assertRefused(team, "name", "non può essere vuoto");
    }

    @Test
    void aWrongNumberOfStartersIsRefusedWithTheViolatedRule() {
        ObjectNode team = TeamFileFixture.validTeam();
        ((ArrayNode) team.get("starters")).remove(10);

        assertRefused(team, "starters, bench", "esattamente 11 titolari", "ne ha 10");
    }

    @Test
    void aMalformedJsonFileIsRefusedNamingTheFile() {
        Path file = TeamFileFixture.writeText(directory, "{\"name\": \"Atletico\", \"style\": ");

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOfSatisfying(InvalidTeamFileException.class, exception -> {
                    assertThat(exception.file()).isEqualTo(file);
                    assertThat(exception.field()).isEmpty();
                })
                .hasMessageContaining(file.toString())
                .hasMessageContaining("non è un JSON valido")
                .hasMessageContaining("riga 1");
    }

    @Test
    void aMissingFileIsRefusedNamingTheFile() {
        Path file = directory.resolve("does-not-exist.json");

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(InvalidTeamFileException.class)
                .hasMessageContaining(file.toString())
                .hasMessageContaining("il file non esiste");
    }

    @Test
    void aFileThatIsNotATeamObjectIsRefusedNamingTheFile() {
        Path file = TeamFileFixture.writeText(directory, "null");

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(InvalidTeamFileException.class)
                .hasMessageContaining(file.toString())
                .hasMessageContaining("oggetto squadra");
    }

    @Test
    void aListOfStartersWrittenAsAnObjectIsRefusedNamingTheField() {
        ObjectNode team = TeamFileFixture.validTeam();
        team.putObject("starters");

        assertRefused(team, "starters", "il valore non ha il tipo atteso");
    }

    @Test
    void aFileWhoseRootIsAnArrayIsRefusedAsNotATeamObject() {
        Path file = TeamFileFixture.writeText(directory, "[1, 2]");

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(InvalidTeamFileException.class)
                .hasMessageContaining(file.toString())
                .hasMessageContaining("oggetto squadra");
    }

    @Test
    void anExtraFieldInTheFileIsIgnored() {
        ObjectNode team = TeamFileFixture.validTeam();
        team.put("stile", "offensivo");

        Team read = reader.read(TeamFileFixture.write(directory, team));

        assertThat(read.name()).isEqualTo("Atletico Mare");
    }

    private void assertRefused(ObjectNode team, String field, String... expectedFragments) {
        Path file = TeamFileFixture.write(directory, team);

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOfSatisfying(InvalidTeamFileException.class, exception -> {
                    assertThat(exception.file()).isEqualTo(file);
                    assertThat(exception.field()).contains(field);
                })
                .hasMessageContaining(file.toString())
                .hasMessageContaining("campo " + field)
                .satisfies(exception -> {
                    for (String fragment : expectedFragments) {
                        assertThat(exception.getMessage()).contains(fragment);
                    }
                });
    }
}
