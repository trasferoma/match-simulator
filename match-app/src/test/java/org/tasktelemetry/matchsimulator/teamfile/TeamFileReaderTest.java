package org.tasktelemetry.matchsimulator.teamfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.coach.squad.Squad;
import org.tasktelemetry.matchsimulator.engine.team.Role;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class TeamFileReaderTest {

    private static final String ROLES = "portiere, difensore, centrocampista, attaccante";
    private static final String CATALOG = "5-3-2, 4-5-1, 4-4-2, 3-5-2, 4-3-3";
    private static final String TACTICAL_READING = "coach.attributes.tacticalReading";

    @TempDir
    Path directory;

    private final TeamFileReader reader = TeamFileReader.italian();

    @Test
    void theHomeExampleIsReadAsASquadWithItsCoachAndThePlayersInFileOrder() {
        Squad squad = reader.read(TeamFileFixture.homeExample());

        assertThat(squad.name()).isEqualTo("Atletico Mare");
        assertThat(squad.coach().name()).isEqualTo("Giorgio Parodi");
        assertThat(squad.coach().attributes().tacticalReading()).isEqualTo(65);
        assertThat(squad.players()).hasSize(18);
        assertThat(squad.players().get(0).name()).isEqualTo("Marco Bianchi");
        assertThat(squad.players().get(0).role()).isEqualTo(Role.GOALKEEPER);
        assertThat(squad.players().get(0).attributes().goalkeeping()).isEqualTo(84);
        assertThat(squad.players().get(11).role()).isEqualTo(Role.GOALKEEPER);
        assertThat(squad.players().get(17).name()).isEqualTo("Emanuele Sala");
    }

    @Test
    void theAwayExampleIsReadWithItsOwnCoach() {
        Squad squad = reader.read(TeamFileFixture.awayExample());

        assertThat(squad.name()).isEqualTo("Sporting Collina");
        assertThat(squad.coach().name()).isEqualTo("Roberto Basile");
        assertThat(squad.coach().attributes().tacticalReading()).isEqualTo(40);
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "coach", "players"})
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
    void aMissingCoachNameIsRefusedNamingTheField() {
        ObjectNode team = TeamFileFixture.validTeam();
        coachOf(team).remove("name");

        assertRefused(team, "coach.name", "il campo manca");
    }

    @Test
    void aBlankCoachNameIsRefusedNamingTheField() {
        ObjectNode team = TeamFileFixture.validTeam();
        coachOf(team).put("name", " ");

        assertRefused(team, "coach.name", "non può essere vuoto");
    }

    @Test
    void missingCoachAttributesAreRefusedNamingTheField() {
        ObjectNode team = TeamFileFixture.validTeam();
        coachOf(team).remove("attributes");

        assertRefused(team, "coach.attributes", "il campo manca");
    }

    @Test
    void aMissingTacticalReadingIsRefusedNamingTheField() {
        ObjectNode team = TeamFileFixture.validTeam();
        coachAttributesOf(team).remove("tacticalReading");

        assertRefused(team, TACTICAL_READING, "il campo manca");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101, -5})
    void aTacticalReadingOutOfScaleIsRefusedNamingTheFieldAndTheValue(int value) {
        ObjectNode team = TeamFileFixture.validTeam();
        coachAttributesOf(team).put("tacticalReading", value);

        assertRefused(team, TACTICAL_READING, "la lettura tattica deve essere compresa tra 1 e 100",
                "vale " + value);
    }

    @Test
    void aTacticalReadingThatIsNotAnIntegerIsRefusedNamingTheField() {
        ObjectNode team = TeamFileFixture.validTeam();
        coachAttributesOf(team).put("tacticalReading", 70.5);

        assertRefused(team, TACTICAL_READING, "il tipo atteso");
    }

    @Test
    void aTacticalReadingOnTheEdgesOfTheScaleIsAccepted() {
        ObjectNode lowest = TeamFileFixture.validTeam();
        coachAttributesOf(lowest).put("tacticalReading", 1);
        ObjectNode highest = TeamFileFixture.validTeam();
        coachAttributesOf(highest).put("tacticalReading", 100);

        Squad lowestSquad = reader.read(TeamFileFixture.write(directory, lowest));
        Squad highestSquad = reader.read(TeamFileFixture.write(directory, highest));

        assertThat(lowestSquad.coach().attributes().tacticalReading()).isEqualTo(1);
        assertThat(highestSquad.coach().attributes().tacticalReading()).isEqualTo(100);
    }

    @Test
    void aMissingPlayerNameIsRefusedNamingThePlayerField() {
        ObjectNode team = TeamFileFixture.validTeam();
        playerOf(team, 14).remove("name");

        assertRefused(team, "players[14].name", "il campo manca");
    }

    @Test
    void anUnknownRoleIsRefusedListingTheAllowedRoles() {
        ObjectNode team = TeamFileFixture.validTeam();
        playerOf(team, 3).put("role", "ala");

        assertRefused(team, "players[3].role", "\"ala\"", ROLES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Portiere", "goalkeeper", "GOALKEEPER"})
    void aRoleThatIsNotAnItalianLowercaseTermIsRefusedListingTheAllowedRoles(String role) {
        ObjectNode team = TeamFileFixture.validTeam();
        playerOf(team, 0).put("role", role);

        assertRefused(team, "players[0].role", "\"" + role + "\"", ROLES);
    }

    @Test
    void twoPlayersWithTheSameNameAreRefusedNamingThePlayer() {
        ObjectNode team = TeamFileFixture.validTeam();
        playerOf(team, 12).put("name", "Marco Bianchi");

        assertRefused(team, "players", "i nomi dei giocatori devono essere unici", "ripetuto: Marco Bianchi");
    }

    @Test
    void aSquadWithoutGoalkeepersIsRefusedWithTheViolatedRule() {
        ObjectNode team = TeamFileFixture.validTeam();
        playerOf(team, 0).put("role", "difensore");
        playerOf(team, 11).put("role", "difensore");

        assertRefused(team, "players", "almeno un portiere");
    }

    @Test
    void aSquadWithoutPlayersIsRefusedAsWithoutGoalkeepers() {
        ObjectNode team = TeamFileFixture.validTeam();
        team.putArray("players");

        assertRefused(team, "players", "almeno un portiere");
    }

    @Test
    void aSquadWithoutAFeasibleFormationIsRefusedListingTheFormationsOfTheCatalog() {
        ObjectNode team = TeamFileFixture.validTeam();
        ArrayNode players = (ArrayNode) team.get("players");
        keepOnlyTheFirstTwelvePlayers(players);
        String[] roles = {"portiere", "difensore", "difensore", "centrocampista", "centrocampista", "centrocampista",
            "centrocampista", "centrocampista", "centrocampista", "centrocampista", "attaccante", "portiere"};
        for (int index = 0; index < roles.length; index++) {
            ((ObjectNode) players.get(index)).put("role", roles[index]);
        }

        assertRefused(team, "players", "nessun modulo del catalogo", CATALOG);
    }

    @Test
    void aSquadThatAllowsOnlyOneFormationOfTheCatalogIsAccepted() {
        ObjectNode team = TeamFileFixture.validTeam();
        ArrayNode players = (ArrayNode) team.get("players");
        keepOnlyTheFirstTwelvePlayers(players);
        String[] roles = {"portiere", "difensore", "difensore", "difensore", "difensore", "centrocampista",
            "centrocampista", "centrocampista", "attaccante", "attaccante", "attaccante", "portiere"};
        for (int index = 0; index < roles.length; index++) {
            ((ObjectNode) players.get(index)).put("role", roles[index]);
        }

        Squad squad = reader.read(TeamFileFixture.write(directory, team));

        assertThat(squad.players()).hasSize(12);
    }

    @Test
    void aMissingAttributeIsRefusedNamingThePlayerAndTheAttribute() {
        ObjectNode team = TeamFileFixture.validTeam();
        attributesOf(team, 4).remove("pace");

        assertRefused(team, "players[4].attributes.pace", "il campo manca");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101, -5})
    void anAttributeOutOfScaleIsRefusedNamingThePlayerAndTheAttribute(int value) {
        ObjectNode team = TeamFileFixture.validTeam();
        attributesOf(team, 12).put("passing", value);

        assertRefused(team, "players[12].attributes", "l'attributo passing", "compreso tra 1 e 100",
                "vale " + value);
    }

    @Test
    void anAttributeThatIsNotAnIntegerIsRefusedNamingThePlayerAndTheAttribute() {
        ObjectNode team = TeamFileFixture.validTeam();
        attributesOf(team, 2).put("shooting", 70.5);

        assertRefused(team, "players[2].attributes.shooting", "il tipo atteso");
    }

    @Test
    void anAttributeTooBigForAnIntegerIsRefusedNamingThePlayerAndTheAttribute() {
        ObjectNode team = TeamFileFixture.validTeam();
        attributesOf(team, 0).put("passing", 99_999_999_999L);

        assertRefused(team, "players[0].attributes.passing", "il tipo atteso");
    }

    @Test
    void anAttributeWrittenAsTextIsRefusedNamingThePlayerAndTheAttribute() {
        ObjectNode team = TeamFileFixture.validTeam();
        attributesOf(team, 2).put("shooting", "alto");

        assertRefused(team, "players[2].attributes.shooting", "il tipo atteso");
    }

    @Test
    void aBlankSquadNameIsRefusedNamingTheField() {
        ObjectNode team = TeamFileFixture.validTeam();
        team.put("name", "  ");

        assertRefused(team, "name", "non può essere vuoto");
    }

    @Test
    void aMalformedJsonFileIsRefusedNamingTheFile() {
        Path file = TeamFileFixture.writeText(directory, "{\"name\": \"Atletico\", \"coach\": ");

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
    void aListOfPlayersWrittenAsAnObjectIsRefusedNamingTheField() {
        ObjectNode team = TeamFileFixture.validTeam();
        team.putObject("players");

        assertRefused(team, "players", "il valore non ha il tipo atteso");
    }

    @Test
    void aFileWhoseRootIsAnArrayIsRefusedAsNotATeamObject() {
        Path file = TeamFileFixture.writeText(directory, "[1, 2]");

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(InvalidTeamFileException.class)
                .hasMessageContaining(file.toString())
                .hasMessageContaining("oggetto squadra");
    }

    @ParameterizedTest
    @ValueSource(strings = {"style", "starters", "bench"})
    void theFieldsOfTheOldFormatAreRefusedAsUnknown(String field) {
        ObjectNode team = TeamFileFixture.validTeam();
        team.put(field, "offensivo");

        assertRefused(team, field, "il campo non è previsto");
    }

    @Test
    void anUnknownTeamFieldIsRefusedNamingIt() {
        ObjectNode team = TeamFileFixture.validTeam();
        team.put("stile", "offensivo");

        assertRefused(team, "stile", "il campo non è previsto");
    }

    @Test
    void anUnknownCoachFieldIsRefusedNamingItWithItsPath() {
        ObjectNode team = TeamFileFixture.validTeam();
        coachOf(team).put("nickname", "Mister");

        assertRefused(team, "coach.nickname", "il campo non è previsto");
    }

    @Test
    void anUnknownCoachAttributeIsRefusedNamingItWithItsPath() {
        ObjectNode team = TeamFileFixture.validTeam();
        coachAttributesOf(team).put("charisma", 70);

        assertRefused(team, "coach.attributes.charisma", "il campo non è previsto");
    }

    @Test
    void anUnknownPlayerFieldIsRefusedNamingItWithItsPath() {
        ObjectNode team = TeamFileFixture.validTeam();
        playerOf(team, 3).put("ruolo", "ala");

        assertRefused(team, "players[3].ruolo", "il campo non è previsto");
    }

    @Test
    void anUnknownAttributeIsRefusedNamingItWithItsPath() {
        ObjectNode team = TeamFileFixture.validTeam();
        attributesOf(team, 0).put("speed", 70);

        assertRefused(team, "players[0].attributes.speed", "il campo non è previsto");
    }

    private ObjectNode coachOf(ObjectNode team) {
        return (ObjectNode) team.get("coach");
    }

    private ObjectNode coachAttributesOf(ObjectNode team) {
        return (ObjectNode) coachOf(team).get("attributes");
    }

    private ObjectNode playerOf(ObjectNode team, int index) {
        return (ObjectNode) team.get("players").get(index);
    }

    private ObjectNode attributesOf(ObjectNode team, int index) {
        return (ObjectNode) playerOf(team, index).get("attributes");
    }

    private void keepOnlyTheFirstTwelvePlayers(ArrayNode players) {
        for (int index = players.size() - 1; index >= 12; index--) {
            players.remove(index);
        }
    }

    private void assertRefused(ObjectNode team, String field, String... expectedFragments) {
        Path file = TeamFileFixture.write(directory, (JsonNode) team);

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
