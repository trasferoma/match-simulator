package org.tasktelemetry.matchsimulator.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import org.tasktelemetry.matchsimulator.engine.resolution.PossessionActionResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.CrossResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.stoppage.SetPieceResolver;
import org.tasktelemetry.matchsimulator.engine.state.MatchClock;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.state.SetPieceKind;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;

public final class Match {

    private final EngineConfig config;
    private final PitchLayout layout;
    private final Map<MatchSide, TeamOnPitch> teams;
    private final PossessionActionResolver possessionResolver;
    private final SetPieceResolver setPieceResolver;
    private final CrossResolver crossResolver;
    private final List<MatchEvent> events = new ArrayList<>();

    private MatchClock clock = MatchClock.beforeKickOff();
    private MatchState state;
    private Score score = Score.goalless();

    Match(EngineConfig config, PitchLayout layout, Map<MatchSide, TeamOnPitch> teams,
            PossessionActionResolver possessionResolver, SetPieceResolver setPieceResolver,
            CrossResolver crossResolver) {
        this.config = config;
        this.layout = layout;
        this.teams = teams;
        this.possessionResolver = possessionResolver;
        this.setPieceResolver = setPieceResolver;
        this.crossResolver = crossResolver;
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
        return resolveOpenPlay();
    }

    private ActionOutcome resolveAerialDuel() {
        TeamOnPitch attackers = teams.get(state.possession());
        MatchSide defendingSide = state.possession().opposite();
        TeamOnPitch defenders = teams.get(defendingSide);
        return crossResolver.resolveAerialDuel(state, score, attackers, defenders);
    }

    private ActionOutcome resolveSetPiece() {
        TeamOnPitch restartingTeam = teams.get(state.possession());
        return setPieceResolver.resolve(state, restartingTeam);
    }

    private ActionOutcome resolveOpenPlay() {
        TeamOnPitch attackers = teams.get(state.possession());
        MatchSide defendingSide = state.possession().opposite();
        TeamOnPitch defenders = teams.get(defendingSide);
        return possessionResolver.resolve(state, score, attackers, defenders);
    }

    private void countGoal(MatchState before, ActionOutcome outcome) {
        if (outcome.type() == EventType.GOAL) {
            score = score.withGoalBy(before.possession());
        }
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
        appendEvent(outcome.type(), before, outcome.participants(), outcome.durationSeconds(), outcome.detail());
    }

    private void recordPeriodEvent(EventType type, EventDetail detail) {
        Participants participants = Participants.nobody();
        appendEvent(type, state, participants, 0, detail);
    }

    private void appendEvent(EventType type, MatchState at, Participants participants, int durationSeconds,
            EventDetail detail) {
        int order = events.size() + 1;
        MatchTime time = new MatchTime(clock.period(), clock.seconds());
        ZoneRole zoneRole = layout.roleOf(at.zone(), at.possession());
        MatchEvent event = new MatchEvent(order, time, type, at.possession(), zoneRole, at.phase(), participants,
                durationSeconds, detail);
        events.add(event);
    }

    private MatchState restartAtCentre(MatchSide possession) {
        Zone kickOffZone = layout.kickOffZone();
        return MatchState.forSetPiece(possession, kickOffZone, SetPieceKind.KICK_OFF);
    }
}
