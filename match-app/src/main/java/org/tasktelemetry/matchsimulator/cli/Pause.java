package org.tasktelemetry.matchsimulator.cli;

import java.time.Duration;

interface Pause {

    void waitFor(Duration duration);
}
