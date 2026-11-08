package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class MatchRandomTest {

    private static final long SEED = 42L;
    private static final int TRIALS = 100_000;

    @Test
    void theSameSeedGivesTheSameSequence() {
        MatchRandom first = new MatchRandom(SEED);
        MatchRandom second = new MatchRandom(SEED);

        double[] firstValues = draw(first, 50);
        double[] secondValues = draw(second, 50);

        assertThat(firstValues).containsExactly(secondValues);
    }

    @Test
    void differentSeedsGiveDifferentSequences() {
        double[] firstValues = draw(new MatchRandom(1L), 50);
        double[] secondValues = draw(new MatchRandom(2L), 50);

        assertThat(firstValues).isNotEqualTo(secondValues);
    }

    @Test
    void usesTheSpecifiedAlgorithm() {
        RandomGenerator reference = RandomGeneratorFactory.of("L64X128MixRandom").create(SEED);
        MatchRandom random = new MatchRandom(SEED);

        assertThat(random.nextDouble()).isEqualTo(reference.nextDouble());
        assertThat(random.nextDouble()).isEqualTo(reference.nextDouble());
    }

    @Test
    void nextDoubleStaysInTheHalfOpenUnitInterval() {
        double[] values = draw(new MatchRandom(SEED), TRIALS);

        assertThat(Arrays.stream(values).min().orElseThrow()).isGreaterThanOrEqualTo(0.0);
        assertThat(Arrays.stream(values).max().orElseThrow()).isLessThan(1.0);
    }

    @Test
    void anEventWithProbabilityZeroNeverOccursAndOneAlwaysOccurs() {
        MatchRandom random = new MatchRandom(SEED);

        long occurrencesAtZero = IntStream.range(0, TRIALS).filter(index -> random.occurs(0.0)).count();
        long occurrencesAtOne = IntStream.range(0, TRIALS).filter(index -> random.occurs(1.0)).count();

        assertThat(occurrencesAtZero).isZero();
        assertThat(occurrencesAtOne).isEqualTo(TRIALS);
    }

    @Test
    void anEventOccursWithTheGivenProbabilityWithinOnePercentagePoint() {
        MatchRandom random = new MatchRandom(SEED);

        long occurrences = IntStream.range(0, TRIALS).filter(index -> random.occurs(0.3)).count();

        assertThat((double) occurrences / TRIALS).isCloseTo(0.3, within(0.01));
    }

    @Test
    void nextIntInclusiveReachesBothBoundsAndNeverExceedsThem() {
        MatchRandom random = new MatchRandom(SEED);

        int[] values = IntStream.range(0, TRIALS).map(index -> random.nextIntInclusive(4, 12)).toArray();

        assertThat(Arrays.stream(values).min().orElseThrow()).isEqualTo(4);
        assertThat(Arrays.stream(values).max().orElseThrow()).isEqualTo(12);
    }

    private double[] draw(MatchRandom random, int count) {
        return IntStream.range(0, count).mapToDouble(index -> random.nextDouble()).toArray();
    }
}
