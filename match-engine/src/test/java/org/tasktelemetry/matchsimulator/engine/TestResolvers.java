package org.tasktelemetry.matchsimulator.engine;

import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.ShotConfig;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.resolution.CounterAttackResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.Matchups;
import org.tasktelemetry.matchsimulator.engine.resolution.PlayerPicker;
import org.tasktelemetry.matchsimulator.engine.resolution.PossessionActionResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.CrossResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ExpectedGoalsModel;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ShotQualityScale;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ShotResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.stoppage.FoulResolver;

public final class TestResolvers {

    private TestResolvers() {
    }

    public static ShotResolver shotResolver(EngineConfig config, PitchLayout layout, MatchRandom random) {
        return shotResolver(config.shot(), layout, random);
    }

    public static CrossResolver crossResolver(EngineConfig config, PitchLayout layout, MatchRandom random,
            ShotResolver shotResolver) {
        PlayerPicker picker = new PlayerPicker(random);
        return new CrossResolver(config.cross(), config.playStyles(), layout, random, picker, shotResolver);
    }

    public static Matchups matchups(EngineConfig config, PitchLayout layout, MatchRandom random) {
        PlayerPicker picker = new PlayerPicker(random);
        return new Matchups(layout, picker, config.playStyles(), config.homeAdvantage(),
                config.lineup().inferiorityMultiplier());
    }

    public static CounterAttackResolver counterAttackResolver(EngineConfig config, PitchLayout layout,
            MatchRandom random, ShotResolver shotResolver) {
        Matchups matchups = matchups(config, layout, random);
        return new CounterAttackResolver(config.counterAttack(), config.playStyles(), layout, random, matchups,
                shotResolver);
    }

    public static PossessionActionResolver possessionResolver(EngineConfig config, PitchLayout layout,
            MatchRandom random) {
        Matchups matchups = matchups(config, layout, random);
        ShotResolver shotResolver = shotResolver(config, layout, random);
        CrossResolver crossResolver = crossResolver(config, layout, random, shotResolver);
        FoulResolver foulResolver = new FoulResolver(config.foul(), layout, random);
        return new PossessionActionResolver(config, layout, random, matchups, shotResolver, crossResolver,
                foulResolver);
    }

    public static ShotResolver shotResolver(ShotConfig shotConfig, PitchLayout layout, MatchRandom random) {
        ExpectedGoalsModel model = new ExpectedGoalsModel(shotConfig);
        ShotQualityScale qualityScale = new ShotQualityScale(shotConfig);
        return new ShotResolver(shotConfig, layout, random, model, qualityScale);
    }
}
