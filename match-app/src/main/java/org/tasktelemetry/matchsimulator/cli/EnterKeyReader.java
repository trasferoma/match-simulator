package org.tasktelemetry.matchsimulator.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

final class EnterKeyReader {

    private final BufferedReader input;

    EnterKeyReader(BufferedReader input) {
        this.input = input;
    }

    static EnterKeyReader standard(InputStream in) {
        InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
        BufferedReader bufferedReader = new BufferedReader(reader);
        return new EnterKeyReader(bufferedReader);
    }

    void waitForEnter() {
        try {
            input.readLine();
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot read the Enter key from the standard input", exception);
        }
    }
}
