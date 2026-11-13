package org.tasktelemetry.matchsimulator.cli;

import java.time.Duration;

final class ThreadSleepPause implements Pause {

    @Override
    public void waitFor(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("the pause of " + duration.toMillis() + " ms was interrupted", exception);
        }
    }
}
