package org.tasktelemetry.matchsimulator.coach.decision;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.tasktelemetry.matchsimulator.coach.Coach;
import org.tasktelemetry.matchsimulator.coach.TieBreakDraw;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.config.StylePrudenceScale;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.coach.reading.ConfirmationDelay;
import org.tasktelemetry.matchsimulator.coach.reading.Dismissal;
import org.tasktelemetry.matchsimulator.coach.reading.EventWindow;
import org.tasktelemetry.matchsimulator.coach.reading.GameFlow;
import org.tasktelemetry.matchsimulator.coach.reading.GameFlowReader;
import org.tasktelemetry.matchsimulator.coach.reading.LineupReading;
import org.tasktelemetry.matchsimulator.coach.reading.MatchReader;
import org.tasktelemetry.matchsimulator.coach.reading.MatchReading;
import org.tasktelemetry.matchsimulator.coach.reading.PlayedTime;
import org.tasktelemetry.matchsimulator.coach.reading.Posture;
import org.tasktelemetry.matchsimulator.coach.reading.SectorStrainReader;
import org.tasktelemetry.matchsimulator.coach.reading.Situation;
import org.tasktelemetry.matchsimulator.coach.reading.SituationClocks;
import org.tasktelemetry.matchsimulator.coach.reading.SituationReader;
import org.tasktelemetry.matchsimulator.coach.squad.CoachAttributes;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public final class AutomaticCoach implements Coach {

    private final double confirmationDelayMinutes;
    private final int retainedWindowMinutes;
    private final MatchReader reader;
    private final SituationReader situations;
    private final DecisionMoments moments;
    private final InterventionPause pause;
    private final ResultRules resultRules;
    private final FlowRules flowRules;
    private final StyleSteps styleSteps;
    private final ModuleResponses moduleResponses;
    private final DismissalRules dismissalRules;
    private final SectorEmergencies sectorEmergencies;
    private final CautionRules cautionRules;

    private MatchNotes notes;

    public AutomaticCoach(CoachConfig config, CoachAttributes attributes) {
        Objects.requireNonNull(config, "coach configuration must not be null");
        Objects.requireNonNull(attributes, "coach attributes must not be null");
        StylePrudenceScale styleScale = config.styleScale();
        ConfirmationDelay delay = new ConfirmationDelay(config.tacticalReading());
        this.confirmationDelayMinutes = delay.minutesFor(attributes.tacticalReading());
        this.retainedWindowMinutes = Math.max(config.flow().windowMinutes(), config.sectorStrain().windowMinutes());
        this.reader = new MatchReader(styleScale);
        GameFlowReader flowReader = new GameFlowReader(config.flow());
        SectorStrainReader strainReader = new SectorStrainReader(config.sectorStrain());
        this.situations = new SituationReader(flowReader, strainReader);
        this.moments = new DecisionMoments(config.timing());
        this.pause = new InterventionPause(config.timing());
        this.resultRules = new ResultRules(config.timing(), config.resultTiming());
        this.flowRules = new FlowRules();
        this.styleSteps = new StyleSteps(styleScale, config.timing());
        PlayerSelection selection = new PlayerSelection();
        DonorSectors donors = new DonorSectors(config.sectorLimits());
        SubstitutionBudget budget = new SubstitutionBudget(config.substitutions(), config.resultTiming());
        SectorReinforcement reinforcement = new SectorReinforcement(config.sectorLimits(), budget, selection);
        this.moduleResponses = new ModuleResponses(donors, reinforcement);
        this.dismissalRules = new DismissalRules(donors, reinforcement, styleSteps);
        this.sectorEmergencies = new SectorEmergencies(donors, reinforcement, styleSteps);
        this.cautionRules = new CautionRules(budget, selection);
    }

    @Override
    public void open(MatchSide side, PreMatchDecision own, Team opponent, TieBreakDraw draw) {
        Objects.requireNonNull(side, "match side must not be null");
        Objects.requireNonNull(own, "pre-match decision must not be null");
        Objects.requireNonNull(opponent, "opponent team must not be null");
        Objects.requireNonNull(draw, "tie-break draw must not be null");
        if (notes != null) {
            throw new IllegalStateException("the coach already has a match open");
        }
        MatchReading started = beginReading(side, own.team(), opponent);
        MatchReading reading = started.withRosterOrder(side, own.rosterOrder());
        notes = MatchNotes.blank(side, own.band(), reading, draw);
    }

    @Override
    public Optional<Intervention> decide(List<MatchEvent> newEvents) {
        Objects.requireNonNull(newEvents, "new events must not be null");
        MatchNotes before = requireOpenMatch();
        notes = readNewEvents(before, newEvents);
        Optional<DecisionMoment> moment = findDecisionMoment(before, newEvents);
        return moment.flatMap(this::decideAt);
    }

    @Override
    public void close() {
        notes = null;
    }

    private MatchNotes requireOpenMatch() {
        if (notes == null) {
            throw new IllegalStateException("the coach has no match open");
        }
        return notes;
    }

    private MatchNotes readNewEvents(MatchNotes before, List<MatchEvent> newEvents) {
        MatchReading reading = advanceReading(before, newEvents);
        EventWindow window = retainRecentEvents(before, newEvents);
        SituationClocks clocks = followSituations(before, reading, window);
        return before.after(reading, window, clocks);
    }

    private MatchReading advanceReading(MatchNotes before, List<MatchEvent> newEvents) {
        return reader.advance(before.reading(), newEvents);
    }

    private EventWindow retainRecentEvents(MatchNotes before, List<MatchEvent> newEvents) {
        return before.window().after(newEvents, retainedWindowMinutes);
    }

    private Optional<DecisionMoment> findDecisionMoment(MatchNotes before, List<MatchEvent> newEvents) {
        MatchTime previousTime = before.reading().time();
        return moments.momentOf(previousTime, newEvents, before.side());
    }

    private MatchReading beginReading(MatchSide side, Team own, Team opponent) {
        if (side == MatchSide.HOME) {
            return reader.begin(own, opponent);
        }
        return reader.begin(opponent, own);
    }

    private SituationClocks followSituations(MatchNotes before, MatchReading reading, EventWindow window) {
        MatchSide side = before.side();
        Posture opponentPosture = reading.postureOf(side.opposite());
        Set<Situation> holdingNow = situations.holdingFor(side, opponentPosture, window);
        PlayedTime now = reading.playedTime();
        return before.clocks().after(holdingNow, now);
    }

    private Optional<Intervention> decideAt(DecisionMoment moment) {
        TeamNow team = ownTeamNow();
        return respondToOwnDismissal(team).or(() -> decideRespectingThePause(moment, team));
    }

    private Optional<Intervention> decideRespectingThePause(DecisionMoment moment, TeamNow team) {
        if (isInPauseAt(moment)) {
            return Optional.empty();
        }
        if (!moment.decidesBeyondEmergencies()) {
            return reinforceStrugglingSectorInOrdinaryPlay(moment, team);
        }
        ResultSituation situation = readResultSituation();
        if (isTimeToTakeTheRisk(situation)) {
            return respondToTheResult(situation, team)
                    .or(() -> reinforceStrugglingSectorWhileTakingRisk(moment, team))
                    .or(() -> respondToTheRest(situation, team));
        }
        return reinforceStrugglingSectorInOrdinaryPlay(moment, team)
                .or(() -> respondToTheResult(situation, team))
                .or(() -> respondToTheRest(situation, team));
    }

    private Optional<Intervention> respondToTheRest(ResultSituation situation, TeamNow team) {
        return respondToTheFlow(situation, team).or(() -> takePrecautions(team));
    }

    private TeamNow ownTeamNow() {
        MatchReading reading = notes.reading();
        MatchSide side = notes.side();
        LineupReading own = reading.lineupOf(side);
        PlayedTime now = reading.playedTime();
        return new TeamNow(side, own, reading.minute(), now, notes.draw());
    }

    private Optional<Intervention> respondToOwnDismissal(TeamNow team) {
        MatchReading reading = notes.reading();
        Optional<Dismissal> dismissal = reading.dismissalOf(team.side());
        int lead = reading.leadOf(team.side());
        return dismissal.flatMap(sentOff -> dismissalRules.rebalanceAfter(sentOff, lead, team));
    }

    private boolean isTimeToTakeTheRisk(ResultSituation situation) {
        return resultRules.isRiskPhase(situation);
    }

    private Optional<Intervention> reinforceStrugglingSectorInOrdinaryPlay(DecisionMoment moment, TeamNow team) {
        int lead = notes.reading().leadOf(team.side());
        return sectorEmergencies.respondInOrdinaryPlay(this::isConfirmed, moment, lead, team);
    }

    private Optional<Intervention> reinforceStrugglingSectorWhileTakingRisk(DecisionMoment moment, TeamNow team) {
        int lead = notes.reading().leadOf(team.side());
        return sectorEmergencies.respondWhileTakingRisk(this::isConfirmed, moment, lead, team);
    }

    private Optional<Intervention> takePrecautions(TeamNow team) {
        return cautionRules.replaceCautionedDefender(team);
    }

    private boolean isInPauseAt(DecisionMoment moment) {
        if (moment.ignoresThePause()) {
            return false;
        }
        MatchReading reading = notes.reading();
        LineupReading own = reading.lineupOf(notes.side());
        return pause.isActive(own.lastIntervention(), reading.time());
    }

    private Optional<Intervention> respondToTheResult(ResultSituation situation, TeamNow team) {
        Optional<Response> response = resultRules.responseTo(situation);
        return response.flatMap(answer -> carryOut(answer, team));
    }

    private Optional<Intervention> respondToTheFlow(ResultSituation situation, TeamNow team) {
        Optional<Response> response = flowRules.responseTo(situation);
        return response.flatMap(answer -> carryOut(answer, team));
    }

    private Optional<Intervention> carryOut(Response response, TeamNow team) {
        if (styleSteps.canStepToward(response.direction(), team)) {
            return styleSteps.toward(response.direction(), response.reason(), team);
        }
        return moduleResponses.answer(response, team);
    }

    private ResultSituation readResultSituation() {
        MatchReading reading = notes.reading();
        MatchSide side = notes.side();
        MatchSide opponent = side.opposite();
        LineupReading own = reading.lineupOf(side);
        int lead = reading.leadOf(side);
        int minute = reading.minute();
        GameFlow flow = readGameFlow();
        boolean opponentUnbalanced = isConfirmed(Situation.OPPONENT_UNBALANCED);
        boolean opponentShortHanded = reading.isShortHanded(opponent);
        boolean ownSetupAboveInitial = own.isMoreOffensiveThanAtStart();
        return new ResultSituation(lead, minute, flow, opponentUnbalanced, opponentShortHanded,
                ownSetupAboveInitial, notes.band());
    }

    private GameFlow readGameFlow() {
        if (isConfirmed(Situation.DOMINATED)) {
            return GameFlow.DOMINATED;
        }
        return isConfirmed(Situation.DOMINANT) ? GameFlow.DOMINANT : GameFlow.EVEN;
    }

    private boolean isConfirmed(Situation situation) {
        PlayedTime now = notes.reading().playedTime();
        return notes.clocks().hasLastedAtLeast(situation, confirmationDelayMinutes, now);
    }
}
