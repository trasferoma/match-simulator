package org.tasktelemetry.matchsimulator.engine.resolution.shot;

import java.util.Comparator;

import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.config.DurationRange;
import org.tasktelemetry.matchsimulator.engine.config.ShotConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.GoalContext;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.event.SaveQuality;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.event.ShotProfile;
import org.tasktelemetry.matchsimulator.engine.event.ShotQuality;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.ActionOutcome;
import org.tasktelemetry.matchsimulator.engine.resolution.Matchups;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.state.SetPieceKind;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class ShotResolver {

    private static final double NEUTRAL = 1.0;

    private final ShotConfig config;
    private final PitchLayout layout;
    private final MatchRandom random;
    private final ExpectedGoalsModel model;
    private final ShotQualityScale qualityScale;
    private final Matchups matchups;

    public ShotResolver(ShotConfig config, PitchLayout layout, MatchRandom random, ExpectedGoalsModel model,
            ShotQualityScale qualityScale, Matchups matchups) {
        this.config = config;
        this.layout = layout;
        this.random = random;
        this.model = model;
        this.qualityScale = qualityScale;
        this.matchups = matchups;
    }

    public ActionOutcome resolveShot(MatchState state, Score score, TeamOnPitch attackers, Player shooter,
            ShotKind kind, TeamOnPitch defenders) {
        Attempt attempt = prepareShot(state, attackers, shooter, kind, defenders);
        return conclude(state, score, attempt);
    }

    public ActionOutcome resolvePenalty(MatchState state, Score score, TeamOnPitch attackers,
            TeamOnPitch defenders) {
        Attempt attempt = preparePenalty(attackers, defenders);
        return conclude(state, score, attempt);
    }

    public Player penaltyTakerOf(TeamOnPitch attackers) {
        Comparator<Player> byShooting = Comparator.comparingInt(player -> player.attributes().shooting());
        return attackers.players().stream()
                .max(byShooting)
                .orElseThrow();
    }

    private Attempt prepareShot(MatchState state, TeamOnPitch attackers, Player shooter, ShotKind kind,
            TeamOnPitch defenders) {
        ZoneRole zoneRole = layout.roleOf(state.zone(), state.possession());
        double shootingMultiplier = matchups.shootingMultiplier(state, attackers, shooter);
        double expectedGoals = model.expectedGoals(zoneRole, state.phase(), kind, shooter, shootingMultiplier);
        ShotQuality danger = qualityScale.dangerOf(expectedGoals);
        ShotProfile profile = ShotProfile.ofShot(expectedGoals, kind, danger);
        Player goalkeeper = defenders.goalkeeper();
        double goalkeepingMultiplier = matchups.goalkeepingMultiplier(state, defenders);
        double goalProbability = model.goalProbability(expectedGoals, goalkeeper, goalkeepingMultiplier);
        MissShares misses = MissShares.ofShot(config);
        DurationRange missDuration = config.shotDuration();
        return new Attempt(profile, shooter, goalkeeper, goalProbability, misses, missDuration);
    }

    private Attempt preparePenalty(TeamOnPitch attackers, TeamOnPitch defenders) {
        Player taker = penaltyTakerOf(attackers);
        double expectedGoals = model.penaltyExpectedGoals(taker);
        ShotQuality danger = qualityScale.dangerOf(expectedGoals);
        ShotProfile profile = ShotProfile.ofPenalty(expectedGoals, danger);
        Player goalkeeper = defenders.goalkeeper();
        double goalProbability = model.goalProbability(expectedGoals, goalkeeper, NEUTRAL);
        MissShares misses = MissShares.ofPenalty(config);
        DurationRange missDuration = config.penaltyDuration();
        return new Attempt(profile, taker, goalkeeper, goalProbability, misses, missDuration);
    }

    private ActionOutcome conclude(MatchState state, Score score, Attempt attempt) {
        if (goalIsScored(attempt)) {
            return scoreGoal(state, score, attempt);
        }
        MissShares.Miss miss = drawMiss(attempt);
        return switch (miss) {
            case BLOCKED -> blockShot(state, attempt);
            case OFF_TARGET -> missTarget(state, attempt);
            case SAVED -> saveShot(state, attempt);
        };
    }

    private boolean goalIsScored(Attempt attempt) {
        return random.occurs(attempt.goalProbability());
    }

    private MissShares.Miss drawMiss(Attempt attempt) {
        double draw = random.nextDouble();
        return attempt.misses().missAt(draw);
    }

    private ActionOutcome scoreGoal(MatchState state, Score score, Attempt attempt) {
        GoalContext context = GoalContext.of(state.possession(), score);
        EventDetail detail = EventDetail.goal(attempt.profile(), context);
        MatchSide concedingSide = state.possession().opposite();
        Zone kickOffZone = layout.kickOffZone();
        MatchState next = MatchState.forSetPiece(concedingSide, kickOffZone, SetPieceKind.KICK_OFF);
        int duration = drawDuration(config.goalWithRestartDuration());
        Participants participants = scorerWithAssistant(state, attempt);
        return new ActionOutcome(EventType.GOAL, participants, next, duration, detail);
    }

    private ActionOutcome saveShot(MatchState state, Attempt attempt) {
        SaveQuality saveQuality = qualityScale.difficultyOfSaving(attempt.goalProbability());
        EventDetail detail = EventDetail.save(attempt.profile(), saveQuality);
        MatchState next = stateAfterSave(state);
        int duration = drawDuration(attempt.missDuration());
        MatchSide attackingSide = state.possession();
        MatchSide defendingSide = attackingSide.opposite();
        Participants participants = Participants.of(attempt.shooter(), attackingSide, attempt.goalkeeper(),
                defendingSide);
        return new ActionOutcome(EventType.SHOT_SAVED, participants, next, duration, detail);
    }

    private ActionOutcome missTarget(MatchState state, Attempt attempt) {
        EventDetail detail = EventDetail.shot(attempt.profile());
        MatchSide goalKickSide = state.possession().opposite();
        Zone goalKickSideBox = layout.ownBoxZone(goalKickSide);
        MatchState next = MatchState.forSetPiece(goalKickSide, goalKickSideBox, SetPieceKind.GOAL_KICK);
        int duration = drawDuration(attempt.missDuration());
        Participants participants = shooterWithCrosser(state, attempt);
        return new ActionOutcome(EventType.SHOT_OFF_TARGET, participants, next, duration, detail);
    }

    private ActionOutcome blockShot(MatchState state, Attempt attempt) {
        EventDetail detail = EventDetail.shot(attempt.profile());
        MatchState next = stateAfterBlock(state);
        int duration = drawDuration(attempt.missDuration());
        Participants participants = shooterWithCrosser(state, attempt);
        return new ActionOutcome(EventType.SHOT_BLOCKED, participants, next, duration, detail);
    }

    private MatchState stateAfterSave(MatchState state) {
        boolean pushedOutForCorner = random.occurs(config.savedToCornerShare());
        if (pushedOutForCorner) {
            return cornerForAttackers(state);
        }
        MatchSide keepingSide = state.possession().opposite();
        Zone keepingSideBox = layout.ownBoxZone(keepingSide);
        return MatchState.openPlay(keepingSide, keepingSideBox);
    }

    private MatchState stateAfterBlock(MatchState state) {
        double draw = random.nextDouble();
        double cornerLimit = config.blockedToCornerShare();
        double attackRecoversLimit = cornerLimit + config.blockedRecoveredByAttackShare();
        if (draw < cornerLimit) {
            return cornerForAttackers(state);
        }
        if (draw < attackRecoversLimit) {
            return state.inOpenPlay();
        }
        MatchSide defendingSide = state.possession().opposite();
        return MatchState.openPlay(defendingSide, state.zone());
    }

    private MatchState cornerForAttackers(MatchState state) {
        MatchSide attackingSide = state.possession();
        MatchSide defendingSide = attackingSide.opposite();
        Zone opponentBox = layout.ownBoxZone(defendingSide);
        return MatchState.forSetPiece(attackingSide, opponentBox, SetPieceKind.CORNER);
    }

    private Participants scorerWithAssistant(MatchState state, Attempt attempt) {
        Player scorer = attempt.shooter();
        if (state.hasCrossInFlight()) {
            return Participants.of(scorer, state.possession(), state.crosser(), state.possession());
        }
        if (isAssistedByLastPasser(state, scorer)) {
            return Participants.of(scorer, state.possession(), state.lastPasser(), state.possession());
        }
        return Participants.of(scorer, state.possession());
    }

    private boolean isAssistedByLastPasser(MatchState state, Player scorer) {
        return state.hasLastPasser() && !state.lastPasser().equals(scorer);
    }

    private Participants shooterWithCrosser(MatchState state, Attempt attempt) {
        if (state.hasCrossInFlight()) {
            return Participants.of(attempt.shooter(), state.possession(), state.crosser(), state.possession());
        }
        return Participants.of(attempt.shooter(), state.possession());
    }

    private int drawDuration(DurationRange range) {
        return random.nextIntInclusive(range.minSeconds(), range.maxSeconds());
    }

    private record Attempt(ShotProfile profile, Player shooter, Player goalkeeper, double goalProbability,
            MissShares misses, DurationRange missDuration) {
    }
}
