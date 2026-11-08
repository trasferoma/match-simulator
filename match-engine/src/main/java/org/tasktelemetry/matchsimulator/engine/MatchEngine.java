package org.tasktelemetry.matchsimulator.engine;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.ShotConfig;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.resolution.CounterAttackResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.Matchups;
import org.tasktelemetry.matchsimulator.engine.resolution.PlayerPicker;
import org.tasktelemetry.matchsimulator.engine.resolution.PossessionActionResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.lineup.DismissalResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.lineup.LineupRepair;
import org.tasktelemetry.matchsimulator.engine.resolution.lineup.MinimumPlayersRule;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.CrossResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ExpectedGoalsModel;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ShotQualityScale;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ShotResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.stoppage.FoulResolver;
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
        ActionResolvers resolvers = newActionResolvers(layout, random);
        Map<MatchSide, TeamOnPitch> teams = teamsOnPitch(home, away);
        return new Match(config, layout, teams, resolvers);
    }

    private ActionResolvers newActionResolvers(PitchLayout layout, MatchRandom random) {
        PlayerPicker picker = new PlayerPicker(random);
        ShotResolver shotResolver = newShotResolver(layout, random);
        CrossResolver crossResolver = new CrossResolver(config.cross(), config.playStyles(), layout, random, picker,
                shotResolver);
        FoulResolver foulResolver = new FoulResolver(config.foul(), layout, random);
        Matchups matchups = new Matchups(layout, picker, config.playStyles(), config.homeAdvantage(),
                config.lineup().inferiorityMultiplier());
        PossessionActionResolver possessionResolver = new PossessionActionResolver(config, layout, random, matchups,
                shotResolver, crossResolver, foulResolver);
        CounterAttackResolver counterAttackResolver = new CounterAttackResolver(config.counterAttack(),
                config.playStyles(), layout, random, matchups, shotResolver);
        SetPieceResolver setPieceResolver = new SetPieceResolver(config.setPiece(), layout, random, crossResolver,
                shotResolver);
        LineupRepair lineupRepair = new LineupRepair(config.lineup());
        MinimumPlayersRule minimumPlayers = new MinimumPlayersRule();
        DismissalResolver dismissalResolver = new DismissalResolver(minimumPlayers, lineupRepair);
        return new ActionResolvers(possessionResolver, counterAttackResolver, setPieceResolver, crossResolver,
                foulResolver, dismissalResolver, minimumPlayers);
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
