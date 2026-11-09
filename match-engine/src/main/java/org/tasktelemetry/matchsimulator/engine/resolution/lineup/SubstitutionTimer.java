package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.config.DurationRange;
import org.tasktelemetry.matchsimulator.engine.config.LineupConfig;

public final class SubstitutionTimer {

    private final LineupConfig config;
    private final MatchRandom random;

    public SubstitutionTimer(LineupConfig config, MatchRandom random) {
        this.config = config;
        this.random = random;
    }

    public int drawSubstitutionSeconds() {
        DurationRange range = config.substitutionDuration();
        return random.nextIntInclusive(range.minSeconds(), range.maxSeconds());
    }
}
