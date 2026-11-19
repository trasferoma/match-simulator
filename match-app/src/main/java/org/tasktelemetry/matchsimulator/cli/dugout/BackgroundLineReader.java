package org.tasktelemetry.matchsimulator.cli.dugout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

public final class BackgroundLineReader implements DugoutInput, AutoCloseable {

    private static final String THREAD_NAME = "dugout-line-reader";

    private final BufferedReader input;
    private final Deque<String> queuedLines = new ArrayDeque<>();
    private boolean inputEnded;

    public BackgroundLineReader(BufferedReader input) {
        this.input = Objects.requireNonNull(input, "input must not be null");
    }

    public static BackgroundLineReader startedOn(InputStream in) {
        Objects.requireNonNull(in, "input stream must not be null");
        InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
        BufferedReader bufferedReader = new BufferedReader(reader);
        BackgroundLineReader lines = new BackgroundLineReader(bufferedReader);
        lines.start();
        return lines;
    }

    public void start() {
        Thread thread = new Thread(this::pumpLines, THREAD_NAME);
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public synchronized boolean isPauseRequested() {
        return !inputEnded && !queuedLines.isEmpty();
    }

    @Override
    public synchronized void discardQueuedLines() {
        queuedLines.clear();
    }

    @Override
    public synchronized Optional<String> awaitLine() {
        while (queuedLines.isEmpty() && !inputEnded) {
            waitForALine();
        }
        String line = queuedLines.pollFirst();
        return Optional.ofNullable(line);
    }

    @Override
    public void close() {
        endInput();
    }

    void pumpLines() {
        Optional<String> line = readLine();
        while (line.isPresent() && !isInputEnded()) {
            queue(line.get());
            line = readLine();
        }
        endInput();
    }

    private Optional<String> readLine() {
        try {
            String line = input.readLine();
            return Optional.ofNullable(line);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private synchronized void queue(String line) {
        if (inputEnded) {
            return;
        }
        queuedLines.addLast(line);
        notifyAll();
    }

    private synchronized boolean isInputEnded() {
        return inputEnded;
    }

    private synchronized void endInput() {
        inputEnded = true;
        notifyAll();
    }

    private void waitForALine() {
        try {
            wait();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for a line from the standard input",
                    exception);
        }
    }
}
