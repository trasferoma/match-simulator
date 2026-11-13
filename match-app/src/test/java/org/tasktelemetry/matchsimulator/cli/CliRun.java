package org.tasktelemetry.matchsimulator.cli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

record CliRun(int exitCode, byte[] outBytes, byte[] errBytes) {

    private static final String TEAMS_DIRECTORY_PROPERTY = "teams.dir";
    private static final String HOME_FILE = "esempio-casa.json";
    private static final String AWAY_FILE = "esempio-ospite.json";

    static CliRun of(String... arguments) {
        return withInput("", arguments);
    }

    static CliRun withInput(String input, String... arguments) {
        InputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        MatchCommandLine commandLine = MatchCommandLine.standard(in, out, err);
        int exitCode = commandLine.run(arguments);
        return new CliRun(exitCode, out.toByteArray(), err.toByteArray());
    }

    static CliRun withTerminal(boolean interactiveTerminal, Pause pause, String... arguments) {
        return withTerminal(interactiveTerminal, StandardCharsets.UTF_8, pause, arguments);
    }

    static CliRun withConsoleCharset(boolean interactiveTerminal, Charset consoleCharset, String... arguments) {
        return withTerminal(interactiveTerminal, consoleCharset, duration -> { }, arguments);
    }

    private static CliRun withTerminal(boolean interactiveTerminal, Charset consoleCharset, Pause pause,
            String... arguments) {
        InputStream in = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        MatchCommandLine commandLine = MatchCommandLine.standard(in, out, err, pause, interactiveTerminal,
                consoleCharset);
        int exitCode = commandLine.run(arguments);
        return new CliRun(exitCode, out.toByteArray(), err.toByteArray());
    }

    static Path homeFile() {
        return teamsDirectory().resolve(HOME_FILE);
    }

    static Path awayFile() {
        return teamsDirectory().resolve(AWAY_FILE);
    }

    String out() {
        return new String(outBytes, StandardCharsets.UTF_8);
    }

    String err() {
        return new String(errBytes, StandardCharsets.UTF_8);
    }

    private static Path teamsDirectory() {
        String value = System.getProperty(TEAMS_DIRECTORY_PROPERTY);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("system property " + TEAMS_DIRECTORY_PROPERTY
                    + " is not set (Surefire of match-app sets it)");
        }
        return Path.of(value);
    }
}
