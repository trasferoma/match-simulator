package org.tasktelemetry.matchsimulator.calibration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.config.FormationCatalog;
import org.tasktelemetry.matchsimulator.coach.lineup.FeasibleFormations;
import org.tasktelemetry.matchsimulator.coach.squad.CoachProfile;
import org.tasktelemetry.matchsimulator.coach.squad.Squad;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class LeagueGeneratorTest {

    private static final List<Integer> EXPECTED_STRENGTHS = List.of(55, 56, 58, 59, 60, 62, 63, 64, 66, 67, 68, 69,
            71, 72, 73, 75, 76, 77, 79, 80);

    private static final List<List<Integer>> WEAKEST_SQUAD_ATTRIBUTES_OF_SEED_7 = List.of(
            List.of(53, 52, 45, 48, 62, 47, 57, 71),
            List.of(52, 47, 49, 66, 68, 56, 63, 40),
            List.of(51, 49, 50, 63, 66, 54, 60, 45),
            List.of(55, 49, 49, 66, 62, 57, 61, 40),
            List.of(55, 51, 50, 67, 63, 58, 61, 44),
            List.of(64, 58, 54, 58, 59, 55, 49, 28),
            List.of(66, 58, 55, 61, 57, 60, 48, 33),
            List.of(64, 62, 54, 57, 61, 54, 53, 30),
            List.of(68, 59, 54, 60, 59, 60, 49, 29),
            List.of(54, 62, 69, 44, 58, 65, 60, 29),
            List.of(54, 67, 67, 43, 57, 61, 60, 28),
            List.of(51, 46, 46, 46, 60, 49, 56, 72),
            List.of(53, 47, 46, 64, 64, 52, 60, 46),
            List.of(56, 52, 50, 67, 66, 55, 61, 44),
            List.of(68, 62, 57, 57, 60, 55, 48, 33),
            List.of(70, 63, 54, 62, 60, 58, 50, 34),
            List.of(60, 66, 65, 38, 59, 61, 57, 34),
            List.of(60, 66, 70, 41, 58, 64, 62, 29));

    private static final List<List<Integer>> STRONGEST_SQUAD_ATTRIBUTES_OF_SEED_7 = List.of(
            List.of(73, 75, 72, 77, 86, 75, 84, 96),
            List.of(80, 73, 71, 91, 93, 78, 90, 71),
            List.of(78, 76, 72, 88, 92, 78, 90, 70),
            List.of(78, 72, 70, 88, 91, 83, 91, 70),
            List.of(80, 77, 75, 88, 89, 81, 86, 65),
            List.of(95, 84, 77, 82, 86, 83, 73, 57),
            List.of(93, 87, 78, 83, 87, 84, 73, 54),
            List.of(92, 85, 77, 81, 85, 82, 73, 55),
            List.of(95, 86, 78, 85, 83, 81, 76, 54),
            List.of(83, 93, 95, 69, 80, 90, 83, 55),
            List.of(82, 92, 92, 63, 79, 90, 86, 54),
            List.of(75, 75, 71, 71, 90, 72, 87, 100),
            List.of(81, 75, 71, 92, 91, 80, 85, 67),
            List.of(76, 76, 73, 89, 87, 80, 85, 67),
            List.of(93, 86, 83, 86, 87, 84, 76, 59),
            List.of(90, 86, 82, 81, 85, 83, 76, 58),
            List.of(83, 87, 93, 66, 85, 88, 82, 53),
            List.of(80, 91, 94, 64, 79, 85, 81, 55));

    private final BenchConfig config = BenchConfig.defaults();
    private final RoleProfiles profiles = RoleProfiles.defaults();
    private final LeagueGenerator generator = new LeagueGenerator(config, profiles);

    @Test
    void theStrengthsAreTheIntegersOfTheSpecification() {
        List<Integer> strengths = IntStream.range(0, config.teamCount()).mapToObj(generator::strengthOf).toList();

        assertThat(strengths).isEqualTo(EXPECTED_STRENGTHS);
    }

    @Test
    void everySquadHasEighteenPlayersWithTwoGoalkeepersSixDefendersSixMidfieldersAndFourForwards() {
        List<Squad> squads = generator.generate(7L);

        assertThat(squads).hasSize(20);
        for (Squad squad : squads) {
            assertThat(squad.players()).hasSize(18);
            assertThat(countOf(squad.players(), Role.GOALKEEPER)).isEqualTo(2);
            assertThat(countOf(squad.players(), Role.DEFENDER)).isEqualTo(6);
            assertThat(countOf(squad.players(), Role.MIDFIELDER)).isEqualTo(6);
            assertThat(countOf(squad.players(), Role.FORWARD)).isEqualTo(4);
        }
    }

    @Test
    void everyFormationOfTheCatalogIsFeasibleWithEverySquad() {
        FormationCatalog catalog = CoachConfig.defaults().formations();
        FeasibleFormations feasibleFormations = new FeasibleFormations(catalog);

        for (Squad squad : generator.generate(7L)) {
            assertThat(feasibleFormations.amongPlayers(squad.players())).isEqualTo(catalog.formations());
        }
    }

    @Test
    void everySquadHasACoachWithTheNeutralTacticalReadingAndAGeneratedName() {
        List<Squad> squads = generator.generate(7L);

        assertThat(config.coachTacticalReading()).isEqualTo(50);
        for (int index = 0; index < squads.size(); index++) {
            CoachProfile coach = squads.get(index).coach();
            assertThat(coach.attributes().tacticalReading()).isEqualTo(config.coachTacticalReading());
            assertThat(coach.name()).isEqualTo(generator.coachNameOf(index));
        }
        assertThat(squads.stream().map(squad -> squad.coach().name()).distinct().count()).isEqualTo(20);
    }

    @Test
    void theTacticalReadingOfTheCoachesComesFromTheConfiguration() {
        BenchConfig sharpCoaches = new BenchConfig(4, 55, 80, 2, 42L, 3, 3.0, 80);
        LeagueGenerator sharpGenerator = new LeagueGenerator(sharpCoaches, profiles);

        List<Squad> squads = sharpGenerator.generate(7L);

        assertThat(squads).allSatisfy(squad -> assertThat(squad.coach().attributes().tacticalReading()).isEqualTo(80));
    }

    @Test
    void theOffsetsOfEveryProfileAddUpToZero() {
        for (Role role : Role.values()) {
            AttributeOffsets offsets = profiles.offsetsOf(role);
            int sum = offsets.passing() + offsets.dribbling() + offsets.shooting() + offsets.tackling()
                    + offsets.positioning() + offsets.pace() + offsets.aerial() + offsets.goalkeeping();

            assertThat(sum).as("sum of the offsets of %s", role).isZero();
        }
    }

    @Test
    void theDefaultStaminaOffsetsCoverEveryRoleAndAreNotAllTheSame() {
        List<Integer> offsets = Arrays.stream(Role.values()).map(profiles::staminaOffsetOf).toList();

        assertThat(profiles.staminaOffsetByRole()).containsOnlyKeys(Role.values());
        assertThat(offsets.stream().distinct().count()).isGreaterThan(1);
        assertThat(offsets).containsExactly(0, 0, 8, -4);
    }

    @Test
    void theStaminaOfEveryPlayerIsTheStrengthOfTheSquadPlusTheOffsetOfTheRole() {
        BenchConfig strengthSeventy = new BenchConfig(2, 70, 71, 1, 42L, 3, 3.0, 50);
        LeagueGenerator seventyGenerator = new LeagueGenerator(strengthSeventy, profiles);

        Squad squad = seventyGenerator.generateSquad(0, 70, new MatchRandom(7L));

        Map<Role, Integer> staminaByRole = new EnumMap<>(Role.class);
        for (Player player : squad.players()) {
            staminaByRole.put(player.role(), player.attributes().stamina());
        }
        assertThat(squad.players()).allSatisfy(player -> assertThat(player.attributes().stamina())
                .isEqualTo(staminaByRole.get(player.role())));
        assertThat(staminaByRole).containsEntry(Role.GOALKEEPER, 70).containsEntry(Role.DEFENDER, 70)
                .containsEntry(Role.MIDFIELDER, 78).containsEntry(Role.FORWARD, 66);
    }

    @Test
    void theDefaultStaminaStaysInTheScaleFromTheWeakestForwardToTheStrongestMidfielder() {
        List<Squad> squads = generator.generate(11L);
        List<Integer> staminas = squads.stream()
                .flatMap(squad -> squad.players().stream())
                .map(player -> player.attributes().stamina())
                .toList();

        assertThat(staminas).allMatch(stamina -> stamina >= 51 && stamina <= 88);
    }

    @Test
    void aStaminaOffsetThatTakesAStrengthOfTheBenchOutOfTheScaleIsRejectedNamingRoleStrengthAndValue() {
        Map<Role, Integer> staminaOffsets = new EnumMap<>(profiles.staminaOffsetByRole());
        staminaOffsets.put(Role.MIDFIELDER, 21);
        RoleProfiles tooHigh = new RoleProfiles(profiles.offsetsByRole(), staminaOffsets);

        assertThatThrownBy(() -> new LeagueGenerator(config, tooHigh))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MIDFIELDER")
                .hasMessageContaining("strength 80")
                .hasMessageContaining("was 101");
    }

    @Test
    void aStaminaOffsetThatTakesTheWeakestStrengthBelowTheScaleIsRejected() {
        Map<Role, Integer> staminaOffsets = new EnumMap<>(profiles.staminaOffsetByRole());
        staminaOffsets.put(Role.FORWARD, -55);
        RoleProfiles tooLow = new RoleProfiles(profiles.offsetsByRole(), staminaOffsets);

        assertThatThrownBy(() -> new LeagueGenerator(config, tooLow))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FORWARD")
                .hasMessageContaining("strength 55")
                .hasMessageContaining("was 0");
    }

    @Test
    void roleProfilesWithoutAStaminaOffsetForSomeRoleAreRejected() {
        Map<Role, Integer> staminaOffsets = new EnumMap<>(profiles.staminaOffsetByRole());
        staminaOffsets.remove(Role.DEFENDER);

        assertThatThrownBy(() -> new RoleProfiles(profiles.offsetsByRole(), staminaOffsets))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEFENDER");
    }

    @Test
    void aProfileWhoseOffsetsDoNotAddUpToZeroIsRejected() {
        assertThatThrownBy(() -> new AttributeOffsets(1, 0, 0, 0, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("add up to zero");
    }

    @Test
    void everyAttributeStaysWithinTheVariationAroundStrengthPlusOffset() {
        for (long seed = 1; seed <= 20; seed++) {
            List<Squad> squads = generator.generate(seed);
            for (int index = 0; index < squads.size(); index++) {
                assertVariationWithinBound(squads.get(index), generator.strengthOf(index));
            }
        }
    }

    @Test
    void everyAttributeIsInTheScaleWithoutAnyClippingAtTheWeakestAndStrongestStrength() {
        List<Squad> squads = generator.generate(11L);
        Squad weakest = squads.get(0);
        Squad strongest = squads.get(squads.size() - 1);

        for (Squad squad : List.of(weakest, strongest)) {
            for (Player player : squad.players()) {
                assertThat(attributeValues(player.attributes()))
                        .allMatch(value -> value >= PlayerAttributes.MIN_VALUE && value <= PlayerAttributes.MAX_VALUE);
            }
        }
        assertThat(generator.strengthOf(0)).isEqualTo(55);
        assertThat(generator.strengthOf(19)).isEqualTo(80);
    }

    @Test
    void theEightExistingAttributesOfTheWeakestAndStrongestSquadAreTheOnesFixedBeforeStamina() {
        List<Squad> squads = generator.generate(7L);

        assertThat(attributeRows(squads.get(0))).isEqualTo(WEAKEST_SQUAD_ATTRIBUTES_OF_SEED_7);
        assertThat(attributeRows(squads.get(squads.size() - 1))).isEqualTo(STRONGEST_SQUAD_ATTRIBUTES_OF_SEED_7);
    }

    @Test
    void theSameSeedGivesTheSameLeagueAndAnotherSeedADifferentOne() {
        assertThat(generator.generate(5L)).isEqualTo(generator.generate(5L));
        assertThat(generator.generate(5L)).isNotEqualTo(generator.generate(6L));
    }

    private void assertVariationWithinBound(Squad squad, int strength) {
        int bound = config.individualVariation();
        for (Player player : squad.players()) {
            AttributeOffsets offsets = profiles.offsetsOf(player.role());
            List<Integer> centres = attributeValues(offsets, strength);
            List<Integer> values = attributeValues(player.attributes());
            for (int position = 0; position < values.size(); position++) {
                int deviation = values.get(position) - centres.get(position);
                assertThat(Math.abs(deviation)).as("%s attribute %d", player.name(), position).isLessThanOrEqualTo(bound);
            }
        }
    }

    private List<Integer> attributeValues(AttributeOffsets offsets, int strength) {
        return List.of(strength + offsets.passing(), strength + offsets.dribbling(),
                strength + offsets.shooting(), strength + offsets.tackling(), strength + offsets.positioning(),
                strength + offsets.pace(), strength + offsets.aerial(), strength + offsets.goalkeeping());
    }

    private List<Integer> attributeValues(PlayerAttributes attributes) {
        return List.of(attributes.passing(), attributes.dribbling(), attributes.shooting(), attributes.tackling(),
                attributes.positioning(), attributes.pace(), attributes.aerial(), attributes.goalkeeping());
    }

    private List<List<Integer>> attributeRows(Squad squad) {
        return squad.players().stream().map(player -> attributeValues(player.attributes())).toList();
    }

    private long countOf(List<Player> players, Role role) {
        return players.stream().filter(player -> player.role() == role).count();
    }
}
