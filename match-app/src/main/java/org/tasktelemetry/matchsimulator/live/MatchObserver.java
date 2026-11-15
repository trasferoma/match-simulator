package org.tasktelemetry.matchsimulator.live;

import java.util.List;

import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;

public interface MatchObserver {

    void eventsHappened(List<MatchEvent> newEvents);

    void matchFinished();
}
