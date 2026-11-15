package org.tasktelemetry.matchsimulator.live;

import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.Match;
import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public final class LiveMatch {

    private final MatchEngine engine;

    public LiveMatch(MatchEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    public List<MatchEvent> play(Team home, Team away, long seed, MatchObserver observer) {
        Objects.requireNonNull(observer, "observer must not be null");
        Match match = engine.start(home, away, seed);
        int deliveredCount = 0;
        while (!match.isFinished()) {
            match.step();
            deliveredCount = deliverNewEvents(match, deliveredCount, observer);
        }
        observer.matchFinished();
        return match.events();
    }

    private int deliverNewEvents(Match match, int deliveredCount, MatchObserver observer) {
        List<MatchEvent> allEvents = match.events();
        int totalCount = allEvents.size();
        if (totalCount == deliveredCount) {
            return deliveredCount;
        }
        List<MatchEvent> newEvents = allEvents.subList(deliveredCount, totalCount);
        List<MatchEvent> unmodifiableNewEvents = List.copyOf(newEvents);
        observer.eventsHappened(unmodifiableNewEvents);
        return totalCount;
    }
}
