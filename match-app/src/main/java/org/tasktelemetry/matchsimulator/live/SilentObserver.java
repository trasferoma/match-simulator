package org.tasktelemetry.matchsimulator.live;

import java.util.List;

import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;

public final class SilentObserver implements MatchObserver {

    @Override
    public void eventsHappened(List<MatchEvent> newEvents) {
    }

    @Override
    public void matchFinished() {
    }
}
