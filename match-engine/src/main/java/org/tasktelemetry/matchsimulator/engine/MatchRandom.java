package org.tasktelemetry.matchsimulator.engine;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

public final class MatchRandom {

    private static final String ALGORITHM = "L64X128MixRandom";

    private final RandomGenerator generator;

    public MatchRandom(long seed) {
        RandomGeneratorFactory<RandomGenerator> factory = RandomGeneratorFactory.of(ALGORITHM);
        this.generator = factory.create(seed);
    }

    public double nextDouble() {
        return generator.nextDouble();
    }

    public boolean occurs(double probability) {
        return generator.nextDouble() < probability;
    }

    public int nextIntInclusive(int minimum, int maximum) {
        return generator.nextInt(minimum, maximum + 1);
    }
}
