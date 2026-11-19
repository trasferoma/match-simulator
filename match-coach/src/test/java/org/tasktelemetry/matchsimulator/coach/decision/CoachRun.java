package org.tasktelemetry.matchsimulator.coach.decision;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.coach.ScenarioEvents;
import org.tasktelemetry.matchsimulator.coach.ScenarioMoments;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.coach.squad.CoachAttributes;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

final class CoachRun {

    record Decision(int minute, Intervention intervention) {
    }

    private static final int HALF_TIME_MINUTE = 45;

    private final MatchSide side;
    private final AutomaticCoach coach;
    private final ScenarioEvents events = new ScenarioEvents();
    private final Map<MatchSide, PlayStyle> styles = new EnumMap<>(MatchSide.class);
    private final List<Decision> decisions = new ArrayList<>();
    private final List<MatchEvent> executedAtTheNextDelivery = new ArrayList<>();
    private final int executionSecond;

    private int minute;

    private CoachRun(MatchSide side, AutomaticCoach coach, Team own, Team opponent, int executionSecond) {
        this.side = side;
        this.coach = coach;
        this.executionSecond = executionSecond;
        styles.put(side, own.style());
        styles.put(side.opposite(), opponent.style());
        coach.decide(List.of(events.kickOff()), ScenarioMoments.deadBall());
    }

    static CoachRun of(PlayStyle ownStyle, AssessmentBand band, PlayStyle opponentStyle) {
        return of(ownStyle, band, opponentStyle, 0);
    }

    static CoachRun of(PlayStyle ownStyle, AssessmentBand band, PlayStyle opponentStyle, int executionSecond) {
        Team own = ScenarioTeams.fourFourTwo("Home", ownStyle);
        Team opponent = ScenarioTeams.fourFourTwo("Away", opponentStyle);
        return of(own, band, opponent, CoachConfig.defaults(), executionSecond);
    }

    static CoachRun of(Team own, AssessmentBand band, Team opponent) {
        return of(own, band, opponent, CoachConfig.defaults(), 0);
    }

    static CoachRun of(Team own, AssessmentBand band, Team opponent, CoachConfig config) {
        return of(own, band, opponent, config, 0);
    }

    static CoachRun withTacticalReading(int tacticalReading, PlayStyle ownStyle, AssessmentBand band,
            PlayStyle opponentStyle) {
        Team own = ScenarioTeams.fourFourTwo("Home", ownStyle);
        Team opponent = ScenarioTeams.fourFourTwo("Away", opponentStyle);
        CoachAttributes attributes = new CoachAttributes(tacticalReading);
        return of(own, band, opponent, CoachConfig.defaults(), attributes, 0);
    }

    private static CoachRun of(Team own, AssessmentBand band, Team opponent, CoachConfig config,
            int executionSecond) {
        return of(own, band, opponent, config, ScenarioTeams.NEUTRAL_COACH, executionSecond);
    }

    private static CoachRun of(Team own, AssessmentBand band, Team opponent, CoachConfig config,
            CoachAttributes attributes, int executionSecond) {
        MatchSide side = MatchSide.HOME;
        PreMatchDecision preMatch = ScenarioTeams.preMatch(own, band);
        AutomaticCoach coach = new AutomaticCoach(config, attributes);
        coach.open(side, preMatch, opponent, ScenarioTeams.ALWAYS_THE_FIRST);
        return new CoachRun(side, coach, own, opponent, executionSecond);
    }

    ScenarioEvents events() {
        return events;
    }

    Optional<Intervention> deliver(MatchEvent... batch) {
        List<MatchEvent> delivery = new ArrayList<>(executedAtTheNextDelivery);
        executedAtTheNextDelivery.clear();
        delivery.addAll(List.of(batch));
        MatchEvent last = delivery.get(delivery.size() - 1);
        minute = last.time().minute();
        delivery.forEach(this::followStyle);
        List<Intervention> requests = coach.decide(delivery, ScenarioMoments.deadBall());
        Optional<Intervention> decision = requests.stream().findFirst();
        decision.ifPresent(this::execute);
        return decision;
    }

    void playUntil(int targetMinute) {
        while (minute < targetMinute) {
            int next = minute + 1;
            if (next == HALF_TIME_MINUTE) {
                deliver(events.firstHalfEnd(0));
                deliver(events.secondHalfStart());
                continue;
            }
            deliver(events.circulation(MatchSide.HOME, next, 0));
        }
    }

    List<Decision> decisions() {
        return List.copyOf(decisions);
    }

    List<Integer> decisionMinutes() {
        return decisions.stream()
                .map(Decision::minute)
                .toList();
    }

    private void followStyle(MatchEvent event) {
        if (event.detail() instanceof EventDetail.StyleChange change) {
            styles.put(event.side(), change.to());
        }
    }

    private void execute(Intervention intervention) {
        decisions.add(new Decision(minute, intervention));
        MatchEvent executed = switch (intervention) {
            case Intervention.StyleChange change -> executeStyleChange(change);
            case Intervention.VoluntarySubstitution substitution -> executeSubstitution(substitution);
            case Intervention.SectorMove move -> executeMove(move);
            case Intervention.FormationChange change ->
                    throw new IllegalStateException("the automatic coach never asks for a formation change");
        };
        executedAtTheNextDelivery.add(executed);
    }

    private MatchEvent executeStyleChange(Intervention.StyleChange change) {
        PlayStyle before = styles.get(side);
        styles.put(side, change.style());
        return events.styleChange(side, before, change.style(), change.reason(), minute, executionSecond);
    }

    private MatchEvent executeSubstitution(Intervention.VoluntarySubstitution substitution) {
        Role sector = substitution.entering().role();
        return events.substitution(side, substitution.leaving(), substitution.entering(), sector,
                substitution.reason(), minute, executionSecond);
    }

    private MatchEvent executeMove(Intervention.SectorMove move) {
        return events.move(side, move.player(), move.sector(), move.reason(), minute, executionSecond);
    }
}
