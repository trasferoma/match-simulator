package org.tasktelemetry.matchsimulator.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.ActionOutcome;
import org.tasktelemetry.matchsimulator.engine.resolution.lineup.DismissalOutcome;
import org.tasktelemetry.matchsimulator.engine.resolution.lineup.Substitution;
import org.tasktelemetry.matchsimulator.engine.state.MatchClock;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.state.SetPieceKind;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class Match {

    private final EngineConfig config;
    private final PitchLayout layout;
    private final Map<MatchSide, TeamOnPitch> teams;
    private final ActionResolvers resolvers;
    private final List<MatchEvent> events = new ArrayList<>();

    private MatchClock clock = MatchClock.beforeKickOff();
    private MatchState state;
    private Score score = Score.goalless();

    Match(EngineConfig config, PitchLayout layout, Map<MatchSide, TeamOnPitch> teams,
            ActionResolvers resolvers) {
        this.config = config;
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
    }

    public boolean isFinished() {
        return clock.period().isOver();
    }

    public List<MatchEvent> events() {
        return List.copyOf(events);
    }

    private void startPeriod(MatchPeriod period, MatchSide kickingOffSide, EventType type) {
        clock = clock.inPeriod(period);
        state = restartAtCentre(kickingOffSide);
        EventDetail detail = EventDetail.none();
        recordPeriodEvent(type, detail);
        state = state.inOpenPlay();
    }

    private void playAction() {
        MatchState before = state;
        ActionOutcome outcome = resolveNextAction();
        advanceClock(outcome.durationSeconds());
        recordActionEvent(before, outcome);
        state = outcome.next();
        countGoal(before, outcome);
        sanctionFoul(before, outcome);
        if (isFinished()) {
            return;
        }
        announceStoppageIfDue();
        closePeriodIfExpired();
    }

    private ActionOutcome resolveNextAction() {
        if (state.hasCrossInFlight()) {
            return resolveAerialDuel();
        }
        if (state.phase() == PlayPhase.SET_PIECE) {
            return resolveSetPiece();
        }
        if (state.phase() == PlayPhase.COUNTER_ATTACK) {
            return resolveCounterAttack();
        }
        return resolveOpenPlay();
    }

    private ActionOutcome resolveAerialDuel() {
        TeamOnPitch attackers = teams.get(state.possession());
        MatchSide defendingSide = state.possession().opposite();
        TeamOnPitch defenders = teams.get(defendingSide);
        return resolvers.cross().resolveAerialDuel(state, score, attackers, defenders);
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
        ActionOutcome checkedForOffside = resolvers.offside().callOffsideIfCaught(outcome, attackers);
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
        appendEvent(card.type(), cardedSide, at, card.participants(), 0, card.detail());
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
        DismissalOutcome outcome = resolveDismissal(side, dismissed);
        teams.put(side, outcome.team());
        if (outcome.suspendsTheMatch()) {
            suspendMatch(side);
            return;
        }
        recordSubstitutions(side, outcome.substitutions());
    }

    private DismissalOutcome resolveDismissal(MatchSide side, Player dismissed) {
        TeamOnPitch beforeDismissal = teams.get(side);
        int lead = score.leadOf(side);
        return resolvers.dismissal().dismiss(beforeDismissal, dismissed, lead);
    }

    private void recordSubstitutions(MatchSide side, List<Substitution> substitutions) {
        for (Substitution substitution : substitutions) {
            recordSubstitution(side, substitution);
        }
    }

    private void recordSubstitution(MatchSide side, Substitution substitution) {
        Participants participants = Participants.of(substitution.leaving(), substitution.entering());
        EventDetail detail = EventDetail.none();
        appendEvent(EventType.SUBSTITUTION, side, state, participants, 0, detail);
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
        int minutes = config.minimumStoppageMinutes();
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
        appendEvent(outcome.type(), before.possession(), before, outcome.participants(), outcome.durationSeconds(),
                outcome.detail());
    }

    private void recordPeriodEvent(EventType type, EventDetail detail) {
        Participants participants = Participants.nobody();
        appendEvent(type, state.possession(), state, participants, 0, detail);
    }

    private void appendEvent(EventType type, MatchSide side, MatchState at, Participants participants,
            int durationSeconds, EventDetail detail) {
        int order = events.size() + 1;
        MatchTime time = new MatchTime(clock.period(), clock.seconds());
        ZoneRole zoneRole = layout.roleOf(at.zone(), side);
        MatchEvent event = new MatchEvent(order, time, type, side, zoneRole, at.phase(), participants,
                durationSeconds, detail);
        events.add(event);
    }

    private MatchState restartAtCentre(MatchSide possession) {
        Zone kickOffZone = layout.kickOffZone();
        return MatchState.forSetPiece(possession, kickOffZone, SetPieceKind.KICK_OFF);
    }
}
