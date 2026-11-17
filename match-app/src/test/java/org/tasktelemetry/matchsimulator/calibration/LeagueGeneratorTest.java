package org.tasktelemetry.matchsimulator.calibration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.config.FormationCatalog;
import org.tasktelemetry.matchsimulator.coach.lineup.FeasibleFormations;
import org.tasktelemetry.matchsimulator.coach.squad.CoachProfile;
import org.tasktelemetry.matchsimulator.coach.squad.Squad;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class LeagueGeneratorTest {

    private static final List<Integer> EXPECTED_STRENGTHS = List.of(55, 56, 58, 59, 60, 62, 63, 64, 66, 67, 68, 69,
            71, 72, 73, 75, 76, 77, 79, 80);

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

    private long countOf(List<Player> players, Role role) {
        return players.stream().filter(player -> player.role() == role).count();
    }
}
