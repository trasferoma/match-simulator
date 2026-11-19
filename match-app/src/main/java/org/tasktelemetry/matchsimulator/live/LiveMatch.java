package org.tasktelemetry.matchsimulator.live;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.coach.MatchCoaches;
import org.tasktelemetry.matchsimulator.coach.MatchMoment;
import org.tasktelemetry.matchsimulator.coach.TieBreakDraw;
import org.tasktelemetry.matchsimulator.engine.Match;
import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;

public final class LiveMatch {

    private static final long SIDE_SEED_STEP = 0x9E3779B97F4A7C15L;

    private final MatchEngine engine;

    public LiveMatch(MatchEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    public List<MatchEvent> play(PreparedMatch prepared, long seed, MatchObserver observer) {
        Objects.requireNonNull(prepared, "prepared match must not be null");
        Objects.requireNonNull(observer, "observer must not be null");
        MatchCoaches coaches = prepared.coaches();
        try {
            openTheCoaches(prepared, seed);
            return playToTheEnd(prepared, seed, observer);
        } finally {
            coaches.close();
        }
    }

    private void openTheCoaches(PreparedMatch prepared, long seed) {
        TieBreakDraw homeDraw = tieBreakDrawOf(seed, MatchSide.HOME);
        TieBreakDraw awayDraw = tieBreakDrawOf(seed, MatchSide.AWAY);
        prepared.coaches().open(prepared.homeDecision(), prepared.awayDecision(), homeDraw, awayDraw);
    }

    private TieBreakDraw tieBreakDrawOf(long seed, MatchSide side) {
        long sideSeed = seed + (side.ordinal() + 1L) * SIDE_SEED_STEP;
        MatchRandom random = new MatchRandom(sideSeed);
        return random::nextDouble;
    }

    private List<MatchEvent> playToTheEnd(PreparedMatch prepared, long seed, MatchObserver observer) {
        Match match = engine.start(prepared.home(), prepared.away(), seed);
        MatchCoaches coaches = prepared.coaches();
        int deliveredCount = 0;
        while (!match.isFinished()) {
            match.step();
            deliveredCount = deliverAndLetCoachesIntervene(match, coaches, observer, deliveredCount);
        }
        observer.matchFinished();
        return match.events();
    }

    private int deliverAndLetCoachesIntervene(Match match, MatchCoaches coaches, MatchObserver observer,
            int deliveredCount) {
        List<MatchEvent> newEvents = takeNewEvents(match, deliveredCount);
        int countAfterDelivery = deliveredCount + newEvents.size();
        deliverToObserver(newEvents, observer);
        boolean requested = letCoachesIntervene(match, coaches, newEvents);
        if (requested && endsThePlayOrReachesHalfTime(match, countAfterDelivery)) {
            return deliverAndLetCoachesIntervene(match, coaches, observer, countAfterDelivery);
        }
        return countAfterDelivery;
    }

    private boolean endsThePlayOrReachesHalfTime(Match match, int deliveredCount) {
        if (match.isFinished()) {
            return true;
        }
        List<MatchEvent> executedEvents = takeNewEvents(match, deliveredCount);
        return executedEvents.stream().anyMatch(event -> event.type() == EventType.FIRST_HALF_END);
    }

    private List<MatchEvent> takeNewEvents(Match match, int deliveredCount) {
        List<MatchEvent> allEvents = match.events();
        int totalCount = allEvents.size();
        List<MatchEvent> newEvents = allEvents.subList(deliveredCount, totalCount);
        return List.copyOf(newEvents);
    }

    private void deliverToObserver(List<MatchEvent> newEvents, MatchObserver observer) {
        if (newEvents.isEmpty()) {
            return;
        }
        observer.eventsHappened(newEvents);
    }

    private boolean letCoachesIntervene(Match match, MatchCoaches coaches, List<MatchEvent> newEvents) {
        if (newEvents.isEmpty() || match.isFinished()) {
            return false;
        }
        MatchMoment moment = momentOf(match);
        List<Intervention> requests = askCoaches(coaches, newEvents, moment);
        List<InterventionResult> results = applyInOrder(match, requests);
        tellCoachesTheResults(coaches, requests, results);
        return !requests.isEmpty();
    }

    private List<Intervention> askCoaches(MatchCoaches coaches, List<MatchEvent> newEvents, MatchMoment moment) {
        return coaches.decide(newEvents, moment);
    }

    private void tellCoachesTheResults(MatchCoaches coaches, List<Intervention> requests,
            List<InterventionResult> results) {
        coaches.settle(requests, results);
    }

    private MatchMoment momentOf(Match match) {
        boolean ballLive = match.isBallLive();
        return new MatchMoment(ballLive, match::previewInterventions);
    }

    private List<InterventionResult> applyInOrder(Match match, List<Intervention> requests) {
        List<InterventionResult> results = new ArrayList<>();
        for (Intervention request : requests) {
            InterventionResult result = match.apply(request);
            results.add(result);
        }
        return results;
    }
}
