package org.tasktelemetry.matchsimulator.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class MatchCommandLineControlTest {

    private static final String USAGE_OPTION = "[--control casa|ospite]";
    private static final String SEED_NOT_ENOUGH_TEXT =
            "Con un allenatore umano la partita non si rigioca dal solo seme";
    private static final Pause NO_WAIT = duration -> { };

    private final String home = CliRun.homeFile().toString();
    private final String away = CliRun.awayFile().toString();

    @Test
    void controllingTheHomeSideStartsAndPlaysTheWholeMatchWhenTheInputIsAlreadyFinished() {
        CliRun run = runWith("", false, home, away, "--seed", "42", "--highlights", "--animate", "--control", "casa");

        assertThat(run.exitCode()).isZero();
        assertThat(run.err()).isEmpty();
        assertThat(run.out()).contains("Tabellino").contains("Seme: 42").contains("Formazioni");
    }

    @Test
    void controllingTheAwaySideStartsOnAnInteractiveTerminalWithoutTheAnimateOption() {
        CliRun run = runWith("", true, home, away, "--seed", "42", "--highlights", "--control", "ospite");

        assertThat(run.exitCode()).isZero();
        assertThat(run.err()).isEmpty();
        assertThat(run.out()).contains("Tabellino").contains("Seme: 42");
    }

    @Test
    void theLinesWaitingOnTheInputDoNotStopTheMatchFromEnding() {
        String keyboardLines = "\n\nriprendi\n".repeat(50);

        CliRun run = runWith(keyboardLines, false, home, away, "--seed", "42", "--highlights", "--animate",
                "--control", "casa");

        assertThat(run.exitCode()).isZero();
        assertThat(run.err()).isEmpty();
        assertThat(run.out()).contains("Tabellino");
    }

    @Test
    void theScoreboardOfAControlledMatchSaysItIsNotReplayableFromTheSeedAlone() {
        CliRun controlled = runWith("", false, home, away, "--seed", "42", "--highlights", "--animate", "--control",
                "casa");
        CliRun automatic = runWith("", false, home, away, "--seed", "42", "--highlights", "--animate");

        assertThat(scoreboardOf(controlled)).contains("Seme: 42" + System.lineSeparator() + SEED_NOT_ENOUGH_TEXT);
        assertThat(automatic.out()).doesNotContain(SEED_NOT_ENOUGH_TEXT);
    }

    @Test
    void withoutTheControlOptionTheHighlightsRunIsTheSameAsBefore() {
        CliRun first = runWith("", false, home, away, "--seed", "42", "--highlights", "--animate");
        CliRun second = runWith("", false, home, away, "--seed", "42", "--highlights", "--animate");

        assertThat(first.exitCode()).isZero();
        assertThat(first.out()).isEqualTo(second.out());
    }

    @Test
    void theControlOptionWithoutHighlightsIsAUsageError() {
        CliRun run = runWith("", true, home, away, "--control", "casa");

        assertUsageError(run, "l'opzione --control si usa solo insieme a --highlights");
    }

    @Test
    void theControlOptionWithoutAnAnimatedSessionIsAUsageErrorThatSuggestsAnimate() {
        CliRun run = runWith("", false, home, away, "--highlights", "--control", "casa");

        assertUsageError(run, "l'opzione --control richiede la cronaca animata", "aggiungi --animate");
    }

    @Test
    void theControlOptionWithoutValueIsAUsageError() {
        CliRun run = runWith("", true, home, away, "--highlights", "--control");

        assertUsageError(run, "l'opzione --control richiede la squadra da guidare: casa oppure ospite");
    }

    @Test
    void theControlOptionWithAnUnknownSideIsAUsageErrorWithTheTextGiven() {
        CliRun run = runWith("", true, home, away, "--highlights", "--control", "banana");

        assertUsageError(run, "la squadra \"banana\" dell'opzione --control non esiste");
    }

    @Test
    void aRepeatedControlOptionIsAUsageError() {
        CliRun run = runWith("", true, home, away, "--highlights", "--control", "casa", "--control", "ospite");

        assertUsageError(run, "l'opzione --control è indicata più di una volta");
    }

    @Test
    void theUsageTextQuotesTheControlOption() {
        CliRun run = runWith("", true, home, away, "--control");

        assertThat(run.err()).contains(USAGE_OPTION);
    }

    @Test
    void aTeamFileProblemIsNotReportedBeforeAControlUsageError() {
        CliRun run = runWith("", false, "manca-casa.json", "manca-ospite.json", "--highlights", "--control", "casa");

        assertUsageError(run, "l'opzione --control richiede la cronaca animata");
    }

    @Test
    void theFirstPauseOfAControlledMatchIsTheSameLineupsPauseAsWithoutControl() {
        List<Duration> controlledPauses = new ArrayList<>();
        List<Duration> automaticPauses = new ArrayList<>();

        runWith("", false, controlledPauses::add, home, away, "--seed", "42", "--highlights", "--animate",
                "--control", "casa");
        runWith("", false, automaticPauses::add, home, away, "--seed", "42", "--highlights", "--animate");

        assertThat(controlledPauses).isNotEmpty();
        assertThat(controlledPauses.getFirst()).isEqualTo(automaticPauses.getFirst());
    }

    private CliRun runWith(String input, boolean interactiveTerminal, String... arguments) {
        return runWith(input, interactiveTerminal, NO_WAIT, arguments);
    }

    private CliRun runWith(String input, boolean interactiveTerminal, Pause pause, String... arguments) {
        InputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        MatchCommandLine commandLine = MatchCommandLine.standard(in, out, err, pause, interactiveTerminal,
                StandardCharsets.UTF_8);
        int exitCode = commandLine.run(arguments);
        return new CliRun(exitCode, out.toByteArray(), err.toByteArray());
    }

    private String scoreboardOf(CliRun run) {
        String output = run.out();
        return output.substring(output.indexOf("Tabellino"));
    }

    private void assertUsageError(CliRun run, String... expectedFragments) {
        assertThat(run.exitCode()).isEqualTo(MatchCommandLine.EXIT_USAGE_ERROR);
        assertThat(run.outBytes()).isEmpty();
        assertThat(run.err()).contains("Errore:").contains(USAGE_OPTION);
        for (String fragment : expectedFragments) {
            assertThat(run.err()).contains(fragment);
        }
    }
}
