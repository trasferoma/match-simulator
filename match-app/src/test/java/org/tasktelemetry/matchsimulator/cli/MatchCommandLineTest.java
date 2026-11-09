package org.tasktelemetry.matchsimulator.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MatchCommandLineTest {

    private static final String USAGE = "Uso: match-simulator casa.json ospite.json [--seed 42] [--full]";
    private static final Pattern ACCENTED_LETTER = Pattern.compile("[àèéìòù]");
    private static final Pattern EXPECTED_GOALS_ROW = Pattern.compile("(?m)^xG: \\d+,\\d{2} - \\d+,\\d{2}$");
    private static final Pattern POSSESSION_ROW = Pattern.compile("(?m)^Possesso: \\d{1,3}% - \\d{1,3}%$");
    private static final Pattern RESULT_ROW =
            Pattern.compile("(?m)^Risultato: Atletico Mare \\d+ - \\d+ Sporting Collina$");
    private static final Pattern SEED_ROW = Pattern.compile("(?m)^Seme: (-?\\d+)$");
    private static final byte UTF8_TWO_BYTE_LEAD = (byte) 0xC3;

    @TempDir
    Path directory;

    private final String home = CliRun.homeFile().toString();
    private final String away = CliRun.awayFile().toString();

    @Test
    void aValidMatchPrintsChronicleScoreboardAndSeedWithExitCodeZero() {
        CliRun run = CliRun.of(home, away, "--seed", "42");

        assertThat(run.exitCode()).isZero();
        assertThat(run.err()).isEmpty();
        assertThat(run.out())
                .contains("Atletico Mare")
                .contains("Sporting Collina")
                .contains("Tabellino")
                .contains("Statistiche (Atletico Mare - Sporting Collina)")
                .contains("Seme: 42");
        assertThat(run.out()).containsPattern(RESULT_ROW);
    }

    @Test
    void theChronicleComesBeforeTheScoreboardAndTheSeedIsTheLastLine() {
        CliRun run = CliRun.of(home, away, "--seed", "42");

        String output = run.out();
        int scoreboard = output.indexOf("Tabellino");
        int firstChronicleLine = output.indexOf("0'");
        assertThat(firstChronicleLine).isGreaterThanOrEqualTo(0).isLessThan(scoreboard);
        assertThat(output.stripTrailing()).endsWith("Seme: 42");
    }

    @Test
    void theOutputIsUtf8WithItalianAccentedLetters() {
        CliRun run = CliRun.of(home, away, "--seed", "42");

        String output = run.out();
        assertThat(output).containsPattern(ACCENTED_LETTER);
        assertThat(output.getBytes(StandardCharsets.UTF_8)).isEqualTo(run.outBytes());
        assertThat(run.outBytes()).contains(UTF8_TWO_BYTE_LEAD);
    }

    @Test
    void numbersAreWrittenInItalianFormat() {
        CliRun run = CliRun.of(home, away, "--seed", "42");

        assertThat(run.out()).containsPattern(EXPECTED_GOALS_ROW);
        assertThat(run.out()).containsPattern(POSSESSION_ROW);
    }

    @Test
    void theSameSeedGivesTheSameBytes() {
        CliRun first = CliRun.of(home, away, "--seed", "42");
        CliRun second = CliRun.of(home, away, "--seed", "42");

        assertThat(second.outBytes()).isEqualTo(first.outBytes());
    }

    @Test
    void aDifferentSeedGivesADifferentMatch() {
        CliRun first = CliRun.of(home, away, "--seed", "42");
        CliRun second = CliRun.of(home, away, "--seed", "43");

        assertThat(second.outBytes()).isNotEqualTo(first.outBytes());
    }

    @Test
    void withoutASeedOneIsGeneratedPrintedAndAReplayWithItGivesTheSameMatch() {
        CliRun generated = CliRun.of(home, away);

        Matcher seedRow = SEED_ROW.matcher(generated.out());
        assertThat(seedRow.find()).isTrue();
        String seed = seedRow.group(1);
        CliRun replay = CliRun.of(home, away, "--seed", seed);
        assertThat(generated.exitCode()).isZero();
        assertThat(replay.outBytes()).isEqualTo(generated.outBytes());
    }

    @Test
    void theOptionsWorkInAnyPosition() {
        CliRun atTheEnd = CliRun.of(home, away, "--seed", "42", "--full");
        CliRun atTheStart = CliRun.of("--seed", "42", "--full", home, away);
        CliRun inTheMiddle = CliRun.of(home, "--full", "--seed", "42", away);

        assertThat(atTheStart.outBytes()).isEqualTo(atTheEnd.outBytes());
        assertThat(inTheMiddle.outBytes()).isEqualTo(atTheEnd.outBytes());
    }

    @Test
    void theFullOptionPrintsMoreChronicleThanTheDefault() {
        CliRun brief = CliRun.of(home, away, "--seed", "42");
        CliRun full = CliRun.of(home, away, "--seed", "42", "--full");

        assertThat(full.exitCode()).isZero();
        assertThat(full.out().lines().count()).isGreaterThan(brief.out().lines().count());
        assertThat(full.out()).contains("Tabellino").contains("Seme: 42");
    }

    @Test
    void noArgumentsPrintTheUsageOnStderrAndSimulateNothing() {
        assertUsageError(CliRun.of(), "servono esattamente due file squadra", "ne sono stati indicati 0");
    }

    @Test
    void onlyOneFilePrintsTheUsageOnStderrAndSimulatesNothing() {
        assertUsageError(CliRun.of(home), "ne sono stati indicati 1");
    }

    @Test
    void threeFilesPrintTheUsageOnStderrAndSimulateNothing() {
        assertUsageError(CliRun.of(home, away, home), "ne sono stati indicati 3");
    }

    @Test
    void aSeedWithoutValuePrintsTheUsageOnStderrAndSimulatesNothing() {
        assertUsageError(CliRun.of(home, away, "--seed"), "l'opzione --seed richiede un numero intero");
    }

    @Test
    void aSeedThatIsNotANumberPrintsTheUsageOnStderrAndSimulatesNothing() {
        assertUsageError(CliRun.of(home, away, "--seed", "abc"), "il seme \"abc\" non è un numero intero");
    }

    @Test
    void anUnknownOptionPrintsTheUsageOnStderrAndSimulatesNothing() {
        assertUsageError(CliRun.of(home, away, "--verbose"), "opzione sconosciuta: --verbose");
    }

    @Test
    void aRepeatedOptionPrintsTheUsageOnStderrAndSimulatesNothing() {
        assertUsageError(CliRun.of(home, away, "--full", "--full"), "l'opzione --full è indicata più di una volta");
    }

    @Test
    void aMissingTeamFileIsReportedOnStderrWithTheFileAndSimulatesNothing() {
        Path missing = directory.resolve("non-esiste.json");

        CliRun run = CliRun.of(home, missing.toString());

        assertThat(run.exitCode()).isEqualTo(MatchCommandLine.EXIT_INVALID_TEAM_FILE).isNotZero();
        assertThat(run.outBytes()).isEmpty();
        assertThat(run.err())
                .contains("Errore:")
                .contains(missing.toString())
                .contains("il file non esiste");
    }

    @Test
    void anInvalidTeamFileIsReportedOnStderrWithFileAndFieldAndSimulatesNothing() throws IOException {
        Path invalid = writeVariantOfTheHomeTeam("\"offensivo\"", "\"Offensivo\"");

        CliRun run = CliRun.of(invalid.toString(), away);

        assertThat(run.exitCode()).isEqualTo(MatchCommandLine.EXIT_INVALID_TEAM_FILE).isNotZero();
        assertThat(run.outBytes()).isEmpty();
        assertThat(run.err())
                .contains(invalid.toString())
                .contains("campo style")
                .contains("valore sconosciuto \"Offensivo\"")
                .contains("valori ammessi: equilibrato, offensivo, difensivo, possesso, contropiede");
    }

    @Test
    void aBrokenFormationRuleIsReportedInItalianAndSimulatesNothing() throws IOException {
        Path invalid = writeVariantOfTheHomeTeam("\"role\": \"difensore\"", "\"role\": \"portiere\"");

        CliRun run = CliRun.of(home, invalid.toString());

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.outBytes()).isEmpty();
        assertThat(run.err())
                .contains("campo starters, bench")
                .contains("la squadra deve avere esattamente un portiere tra i titolari")
                .doesNotContain("must");
    }

    @Test
    void anAttributeOutOfScaleIsReportedInItalianWithTheAttributeName() throws IOException {
        Path invalid = writeVariantOfTheHomeTeam("\"passing\": 58", "\"passing\": 0");

        CliRun run = CliRun.of(invalid.toString(), away);

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.outBytes()).isEmpty();
        assertThat(run.err())
                .contains("l'attributo passing deve essere compreso tra 1 e 100, ma vale 0")
                .doesNotContain("must");
    }

    @Test
    void anInvalidSecondFileSimulatesNothingEvenIfTheFirstOneIsValid() throws IOException {
        Path invalid = writeVariantOfTheHomeTeam("\"offensivo\"", "\"Offensivo\"");

        CliRun run = CliRun.of(home, invalid.toString(), "--seed", "42");

        assertThat(run.outBytes()).isEmpty();
        assertThat(run.errBytes()).isNotEmpty();
    }

    private void assertUsageError(CliRun run, String... expectedFragments) {
        assertThat(run.exitCode()).isEqualTo(MatchCommandLine.EXIT_USAGE_ERROR).isNotZero();
        assertThat(run.outBytes()).isEmpty();
        assertThat(run.err()).contains("Errore:").contains(USAGE);
        for (String fragment : expectedFragments) {
            assertThat(run.err()).contains(fragment);
        }
    }

    private Path writeVariantOfTheHomeTeam(String target, String replacement) throws IOException {
        String content = Files.readString(CliRun.homeFile());
        String variant = content.replaceFirst(Pattern.quote(target), Matcher.quoteReplacement(replacement));
        Path file = directory.resolve("variante.json");
        return Files.writeString(file, variant);
    }
}
