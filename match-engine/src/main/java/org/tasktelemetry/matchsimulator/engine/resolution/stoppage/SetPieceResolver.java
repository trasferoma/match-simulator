package org.tasktelemetry.matchsimulator.engine.resolution.stoppage;

import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.config.DurationRange;
import org.tasktelemetry.matchsimulator.engine.config.SetPieceConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.ActionOutcome;
import org.tasktelemetry.matchsimulator.engine.resolution.PlayerPicker;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.CrossResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ShotResolver;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.state.SetPieceKind;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class SetPieceResolver {

    private static final ZoneRole TAKER_ZONE_ROLE = ZoneRole.MIDFIELD;

    private final SetPieceConfig config;
    private final PitchLayout layout;
    private final MatchRandom random;
    private final PlayerPicker picker;
    private final CrossResolver crossResolver;
    private final ShotResolver shotResolver;

    public SetPieceResolver(SetPieceConfig config, PitchLayout layout, MatchRandom random, PlayerPicker picker,
            CrossResolver crossResolver, ShotResolver shotResolver) {
        this.config = config;
        this.layout = layout;
        this.random = random;
        this.picker = picker;
        this.crossResolver = crossResolver;
        this.shotResolver = shotResolver;
    }

    public ActionOutcome resolve(MatchState state, Score score, TeamOnPitch restartingTeam,
            TeamOnPitch opponents) {
        return switch (state.setPiece()) {
            case KICK_OFF -> kickOffAfterGoal(state);
            case GOAL_KICK -> goalKick(state, restartingTeam);
            case CORNER -> cornerKick(state, restartingTeam);
            case FREE_KICK -> freeKick(state, score, restartingTeam, opponents);
            case OFFSIDE_FREE_KICK -> offsideFreeKick(state, restartingTeam);
            case PENALTY -> shotResolver.resolvePenalty(state, score, restartingTeam, opponents);
        };
    }

    private ActionOutcome kickOffAfterGoal(MatchState state) {
        Participants nobody = Participants.nobody();
        EventDetail detail = EventDetail.none();
        MatchState next = state.inOpenPlay();
        return new ActionOutcome(EventType.KICK_OFF_AFTER_GOAL, nobody, next, 0, detail);
    }

    private ActionOutcome cornerKick(MatchState state, TeamOnPitch restartingTeam) {
        Player taker = crossResolver.pickCrosser(restartingTeam);
        DurationRange range = config.cornerDuration();
        int duration = random.nextIntInclusive(range.minSeconds(), range.maxSeconds());
        MatchState next = state.withCrossInFlightFrom(taker);
        return ActionOutcome.byPlayer(EventType.CORNER_KICK, taker, next, duration);
    }

    private ActionOutcome goalKick(MatchState state, TeamOnPitch restartingTeam) {
        Player goalkeeper = restartingTeam.goalkeeper();
        DurationRange range = config.goalKickDuration();
        int duration = random.nextIntInclusive(range.minSeconds(), range.maxSeconds());
        MatchState next = state.inOpenPlay();
        return ActionOutcome.byPlayer(EventType.GOAL_KICK, goalkeeper, next, duration);
    }

    private ActionOutcome freeKick(MatchState state, Score score, TeamOnPitch restartingTeam,
            TeamOnPitch opponents) {
        if (!isFromAttackingThird(state)) {
            Player taker = pickTaker(restartingTeam);
            return restartFromFreeKick(state, taker);
        }
        if (decidesToShootDirectly()) {
            Player shooter = pickTaker(restartingTeam);
            ActionOutcome shot = shotResolver.resolveShot(state, score, restartingTeam, shooter,
                    ShotKind.DIRECT_FREE_KICK, opponents);
            return shot.afterWaiting(drawFreeKickDuration());
        }
        Player crosser = crossResolver.pickCrosser(restartingTeam);
        return crossFromFreeKick(state, crosser);
    }

    private ActionOutcome offsideFreeKick(MatchState state, TeamOnPitch restartingTeam) {
        Player taker = pickTaker(restartingTeam);
        int duration = drawFreeKickDuration();
        MatchState next = state.inOpenPlay();
        return ActionOutcome.byPlayer(EventType.OFFSIDE_FREE_KICK, taker, next, duration);
    }

    private ActionOutcome restartFromFreeKick(MatchState state, Player taker) {
        int duration = drawFreeKickDuration();
        MatchState next = state.inOpenPlay();
        return ActionOutcome.byPlayer(EventType.FREE_KICK, taker, next, duration);
    }

    private ActionOutcome crossFromFreeKick(MatchState state, Player taker) {
        MatchSide defendingSide = state.possession().opposite();
        Zone opponentBox = layout.ownBoxZone(defendingSide);
        MatchState inTheBox = MatchState.forSetPiece(state.possession(), opponentBox, SetPieceKind.FREE_KICK);
        MatchState next = inTheBox.withCrossInFlightFrom(taker);
        int duration = drawFreeKickDuration();
        return ActionOutcome.byPlayer(EventType.FREE_KICK_CROSS, taker, next, duration);
    }

    private Player pickTaker(TeamOnPitch restartingTeam) {
        return picker.pickFrom(restartingTeam, TAKER_ZONE_ROLE);
    }

    private boolean isFromAttackingThird(MatchState state) {
        ZoneRole zoneRole = layout.roleOf(state.zone(), state.possession());
        return zoneRole == ZoneRole.ATTACKING_THIRD;
    }

    private boolean decidesToShootDirectly() {
        return random.occurs(config.directFreeKickShare());
    }

    private int drawFreeKickDuration() {
        DurationRange range = config.freeKickDuration();
        return random.nextIntInclusive(range.minSeconds(), range.maxSeconds());
    }
}
