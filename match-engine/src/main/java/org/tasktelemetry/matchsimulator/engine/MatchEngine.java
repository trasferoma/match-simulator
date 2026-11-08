package org.tasktelemetry.matchsimulator.engine;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.ShotConfig;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.resolution.PlayerPicker;
import org.tasktelemetry.matchsimulator.engine.resolution.PossessionActionResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.CrossResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ExpectedGoalsModel;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ShotQualityScale;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ShotResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.stoppage.SetPieceResolver;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public final class MatchEngine {

    private final EngineConfig config;

    public MatchEngine(EngineConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public Match start(Team home, Team away, long seed) {
        Objects.requireNonNull(home, "home team must not be null");
        Objects.requireNonNull(away, "away team must not be null");
        MatchRandom random = new MatchRandom(seed);
        PitchLayout layout = PitchLayout.standard();
        PlayerPicker picker = new PlayerPicker(random);
        ShotResolver shotResolver = newShotResolver(layout, random);
        CrossResolver crossResolver = new CrossResolver(config.cross(), layout, random, picker, shotResolver);
        PossessionActionResolver possessionResolver = new PossessionActionResolver(config, layout, random, picker,
                shotResolver, crossResolver);
        SetPieceResolver setPieceResolver = new SetPieceResolver(config.setPiece(), random, crossResolver);
        Map<MatchSide, TeamOnPitch> teams = teamsOnPitch(home, away);
        return new Match(config, layout, teams, possessionResolver, setPieceResolver, crossResolver);
    }

    private ShotResolver newShotResolver(PitchLayout layout, MatchRandom random) {
        ShotConfig shotConfig = config.shot();
        ExpectedGoalsModel model = new ExpectedGoalsModel(shotConfig);
        ShotQualityScale qualityScale = new ShotQualityScale(shotConfig);
        return new ShotResolver(shotConfig, layout, random, model, qualityScale);
    }

    private Map<MatchSide, TeamOnPitch> teamsOnPitch(Team home, Team away) {
        Map<MatchSide, TeamOnPitch> teams = new EnumMap<>(MatchSide.class);
        TeamOnPitch homeOnPitch = TeamOnPitch.startingWith(home);
        TeamOnPitch awayOnPitch = TeamOnPitch.startingWith(away);
        teams.put(MatchSide.HOME, homeOnPitch);
        teams.put(MatchSide.AWAY, awayOnPitch);
        return teams;
    }
}
