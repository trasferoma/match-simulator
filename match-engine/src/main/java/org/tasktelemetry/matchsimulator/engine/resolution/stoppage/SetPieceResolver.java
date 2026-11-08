package org.tasktelemetry.matchsimulator.engine.resolution.stoppage;

import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.config.DurationRange;
import org.tasktelemetry.matchsimulator.engine.config.SetPieceConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.resolution.ActionOutcome;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.CrossResolver;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class SetPieceResolver {

    private final SetPieceConfig config;
    private final MatchRandom random;
    private final CrossResolver crossResolver;

    public SetPieceResolver(SetPieceConfig config, MatchRandom random, CrossResolver crossResolver) {
        this.config = config;
        this.random = random;
        this.crossResolver = crossResolver;
    }

    public ActionOutcome resolve(MatchState state, TeamOnPitch restartingTeam) {
        return switch (state.setPiece()) {
            case KICK_OFF -> kickOffAfterGoal(state);
            case GOAL_KICK -> goalKick(state, restartingTeam);
            case CORNER -> cornerKick(state, restartingTeam);
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
}
