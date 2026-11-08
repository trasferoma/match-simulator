package org.tasktelemetry.matchsimulator.engine;

import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.ShotConfig;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.resolution.PlayerPicker;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.CrossResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ExpectedGoalsModel;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ShotQualityScale;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ShotResolver;

public final class TestResolvers {

    private TestResolvers() {
    }

    public static ShotResolver shotResolver(EngineConfig config, PitchLayout layout, MatchRandom random) {
        return shotResolver(config.shot(), layout, random);
    }

    public static CrossResolver crossResolver(EngineConfig config, PitchLayout layout, MatchRandom random,
            ShotResolver shotResolver) {
        PlayerPicker picker = new PlayerPicker(random);
        return new CrossResolver(config.cross(), layout, random, picker, shotResolver);
    }

    public static ShotResolver shotResolver(ShotConfig shotConfig, PitchLayout layout, MatchRandom random) {
        ExpectedGoalsModel model = new ExpectedGoalsModel(shotConfig);
        ShotQualityScale qualityScale = new ShotQualityScale(shotConfig);
        return new ShotResolver(shotConfig, layout, random, model, qualityScale);
    }
}
