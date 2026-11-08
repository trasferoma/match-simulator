package org.tasktelemetry.matchsimulator.calibration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

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
    void everyTeamHasTheFourFourTwoCompositionWithSevenOnTheBench() {
        List<Team> teams = generator.generate(7L);

        assertThat(teams).hasSize(20);
        for (Team team : teams) {
            assertThat(countOf(team.starters(), Role.GOALKEEPER)).isEqualTo(1);
            assertThat(countOf(team.starters(), Role.DEFENDER)).isEqualTo(4);
            assertThat(countOf(team.starters(), Role.MIDFIELDER)).isEqualTo(4);
            assertThat(countOf(team.starters(), Role.FORWARD)).isEqualTo(2);
            assertThat(team.bench()).hasSize(7);
            assertThat(countOf(team.bench(), Role.GOALKEEPER)).isEqualTo(1);
            assertThat(countOf(team.bench(), Role.DEFENDER)).isEqualTo(2);
            assertThat(countOf(team.bench(), Role.MIDFIELDER)).isEqualTo(2);
            assertThat(countOf(team.bench(), Role.FORWARD)).isEqualTo(2);
        }
    }

    @Test
    void theStylesRotateAmongTheFiveStyles() {
        List<Team> teams = generator.generate(7L);

        assertThat(teams.get(0).style()).isEqualTo(teams.get(5).style());
        assertThat(teams.get(0).style()).isNotEqualTo(teams.get(1).style());
        assertThat(teams.stream().map(Team::style).distinct().count()).isEqualTo(5);
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
            List<Team> teams = generator.generate(seed);
            for (int index = 0; index < teams.size(); index++) {
                assertVariationWithinBound(teams.get(index), generator.strengthOf(index));
            }
        }
    }

    @Test
    void everyAttributeIsInTheScaleWithoutAnyClippingAtTheWeakestAndStrongestStrength() {
        List<Team> teams = generator.generate(11L);
        Team weakest = teams.get(0);
        Team strongest = teams.get(teams.size() - 1);

        for (Team team : List.of(weakest, strongest)) {
            for (Player player : squadOf(team)) {
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

    private void assertVariationWithinBound(Team team, int strength) {
        int bound = config.individualVariation();
        for (Player player : squadOf(team)) {
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

    private List<Player> squadOf(Team team) {
        return Stream.concat(team.starters().stream(), team.bench().stream()).toList();
    }

    private long countOf(List<Player> players, Role role) {
        return players.stream().filter(player -> player.role() == role).count();
    }
}
