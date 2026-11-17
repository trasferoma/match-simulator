package org.tasktelemetry.matchsimulator.coach.decision;

import java.util.List;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.coach.config.TimingConfig;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;

public final class DecisionMoments {

    private final TimingConfig timing;

    public DecisionMoments(TimingConfig timing) {
        this.timing = timing;
    }

    public Optional<DecisionMoment> momentOf(MatchTime previousTime, List<MatchEvent> newEvents, MatchSide own) {
        if (newEvents.isEmpty() || endsTheMatch(newEvents)) {
            return Optional.empty();
        }
        if (reachesHalfTime(newEvents)) {
            return Optional.of(DecisionMoment.HALF_TIME);
        }
        MatchTime latestTime = timeOfLast(newEvents);
        boolean periodicCheckDue = isPeriodicCheckDue(previousTime, latestTime);
        if (latestTime.period() == MatchPeriod.FIRST_HALF) {
            return firstHalfMoment(periodicCheckDue, newEvents);
        }
        return secondHalfMoment(periodicCheckDue, newEvents, own);
    }

    private Optional<DecisionMoment> firstHalfMoment(boolean periodicCheckDue, List<MatchEvent> newEvents) {
        boolean dismissalHappened = newEvents.stream().anyMatch(event -> event.type().isDismissal());
        if (periodicCheckDue || dismissalHappened) {
            return Optional.of(DecisionMoment.FIRST_HALF_EMERGENCY);
        }
        return Optional.empty();
    }

    private Optional<DecisionMoment> secondHalfMoment(boolean periodicCheckDue, List<MatchEvent> newEvents,
            MatchSide own) {
        boolean strongEventHappened = newEvents.stream().anyMatch(event -> isStrongEvent(event, own));
        if (periodicCheckDue || strongEventHappened) {
            return Optional.of(DecisionMoment.SECOND_HALF);
        }
        return Optional.empty();
    }

    private boolean isStrongEvent(MatchEvent event, MatchSide own) {
        boolean isGoalOrDismissal = event.type() == EventType.GOAL || event.type().isDismissal();
        boolean isOpponentIntervention = event.isVoluntaryIntervention() && event.side() == own.opposite();
        return isGoalOrDismissal || isOpponentIntervention;
    }

    private boolean isPeriodicCheckDue(MatchTime previousTime, MatchTime latestTime) {
        if (previousTime.period() != latestTime.period()) {
            return false;
        }
        return checkBlockOf(latestTime) > checkBlockOf(previousTime);
    }

    private int checkBlockOf(MatchTime time) {
        return time.minute() / timing.checkIntervalMinutes();
    }

    private boolean endsTheMatch(List<MatchEvent> events) {
        return events.stream().anyMatch(event -> event.type() == EventType.MATCH_END
                || event.type() == EventType.MATCH_SUSPENDED);
    }

    private boolean reachesHalfTime(List<MatchEvent> events) {
        return events.stream().anyMatch(event -> event.type() == EventType.FIRST_HALF_END);
    }

    private MatchTime timeOfLast(List<MatchEvent> events) {
        MatchEvent last = events.get(events.size() - 1);
        return last.time();
    }
}
