package org.tasktelemetry.matchsimulator.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.message.UserMessages;
import org.tasktelemetry.matchsimulator.summary.ContourCounters;
import org.tasktelemetry.matchsimulator.summary.MatchSummary;
import org.tasktelemetry.matchsimulator.summary.ScoredGoal;
import org.tasktelemetry.matchsimulator.summary.Suspension;
import org.tasktelemetry.matchsimulator.summary.TeamStatistics;

class MatchPrinterTest {

    private static final int SECONDS_PER_MINUTE = 60;
    private static final PlayerAttributes ATTRIBUTES = new PlayerAttributes(60, 60, 60, 60, 60, 60, 60, 60);
    private static final Player SCORER = new Player("Nicolò Pàstina", Role.FORWARD, ATTRIBUTES);

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final EnterKeyProbe enterKey = new EnterKeyProbe();
    private final MatchPrinter printer = new MatchPrinter(UserMessages.italian(),
            new PrintStream(bytes, true, StandardCharsets.UTF_8), new EnterKeyReader(enterKey));

    @Test
    void aPlayedMatchShowsTheScoreScorersWithMinuteAndTheStatisticsOfBothTeams() {
        MatchSummary summary = summaryWithOneHomeGoalAt(secondHalfAfterMinutes(2));

        printer.print(report(summary));

        String output = output();
        assertThat(output).contains("Risultato: Rossi 1 - 0 Blu");
        assertThat(output).contains("Marcatori:", "47' Nicolò Pàstina (Rossi)");
        assertThat(output).contains("Statistiche (Rossi - Blu)");
        assertThat(output).contains("Tiri: 10 - 4", "Tiri in porta: 5 - 2", "xG: 1,25 - 0,50");
        assertThat(output).contains("Possesso: 60% - 40%", "Angoli: 6 - 3", "Cross: 8 - 7", "Falli: 11 - 12");
        assertThat(output).contains("Gialli: 2 - 1", "Rossi: 0 - 1", "Fuorigioco: 3 - 2", "Cambi: 3 - 5");
        assertThat(output).doesNotContain("sospesa", "a tavolino");
    }

    @Test
    void aStoppageTimeGoalIsShownWithTheStoppageMinute() {
        MatchSummary summary = summaryWithOneHomeGoalAt(secondHalfAfterMinutes(47));

        printer.print(report(summary));

        assertThat(output()).contains("90+2' Nicolò Pàstina (Rossi)");
    }

    @Test
    void aSuspendedMatchShowsTheOfficialResultTheFieldScoreTheMinuteAndTheResponsibleTeam() {
        MatchTime suspendedAt = secondHalfAfterMinutes(18);
        Suspension suspension = new Suspension(MatchSide.HOME, new Score(0, 3), suspendedAt);
        MatchSummary summary = new MatchSummary(List.of(), statistics(), statistics(), suspension);

        printer.print(report(summary));

        assertThat(output()).contains(
                "Partita sospesa al minuto 63'",
                "Squadra responsabile: Rossi",
                "Risultato ufficiale (a tavolino): Rossi 0 - 3 Blu",
                "Punteggio sul campo: Rossi 0 - 0 Blu");
        assertThat(output()).doesNotContain("Risultato: ");
    }

    @Test
    void aMatchWithoutGoalsShowsNoScorersBlock() {
        MatchSummary summary = new MatchSummary(List.of(), statistics(), statistics());

        printer.print(report(summary));

        assertThat(output()).contains("Risultato: Rossi 0 - 0 Blu").doesNotContain("Marcatori:");
    }

    @Test
    void theChronicleLinesComeFirstAndTheSeedLast() {
        MatchSummary summary = new MatchSummary(List.of(), statistics(), statistics());
        MatchReport report = new MatchReport("Rossi", "Blu", List.of(List.of("0' Inizia la partita.")), summary, -7L);

        printer.print(report);

        String output = output();
        assertThat(output).startsWith("0' Inizia la partita.");
        assertThat(output.stripTrailing()).endsWith("Seme: -7");
    }

    @Test
    void aBlankLineSeparatesEachEventFromTheNextAndTheScoreboardButNothingIsWaitedFor() {
        printer.print(reportWithEvents(3));

        List<String> lines = output().lines().toList();
        assertThat(lines.subList(0, 9)).containsExactly(
                "event 1 field", "event 1 sentence", "",
                "event 2 field", "event 2 sentence", "",
                "event 3 field", "event 3 sentence", "");
        assertThat(lines.get(9)).startsWith("Tabellino");
        assertThat(enterKey.waits()).isZero();
    }

    @Test
    void stepByStepWaitsOnceBeforeEveryEventExceptTheFirstAndNeverBeforeTheScoreboard() {
        enterKey.pressesAvailable(10);

        printer.printStepByStep(reportWithEvents(3));

        assertThat(enterKey.waits()).isEqualTo(2);
        assertThat(enterKey.outputAtWait(0)).isEqualTo(linesOf("event 1 field", "event 1 sentence"));
        assertThat(enterKey.outputAtWait(1))
                .isEqualTo(linesOf("event 1 field", "event 1 sentence", "event 2 field", "event 2 sentence"));
        assertThat(output()).contains("event 3 sentence", "Tabellino", "Seme: 42");
    }

    @Test
    void stepByStepDoesNotPrintTheBlankLineBetweenEventsBecauseTheEnterKeyAlreadyMakesOne() {
        enterKey.pressesAvailable(10);

        printer.printStepByStep(reportWithEvents(3));

        List<String> lines = output().lines().toList();
        assertThat(lines.subList(0, 7)).containsExactly(
                "event 1 field", "event 1 sentence",
                "event 2 field", "event 2 sentence",
                "event 3 field", "event 3 sentence", "");
        assertThat(lines.get(7)).startsWith("Tabellino");
    }

    @Test
    void stepByStepKeepsPrintingWhenTheInputEndsInTheMiddleOfTheChronicle() {
        enterKey.pressesAvailable(1);

        printer.printStepByStep(reportWithEvents(4));

        assertThat(enterKey.waits()).isEqualTo(3);
        assertThat(output()).contains("event 4 sentence", "Tabellino", "Seme: 42");
    }

    @Test
    void stepByStepWithOneEventOrNoneNeverWaits() {
        printer.printStepByStep(reportWithEvents(1));
        printer.printStepByStep(reportWithEvents(0));

        assertThat(enterKey.waits()).isZero();
        assertThat(output()).contains("event 1 sentence");
    }

    private MatchReport reportWithEvents(int count) {
        List<List<String>> events = new ArrayList<>();
        for (int number = 1; number <= count; number++) {
            events.add(List.of("event " + number + " field", "event " + number + " sentence"));
        }
        MatchSummary summary = new MatchSummary(List.of(), statistics(), statistics());
        return new MatchReport("Rossi", "Blu", events, summary, 42L);
    }

    private String linesOf(String... lines) {
        return String.join(System.lineSeparator(), lines) + System.lineSeparator();
    }

    private MatchSummary summaryWithOneHomeGoalAt(MatchTime time) {
        ScoredGoal goal = new ScoredGoal(MatchSide.HOME, SCORER, time);
        ContourCounters homeContour = new ContourCounters(6, 8, 11, 2, 0, 3, 3, 0, 0, 0, 0, 0);
        ContourCounters awayContour = new ContourCounters(3, 7, 12, 1, 1, 2, 5, 0, 0, 0, 0, 0);
        TeamStatistics home = new TeamStatistics(10, 5, 1.25, 600, homeContour);
        TeamStatistics away = new TeamStatistics(4, 2, 0.5, 400, awayContour);
        return new MatchSummary(List.of(goal), home, away);
    }

    private TeamStatistics statistics() {
        return new TeamStatistics(0, 0, 0.0, 0, ContourCounters.none());
    }

    private MatchTime secondHalfAfterMinutes(int minutes) {
        return new MatchTime(MatchPeriod.SECOND_HALF, minutes * SECONDS_PER_MINUTE);
    }

    private MatchReport report(MatchSummary summary) {
        return new MatchReport("Rossi", "Blu", List.of(), summary, 42L);
    }

    private String output() {
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private final class EnterKeyProbe extends BufferedReader {

        private final List<String> outputAtEachWait = new ArrayList<>();
        private int pressesAvailable;

        EnterKeyProbe() {
            super(new StringReader(""));
        }

        void pressesAvailable(int count) {
            pressesAvailable = count;
        }

        int waits() {
            return outputAtEachWait.size();
        }

        String outputAtWait(int index) {
            return outputAtEachWait.get(index);
        }

        @Override
        public String readLine() {
            outputAtEachWait.add(output());
            if (pressesAvailable == 0) {
                return null;
            }
            pressesAvailable--;
            return "";
        }
    }
}
