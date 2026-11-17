package org.tasktelemetry.matchsimulator.engine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.event.BallPosition;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.ActionOutcome;
import org.tasktelemetry.matchsimulator.engine.resolution.fatigue.FatigueUpdate;
import org.tasktelemetry.matchsimulator.engine.resolution.lineup.LineupOutcome;
import org.tasktelemetry.matchsimulator.engine.resolution.lineup.PlayerMove;
import org.tasktelemetry.matchsimulator.engine.resolution.lineup.Substitution;
import org.tasktelemetry.matchsimulator.engine.resolution.lineup.VoluntarySubstitutionRule;
import org.tasktelemetry.matchsimulator.engine.state.MatchClock;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.state.SetPieceKind;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class Match {

    private record PendingIntervention(Intervention intervention, Score scoreAtRequest) {
    }

    private record DeploymentShift(Deployment before, Deployment after) {

        static DeploymentShift between(TeamOnPitch teamBefore, TeamOnPitch teamAfter) {
            Deployment before = Deployment.of(teamBefore);
            Deployment after = Deployment.of(teamAfter);
            return new DeploymentShift(before, after);
        }
    }

    private final PitchLayout layout;
    private final Map<MatchSide, TeamOnPitch> teams;
    private final ActionResolvers resolvers;
    private final List<MatchEvent> events = new ArrayList<>();
    private final Map<MatchSide, PendingIntervention> pendingInterventions = new EnumMap<>(MatchSide.class);

    private MatchClock clock = MatchClock.beforeKickOff();
    private MatchState state;
    private Score score = Score.goalless();

    Match(PitchLayout layout, Map<MatchSide, TeamOnPitch> teams, ActionResolvers resolvers) {
        this.layout = layout;
        this.teams = teams;
        this.resolvers = resolvers;
        this.state = restartAtCentre(MatchSide.HOME);
    }

    public void step() {
        switch (clock.period()) {
            case BEFORE_KICK_OFF -> startPeriod(MatchPeriod.FIRST_HALF, MatchSide.HOME, EventType.KICK_OFF);
            case HALF_TIME -> startPeriod(MatchPeriod.SECOND_HALF, MatchSide.AWAY, EventType.SECOND_HALF_START);
            case FIRST_HALF, SECOND_HALF -> playAction();
            case FINISHED, SUSPENDED -> throw new IllegalStateException("the match is over, no step can be played");
        }
        settlePendingInterventions();
    }

    public InterventionResult apply(Intervention intervention) {
        Objects.requireNonNull(intervention, "intervention must not be null");
        Optional<RefusalReason> refusal = findRefusalToRequest(intervention);
        if (refusal.isPresent()) {
            return InterventionResult.refused(refusal.get());
        }
        if (isBallLive()) {
            return keepPending(intervention);
        }
        execute(intervention);
        return InterventionResult.executed();
    }

    public boolean isFinished() {
        return clock.period().isOver();
    }

    public List<MatchEvent> events() {
        return List.copyOf(events);
    }

    TeamOnPitch teamOnPitch(MatchSide side) {
        return teams.get(side);
    }

    MatchState state() {
        return state;
    }

    private Optional<RefusalReason> findRefusalToRequest(Intervention intervention) {
        if (!clock.period().isInProgress()) {
            return Optional.of(RefusalReason.MATCH_NOT_IN_PLAY);
        }
        if (pendingInterventions.containsKey(intervention.side())) {
            return Optional.of(RefusalReason.INTERVENTION_ALREADY_PENDING);
        }
        return findRefusalOf(intervention);
    }

    private InterventionResult keepPending(Intervention intervention) {
        pendingInterventions.put(intervention.side(), new PendingIntervention(intervention, score));
        return InterventionResult.pending();
    }

    private boolean isBallLive() {
        boolean isPlayed = clock.period().isPlayed();
        return isPlayed && (state.hasCrossInFlight() || state.isAwaitingPenalty());
    }

    private void settlePendingInterventions() {
        if (pendingInterventions.isEmpty() || isBallLive()) {
            return;
        }
        List<PendingIntervention> due = List.copyOf(pendingInterventions.values());
        pendingInterventions.clear();
        for (PendingIntervention pending : due) {
            if (isFinished()) {
                return;
            }
            executeIfStillValid(pending);
        }
    }

    private void executeIfStillValid(PendingIntervention pending) {
        Intervention intervention = pending.intervention();
        boolean isOutdatedByAGoal = !score.equals(pending.scoreAtRequest());
        if (isOutdatedByAGoal || findRefusalOf(intervention).isPresent()) {
            return;
        }
        execute(intervention);
    }

    private Optional<RefusalReason> findRefusalOf(Intervention intervention) {
        TeamOnPitch team = teams.get(intervention.side());
        return switch (intervention) {
            case Intervention.StyleChange change -> findStyleRefusalOf(team, change);
            case Intervention.VoluntarySubstitution request -> findSubstitutionRefusalOf(team, request);
            case Intervention.SectorMove request -> findMoveRefusalOf(team, request);
        };
    }

    private Optional<RefusalReason> findSubstitutionRefusalOf(TeamOnPitch team,
            Intervention.VoluntarySubstitution request) {
        return resolvers.voluntarySubstitution().refusalOf(team, request);
    }

    private Optional<RefusalReason> findMoveRefusalOf(TeamOnPitch team, Intervention.SectorMove request) {
        return resolvers.sectorMove().refusalOf(team, request);
    }

    private Optional<RefusalReason> findStyleRefusalOf(TeamOnPitch team, Intervention.StyleChange change) {
        if (team.style() == change.style()) {
            return Optional.of(RefusalReason.STYLE_ALREADY_IN_USE);
        }
        return Optional.empty();
    }

    private void execute(Intervention intervention) {
        switch (intervention) {
            case Intervention.StyleChange change -> changeStyle(change);
            case Intervention.VoluntarySubstitution request -> substituteVoluntarily(request);
            case Intervention.SectorMove request -> moveToSector(request);
        }
    }

    private void changeStyle(Intervention.StyleChange change) {
        MatchSide side = change.side();
        TeamOnPitch beforeChange = teams.get(side);
        PlayStyle previousStyle = beforeChange.style();
        TeamOnPitch restyled = beforeChange.withStyle(change.style());
        teams.put(side, restyled);
        recordStyleChange(side, previousStyle, change);
    }

    private void substituteVoluntarily(Intervention.VoluntarySubstitution request) {
        MatchSide side = request.side();
        TeamOnPitch beforeChange = teams.get(side);
        Substitution substitution = bringInTheSubstitute(request);
        TeamOnPitch afterChange = teams.get(side);
        DeploymentShift shift = DeploymentShift.between(beforeChange, afterChange);
        recordVoluntarySubstitution(side, substitution, shift);
    }

    private void moveToSector(Intervention.SectorMove request) {
        MatchSide side = request.side();
        TeamOnPitch beforeMove = teams.get(side);
        TeamOnPitch moved = resolvers.sectorMove().move(beforeMove, request);
        teams.put(side, moved);
        PlayerMove move = new PlayerMove(request.player(), request.sector(), request.reason());
        DeploymentShift shift = DeploymentShift.between(beforeMove, moved);
        recordMove(side, move, shift);
    }

    private Substitution bringInTheSubstitute(Intervention.VoluntarySubstitution request) {
        MatchSide side = request.side();
        TeamOnPitch beforeChange = teams.get(side);
        VoluntarySubstitutionRule rule = resolvers.voluntarySubstitution();
        TeamOnPitch changed = rule.substitute(beforeChange, request);
        teams.put(side, changed);
        Role sector = changed.roleOf(request.entering());
        return new Substitution(request.leaving(), request.entering(), sector, request.reason());
    }

    private void recordVoluntarySubstitution(MatchSide side, Substitution substitution, DeploymentShift shift) {
        if (clock.period() == MatchPeriod.HALF_TIME) {
            appendSubstitution(side, substitution, shift, 0);
            return;
        }
        forgetPassOf(side, substitution.leaving());
        recordSubstitution(side, substitution, shift);
        announceStoppageAndClosePeriodIfDue();
    }

    private void announceStoppageAndClosePeriodIfDue() {
        announceStoppageIfDue();
        closePeriodIfExpired();
    }

    private void recordStyleChange(MatchSide side, PlayStyle previousStyle, Intervention.StyleChange change) {
        EventDetail detail = EventDetail.styleChange(previousStyle, change.style(), change.reason());
        Participants nobody = Participants.nobody();
        appendEvent(EventType.STYLE_CHANGED, side, state, nobody, 0, detail);
    }

    private void startPeriod(MatchPeriod period, MatchSide kickingOffSide, EventType type) {
        clock = clock.inPeriod(period);
        state = restartAtCentre(kickingOffSide);
        EventDetail detail = EventDetail.none();
        recordPeriodEvent(type, detail);
        state = state.inOpenPlay();
    }

    private void playAction() {
        resolveAndRecordNextAction();
        if (isFinished()) {
            return;
        }
        strikeInjuries();
        if (isFinished()) {
            return;
        }
        announceStoppageIfDue();
        completeCrossInFlightAtTheWhistle();
        takePenaltyAwardedAtTheWhistle();
        closePeriodIfExpired();
    }

    private void resolveAndRecordNextAction() {
        MatchState before = state;
        ActionOutcome outcome = resolveNextAction();
        advanceClock(outcome.durationSeconds());
        recordActionEvent(before, outcome);
        state = outcome.next();
        tireThePlayers(outcome);
        countGoal(before, outcome);
        sanctionFoul(before, outcome);
    }

    private void tireThePlayers(ActionOutcome outcome) {
        for (MatchSide side : MatchSide.values()) {
            TeamOnPitch team = teams.get(side);
            FatigueUpdate update = resolvers.energyDrain().drain(team, side, outcome);
            teams.put(side, update.team());
            recordFatigueEvents(side, EventType.PLAYER_TIRED, update);
        }
    }

    private void recoverAtHalfTime() {
        for (MatchSide side : MatchSide.values()) {
            TeamOnPitch team = teams.get(side);
            FatigueUpdate update = resolvers.halfTimeRecovery().recover(team);
            teams.put(side, update.team());
            recordFatigueEvents(side, EventType.PLAYER_RECOVERED, update);
        }
    }

    private void recordFatigueEvents(MatchSide side, EventType type, FatigueUpdate update) {
        for (FatigueUpdate.BandShift shift : update.shifts()) {
            Participants participants = Participants.of(shift.player(), side);
            EventDetail detail = EventDetail.fatigue(shift.band());
            appendEvent(type, side, state, participants, 0, detail);
        }
    }

    private void completeCrossInFlightAtTheWhistle() {
        if (isCrossAwaitedAtTheWhistle()) {
            resolveAndRecordNextAction();
        }
    }

    private boolean isCrossAwaitedAtTheWhistle() {
        return clock.hasExpired() && state.hasCrossInFlight();
    }

    private void takePenaltyAwardedAtTheWhistle() {
        if (isPenaltyAwaitedAtTheWhistle()) {
            resolveAndRecordNextAction();
        }
    }

    private boolean isPenaltyAwaitedAtTheWhistle() {
        return clock.hasExpired() && state.isAwaitingPenalty();
    }

    private void strikeInjuries() {
        for (MatchSide side : MatchSide.values()) {
            if (!canBeInjured()) {
                return;
            }
            strikeInjuryOf(side);
        }
    }

    private boolean canBeInjured() {
        boolean playIsOn = !isFinished() && !clock.reachedRegulationTime();
        return playIsOn && !state.hasCrossInFlight();
    }

    private void strikeInjuryOf(MatchSide side) {
        Optional<Player> injured = drawInjuredPlayer(side);
        injured.ifPresent(player -> treatInjury(side, player));
    }

    private Optional<Player> drawInjuredPlayer(MatchSide side) {
        TeamOnPitch team = teams.get(side);
        return resolvers.injury().injuredIn(team);
    }

    private void treatInjury(MatchSide side, Player injured) {
        recordInjury(side, injured);
        forgetPassOf(side, injured);
        TeamOnPitch beforeInjury = teams.get(side);
        LineupOutcome outcome = replaceInjured(beforeInjury, injured);
        TeamOnPitch beforeChanges = lineupBeforeChangesAfterInjury(beforeInjury, injured, outcome);
        applyLineupOutcome(side, beforeChanges, outcome);
    }

    private TeamOnPitch lineupBeforeChangesAfterInjury(TeamOnPitch beforeInjury, Player injured,
            LineupOutcome outcome) {
        boolean isLeftWithoutReplacement = outcome.substitutions().isEmpty();
        if (isLeftWithoutReplacement) {
            return beforeInjury.withoutPlayer(injured);
        }
        return beforeInjury;
    }

    private void forgetPassOf(MatchSide side, Player injured) {
        if (isLastPasserOfThePossessingSide(side, injured)) {
            state = state.withoutLastPasser();
        }
    }

    private boolean isLastPasserOfThePossessingSide(MatchSide side, Player player) {
        boolean isPossessingSide = side == state.possession();
        return isPossessingSide && player.equals(state.lastPasser());
    }

    private LineupOutcome replaceInjured(TeamOnPitch beforeInjury, Player injured) {
        return resolvers.injury().replaceInjured(beforeInjury, injured);
    }

    private void recordInjury(MatchSide side, Player injured) {
        int duration = resolvers.injury().drawInjurySeconds();
        advanceClock(duration);
        Participants participants = Participants.of(injured, side);
        EventDetail detail = playerRemovedDetail(side, injured);
        appendEvent(EventType.INJURY, side, state, participants, duration, detail);
    }

    private EventDetail playerRemovedDetail(MatchSide side, Player removed) {
        TeamOnPitch withoutRemoved = teams.get(side).withoutPlayer(removed);
        Deployment deploymentAfter = Deployment.of(withoutRemoved);
        return EventDetail.playerRemoved(deploymentAfter);
    }

    private ActionOutcome resolveNextAction() {
        if (state.hasCrossInFlight()) {
            return resolveCrossInFlight();
        }
        if (state.phase() == PlayPhase.SET_PIECE) {
            return resolveSetPiece();
        }
        if (state.phase() == PlayPhase.COUNTER_ATTACK) {
            return resolveCounterAttack();
        }
        return resolveOpenPlay();
    }

    private ActionOutcome resolveCrossInFlight() {
        TeamOnPitch attackers = teams.get(state.possession());
        MatchSide defendingSide = state.possession().opposite();
        TeamOnPitch defenders = teams.get(defendingSide);
        return resolvers.cross().resolveCrossInFlight(state, score, attackers, defenders);
    }

    private ActionOutcome resolveSetPiece() {
        TeamOnPitch restartingTeam = teams.get(state.possession());
        MatchSide opposingSide = state.possession().opposite();
        TeamOnPitch opponents = teams.get(opposingSide);
        return resolvers.setPiece().resolve(state, score, restartingTeam, opponents);
    }

    private ActionOutcome resolveOpenPlay() {
        TeamOnPitch attackers = teams.get(state.possession());
        MatchSide defendingSide = state.possession().opposite();
        TeamOnPitch defenders = teams.get(defendingSide);
        ActionOutcome outcome = resolvers.possession().resolve(state, score, attackers, defenders);
        ActionOutcome checkedForOffside = resolvers.offside().callOffsideIfCaught(outcome, attackers, defenders);
        return resolvers.counterAttack().openCounterAttackIfBallRecovered(checkedForOffside, defenders);
    }

    private ActionOutcome resolveCounterAttack() {
        TeamOnPitch attackers = teams.get(state.possession());
        MatchSide defendingSide = state.possession().opposite();
        TeamOnPitch defenders = teams.get(defendingSide);
        ActionOutcome outcome = resolvers.counterAttack().resolve(state, score, attackers, defenders);
        return resolvers.counterAttack().openCounterAttackIfBallRecovered(outcome, defenders);
    }

    private void countGoal(MatchState before, ActionOutcome outcome) {
        if (outcome.type() == EventType.GOAL) {
            score = score.withGoalBy(before.possession());
        }
    }

    private void sanctionFoul(MatchState before, ActionOutcome outcome) {
        if (outcome.type() != EventType.FOUL) {
            return;
        }
        MatchSide foulingSide = before.possession().opposite();
        Optional<ActionOutcome> sanction = decideSanction(outcome, foulingSide);
        sanction.ifPresent(card -> applyCard(before, foulingSide, card));
    }

    private Optional<ActionOutcome> decideSanction(ActionOutcome foul, MatchSide foulingSide) {
        TeamOnPitch foulingTeam = teams.get(foulingSide);
        return resolvers.foul().sanctionFor(foul, foulingTeam);
    }

    private void applyCard(MatchState at, MatchSide cardedSide, ActionOutcome card) {
        Occurrence occurrence = new Occurrence(card.type(), cardedSide, at, card.next());
        recordEvent(occurrence, card.participants(), 0, card.detail());
        Player carded = card.participants().primary().orElseThrow();
        if (card.type().isDismissal()) {
            dismiss(cardedSide, carded);
            return;
        }
        cautionPlayer(cardedSide, carded);
    }

    private void cautionPlayer(MatchSide side, Player player) {
        TeamOnPitch cautionedTeam = teams.get(side).withCautioned(player);
        teams.put(side, cautionedTeam);
    }

    private void dismiss(MatchSide side, Player dismissed) {
        TeamOnPitch beforeDismissal = teams.get(side);
        LineupOutcome outcome = resolveDismissal(side, dismissed);
        TeamOnPitch beforeChanges = beforeDismissal.withoutPlayer(dismissed);
        applyLineupOutcome(side, beforeChanges, outcome);
    }

    private void applyLineupOutcome(MatchSide side, TeamOnPitch beforeChanges, LineupOutcome outcome) {
        teams.put(side, outcome.team());
        if (outcome.suspendsTheMatch()) {
            suspendMatch(side);
            return;
        }
        TeamOnPitch afterSubstitutions = recordSubstitutions(side, beforeChanges, outcome.substitutions());
        recordMoves(side, afterSubstitutions, outcome.moves());
    }

    private LineupOutcome resolveDismissal(MatchSide side, Player dismissed) {
        TeamOnPitch beforeDismissal = teams.get(side);
        int lead = score.leadOf(side);
        return resolvers.dismissal().dismiss(beforeDismissal, dismissed, lead);
    }

    private TeamOnPitch recordSubstitutions(MatchSide side, TeamOnPitch beforeAll,
            List<Substitution> substitutions) {
        TeamOnPitch current = beforeAll;
        for (Substitution substitution : substitutions) {
            Player leaving = substitution.leaving();
            Player entering = substitution.entering();
            TeamOnPitch changed = current.withSubstitution(leaving, entering, substitution.sector());
            DeploymentShift shift = DeploymentShift.between(current, changed);
            recordSubstitution(side, substitution, shift);
            current = changed;
        }
        return current;
    }

    private void recordMoves(MatchSide side, TeamOnPitch beforeAll, List<PlayerMove> moves) {
        TeamOnPitch current = beforeAll;
        for (PlayerMove move : moves) {
            TeamOnPitch moved = current.withRole(move.player(), move.sector());
            DeploymentShift shift = DeploymentShift.between(current, moved);
            recordMove(side, move, shift);
            current = moved;
        }
    }

    private void recordMove(MatchSide side, PlayerMove move, DeploymentShift shift) {
        Participants participants = Participants.of(move.player(), side);
        EventDetail detail = EventDetail.lineupChange(move.sector(), move.reason(), shift.before(), shift.after());
        appendEvent(EventType.PLAYER_MOVED, side, state, participants, 0, detail);
    }

    private void recordSubstitution(MatchSide side, Substitution substitution, DeploymentShift shift) {
        int duration = resolvers.substitutionTimer().drawSubstitutionSeconds();
        advanceClock(duration);
        appendSubstitution(side, substitution, shift, duration);
    }

    private void appendSubstitution(MatchSide side, Substitution substitution, DeploymentShift shift,
            int durationSeconds) {
        Participants participants = Participants.of(substitution.leaving(), side, substitution.entering(), side);
        EventDetail detail = EventDetail.lineupChange(substitution.sector(), substitution.reason(), shift.before(),
                shift.after());
        appendEvent(EventType.SUBSTITUTION, side, state, participants, durationSeconds, detail);
    }

    private void suspendMatch(MatchSide responsibleSide) {
        Score official = resolvers.minimumPlayers().officialResult(responsibleSide, score);
        EventDetail detail = EventDetail.suspension(responsibleSide, score, official);
        Participants nobody = Participants.nobody();
        appendEvent(EventType.MATCH_SUSPENDED, responsibleSide, state, nobody, 0, detail);
        clock = clock.inPeriod(MatchPeriod.SUSPENDED);
    }

    private void advanceClock(int seconds) {
        clock = clock.advancedBy(seconds);
    }

    private void announceStoppageIfDue() {
        if (!clock.reachedRegulationTime() || clock.isStoppageAnnounced()) {
            return;
        }
        int minutes = resolvers.stoppage().announcedMinutes(events, clock.period());
        clock = clock.withAnnouncedStoppage(minutes);
        EventDetail detail = EventDetail.stoppageAnnounced(minutes);
        recordPeriodEvent(EventType.STOPPAGE_ANNOUNCED, detail);
    }

    private void closePeriodIfExpired() {
        if (!clock.hasExpired()) {
            return;
        }
        if (clock.period() == MatchPeriod.FIRST_HALF) {
            endPeriod(EventType.FIRST_HALF_END, MatchPeriod.HALF_TIME);
            recoverAtHalfTime();
            return;
        }
        endPeriod(EventType.MATCH_END, MatchPeriod.FINISHED);
    }

    private void endPeriod(EventType type, MatchPeriod next) {
        EventDetail detail = EventDetail.none();
        recordPeriodEvent(type, detail);
        clock = clock.inPeriod(next);
    }

    private void recordActionEvent(MatchState before, ActionOutcome outcome) {
        MatchState ballAt = ballAfter(before, outcome);
        Occurrence occurrence = new Occurrence(outcome.type(), before.possession(), before, ballAt);
        recordEvent(occurrence, outcome.participants(), outcome.durationSeconds(), outcome.detail());
    }

    private MatchState ballAfter(MatchState before, ActionOutcome outcome) {
        if (outcome.type() == EventType.GOAL) {
            return ballInTheNet(before);
        }
        return outcome.next();
    }

    private MatchState ballInTheNet(MatchState before) {
        MatchSide scoringSide = before.possession();
        MatchSide concedingSide = scoringSide.opposite();
        Zone concedingBox = layout.ownBoxZone(concedingSide);
        return MatchState.openPlay(scoringSide, concedingBox);
    }

    private void recordPeriodEvent(EventType type, EventDetail detail) {
        Participants participants = Participants.nobody();
        appendEvent(type, state.possession(), state, participants, 0, detail);
    }

    private void appendEvent(EventType type, MatchSide side, MatchState at, Participants participants,
            int durationSeconds, EventDetail detail) {
        Occurrence occurrence = new Occurrence(type, side, at, at);
        recordEvent(occurrence, participants, durationSeconds, detail);
    }

    private void recordEvent(Occurrence occurrence, Participants participants, int durationSeconds,
            EventDetail detail) {
        int order = events.size() + 1;
        MatchTime time = timeOfNextEvent();
        MatchState at = occurrence.at();
        ZoneRole zoneRole = layout.roleOf(at.zone(), occurrence.side());
        BallPosition ball = ballPositionAt(occurrence.ballAt());
        MatchEvent event = new MatchEvent(order, time, occurrence.type(), occurrence.side(), zoneRole, ball,
                at.phase(), participants, durationSeconds, detail);
        events.add(event);
    }

    private MatchTime timeOfNextEvent() {
        if (clock.period() == MatchPeriod.HALF_TIME) {
            return new MatchTime(MatchPeriod.SECOND_HALF, 0);
        }
        return new MatchTime(clock.period(), clock.seconds());
    }

    private BallPosition ballPositionAt(MatchState ballAt) {
        ZoneRole zoneRole = layout.roleOf(ballAt.zone(), ballAt.possession());
        return new BallPosition(ballAt.possession(), zoneRole);
    }

    private MatchState restartAtCentre(MatchSide possession) {
        Zone kickOffZone = layout.kickOffZone();
        return MatchState.forSetPiece(possession, kickOffZone, SetPieceKind.KICK_OFF);
    }

    private record Occurrence(EventType type, MatchSide side, MatchState at, MatchState ballAt) {
    }
}
