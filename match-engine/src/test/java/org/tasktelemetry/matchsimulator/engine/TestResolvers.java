package org.tasktelemetry.matchsimulator.engine;

import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.FoulConfig;
import org.tasktelemetry.matchsimulator.engine.config.ShotConfig;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.resolution.CounterAttackResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.Matchups;
import org.tasktelemetry.matchsimulator.engine.resolution.OutOfPositionPenalty;
import org.tasktelemetry.matchsimulator.engine.resolution.PlayerPicker;
import org.tasktelemetry.matchsimulator.engine.resolution.PossessionActionResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.ZonePresence;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.CrossResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ExpectedGoalsModel;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ShotQualityScale;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ShotResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.stoppage.FoulResolver;

public final class TestResolvers {

    private TestResolvers() {
    }

    public static ShotResolver shotResolver(EngineConfig config, PitchLayout layout, MatchRandom random) {
        Matchups matchups = matchups(config, layout, random);
        return shotResolver(config.shot(), layout, random, matchups);
    }

    public static ShotResolver shotResolver(ShotConfig shotConfig, PitchLayout layout, MatchRandom random) {
        EngineConfig neutralVenue = TestConfigs.withoutHomeAdvantage(EngineConfig.defaults());
        Matchups matchups = matchups(neutralVenue, layout, random);
        return shotResolver(shotConfig, layout, random, matchups);
    }

    public static ShotResolver shotResolver(ShotConfig shotConfig, PitchLayout layout, MatchRandom random,
            Matchups matchups) {
        ExpectedGoalsModel model = new ExpectedGoalsModel(shotConfig);
        ShotQualityScale qualityScale = new ShotQualityScale(shotConfig);
        return new ShotResolver(shotConfig, layout, random, model, qualityScale, matchups);
    }

    public static CrossResolver crossResolver(EngineConfig config, PitchLayout layout, MatchRandom random,
            ShotResolver shotResolver) {
        ZonePresence zonePresence = zonePresence(config);
        PlayerPicker picker = new PlayerPicker(random, zonePresence);
        Matchups matchups = matchups(config, layout, random);
        return new CrossResolver(config.cross(), config.playStyles(), layout, random, picker, shotResolver,
                matchups, zonePresence);
    }

    public static ZonePresence zonePresence(EngineConfig config) {
        return new ZonePresence(config.zonePresence());
    }

    public static PlayerPicker playerPicker(EngineConfig config, MatchRandom random) {
        return new PlayerPicker(random, zonePresence(config));
    }

    public static Matchups matchups(EngineConfig config, PitchLayout layout, MatchRandom random) {
        ZonePresence zonePresence = zonePresence(config);
        PlayerPicker picker = new PlayerPicker(random, zonePresence);
        OutOfPositionPenalty outOfPosition = outOfPositionPenalty(config);
        return new Matchups(layout, picker, zonePresence, config.playStyles(), config.homeAdvantage(),
                outOfPosition);
    }

    public static OutOfPositionPenalty outOfPositionPenalty(EngineConfig config) {
        return new OutOfPositionPenalty(config.lineup().outOfPositionMultiplier());
    }

    public static FoulResolver foulResolver(FoulConfig foulConfig, PitchLayout layout, MatchRandom random) {
        OutOfPositionPenalty outOfPosition = outOfPositionPenalty(EngineConfig.defaults());
        return new FoulResolver(foulConfig, layout, random, outOfPosition);
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
        FoulResolver foulResolver = foulResolver(config.foul(), layout, random);
        ZonePresence zonePresence = zonePresence(config);
        return new PossessionActionResolver(config, layout, random, matchups, shotResolver, crossResolver,
                foulResolver, zonePresence);
    }
}
