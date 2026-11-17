package org.tasktelemetry.matchsimulator.coach.squad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class SquadTest {

    private static final CoachProfile COACH = new CoachProfile("Mister", new CoachAttributes(50));

    @Test
    void acceptsAValidSquadAndKeepsThePlayersInOrder() {
        List<Player> players = List.of(player("Zeta", Role.FORWARD), player("Alfa", Role.GOALKEEPER),
                player("Beta", Role.DEFENDER));

        Squad squad = new Squad("Rossi", COACH, players);

        assertThat(squad.name()).isEqualTo("Rossi");
        assertThat(squad.coach()).isEqualTo(COACH);
        assertThat(squad.players()).containsExactlyElementsOf(players);
    }

    @Test
    void acceptsASquadWithMoreThanOneGoalkeeper() {
        List<Player> players = List.of(player("Alfa", Role.GOALKEEPER), player("Beta", Role.GOALKEEPER));

        assertThat(new Squad("Rossi", COACH, players).players()).hasSize(2);
    }

    @Test
    void copiesThePlayersAndDoesNotAllowToModifyThem() {
        List<Player> players = new ArrayList<>(List.of(player("Alfa", Role.GOALKEEPER)));
        Squad squad = new Squad("Rossi", COACH, players);
        players.add(player("Beta", Role.DEFENDER));

        assertThat(squad.players()).hasSize(1);
        assertThatThrownBy(() -> squad.players().add(player("Gamma", Role.FORWARD)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsABlankSquadName(String name) {
        assertThatThrownBy(() -> new Squad(name, COACH, List.of(player("Alfa", Role.GOALKEEPER))))
                .isInstanceOf(SquadRuleViolation.class)
                .hasMessageContaining("squad name must not be blank")
                .extracting(violation -> ((SquadRuleViolation) violation).rule())
                .isEqualTo(SquadRule.SQUAD_NAME_BLANK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsABlankCoachName(String name) {
        assertThatThrownBy(() -> new CoachProfile(name, new CoachAttributes(50)))
                .isInstanceOf(SquadRuleViolation.class)
                .hasMessageContaining("coach name must not be blank")
                .extracting(violation -> ((SquadRuleViolation) violation).rule())
                .isEqualTo(SquadRule.COACH_NAME_BLANK);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5, 101, 1000})
    void rejectsATacticalReadingOutsideTheAttributeScale(int tacticalReading) {
        assertThatThrownBy(() -> new CoachAttributes(tacticalReading))
                .isInstanceOf(SquadRuleViolation.class)
                .hasMessageContaining("tactical reading must be between 1 and 100, was " + tacticalReading)
                .extracting(violation -> ((SquadRuleViolation) violation).rule())
                .isEqualTo(SquadRule.TACTICAL_READING_OUT_OF_SCALE);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 50, 100})
    void acceptsATacticalReadingOnTheScaleAndItsExtremes(int tacticalReading) {
        assertThat(new CoachAttributes(tacticalReading).tacticalReading()).isEqualTo(tacticalReading);
    }

    @Test
    void carriesTheViolatedValuesInTheViolation() {
        assertThatThrownBy(() -> new CoachAttributes(101))
                .isInstanceOfSatisfying(SquadRuleViolation.class,
                        violation -> assertThat(violation.arguments()).containsExactly(1, 100, 101));
    }

    @Test
    void rejectsARepeatedPlayerName() {
        List<Player> players = List.of(player("Alfa", Role.GOALKEEPER), player("Beta", Role.DEFENDER),
                player("Alfa", Role.FORWARD));

        assertThatThrownBy(() -> new Squad("Rossi", COACH, players))
                .isInstanceOfSatisfying(SquadRuleViolation.class, violation -> {
                    assertThat(violation.rule()).isEqualTo(SquadRule.PLAYER_NAME_REPEATED);
                    assertThat(violation).hasMessageContaining("repeated: Alfa");
                    assertThat(violation.arguments()).containsExactly("Alfa");
                });
    }

    @Test
    void rejectsASquadWithoutAGoalkeeper() {
        List<Player> players = List.of(player("Alfa", Role.DEFENDER), player("Beta", Role.FORWARD));

        assertThatThrownBy(() -> new Squad("Rossi", COACH, players))
                .isInstanceOfSatisfying(SquadRuleViolation.class, violation -> {
                    assertThat(violation.rule()).isEqualTo(SquadRule.GOALKEEPER_MISSING);
                    assertThat(violation).hasMessageContaining("at least one goalkeeper");
                });
    }

    @Test
    void rejectsAnEmptySquadForTheMissingGoalkeeper() {
        assertThatThrownBy(() -> new Squad("Rossi", COACH, List.of()))
                .isInstanceOfSatisfying(SquadRuleViolation.class,
                        violation -> assertThat(violation.rule()).isEqualTo(SquadRule.GOALKEEPER_MISSING));
    }

    @Test
    void rejectsMissingValues() {
        List<Player> players = List.of(player("Alfa", Role.GOALKEEPER));

        assertThatThrownBy(() -> new Squad(null, COACH, players)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Squad("Rossi", null, players)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Squad("Rossi", COACH, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CoachProfile(null, new CoachAttributes(50)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CoachProfile("Mister", null)).isInstanceOf(NullPointerException.class);
    }

    private static Player player(String name, Role role) {
        PlayerAttributes attributes = new PlayerAttributes(50, 50, 50, 50, 50, 50, 50, 50, 50);
        return new Player(name, role, attributes);
    }
}
