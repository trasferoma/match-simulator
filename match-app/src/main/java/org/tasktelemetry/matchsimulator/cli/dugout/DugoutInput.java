package org.tasktelemetry.matchsimulator.cli.dugout;

import java.util.Optional;

public interface DugoutInput {

    boolean isPauseRequested();

    void discardQueuedLines();

    Optional<String> awaitLine();
}
