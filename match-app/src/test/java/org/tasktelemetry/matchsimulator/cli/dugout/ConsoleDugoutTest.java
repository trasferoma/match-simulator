package org.tasktelemetry.matchsimulator.cli.dugout;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.config.Formation;
import org.tasktelemetry.matchsimulator.coach.config.FormationCatalog;
import org.tasktelemetry.matchsimulator.coach.human.PlannedMoves;
import org.tasktelemetry.matchsimulator.coach.human.TeamSheet;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.message.UserMessages;

class ConsoleDugoutTest {

    private final DugoutFixture fixture = new DugoutFixture();
    private final UserMessages messages = UserMessages.italian();
    private final ScriptedInput input = new ScriptedInput();
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private final List<String> alignments = new ArrayList<>();
    private final ConsoleDugout dugout = ConsoleDugout.standard(input, printStream(), messages,
            DugoutFixture.HOME_COACH, () -> alignments.add("aligned"));

    @Test
    void theSheetShowsTeamCoachMinuteScoreStyleFormationSubstitutionsAndAvailableFormations() {
        Player tiredDefender = fixture.starter(Role.DEFENDER, 0);
        TeamSheet sheet = fixture.sheetAfter(fixture.kickOff(), fixture.tired(tiredDefender, FatigueBand.TIRED, 30),
                fixture.awayGoal(63));
        PlannedMoves moves = fixture.plannedMoves(sheet, DugoutFixture.acceptEverything());

        dugout.open(sheet, moves);

        String screen = output();
        String formation = sheet.deployment().label();
        assertThat(screen).contains("Panchina di Atletico Mare (all. Giorgio Parodi) - 63' - Atletico Mare 0-1 Sporting Collina");
        assertThat(screen).contains("modulo " + formation + ", cambi 0 di " + sheet.maximumSubstitutions());
        assertThat(screen).contains("Moduli disponibili: 5-3-2, 4-5-1, 4-4-2, 3-5-2, 4-3-3");
        assertThat(screen).contains("Mosse accettate: nessuna");
    }

    @Test
    void theRosterShowsSectorBaseRoleAttributesFatigueAndCautionForThePlayersOnThePitch() {
        Player tiredDefender = fixture.starter(Role.DEFENDER, 0);
        Player exhaustedForward = fixture.starter(Role.FORWARD, 0);
        Player cautionedMidfielder = fixture.starter(Role.MIDFIELDER, 0);
        TeamSheet sheet = fixture.sheetAfter(fixture.kickOff(), fixture.tired(tiredDefender, FatigueBand.TIRED, 20),
                fixture.tired(exhaustedForward, FatigueBand.EXHAUSTED, 30),
                fixture.yellowCard(cautionedMidfielder, 35));
        PlannedMoves moves = fixture.plannedMoves(sheet, DugoutFixture.acceptEverything());

        dugout.open(sheet, moves);

        String screen = output();
        assertThat(screen).contains("In campo");
        assertThat(rowOf(screen, tiredDefender)).contains("in difesa (difensore)").contains("stanco")
                .containsPattern("PAS +\\d+ DRI +\\d+ TIR +\\d+ CON +\\d+ POS +\\d+ VEL +\\d+ AER +\\d+ POR +\\d+ RES +\\d+");
        assertThat(rowOf(screen, exhaustedForward)).contains("in attacco (attaccante)").contains("sfinito")
                .doesNotContain("ammonito");
        assertThat(rowOf(screen, cautionedMidfielder)).contains("a centrocampo (centrocampista)").contains("fresco")
                .endsWith("ammonito");
    }

    @Test
    void thePlayersOnTheBenchAreListedWithTheirRoleAndTheOnesWhoLeftWithTheirReason() {
        Player leaving = fixture.starter(Role.MIDFIELDER, 1);
        Player entering = fixture.benchPlayer(Role.MIDFIELDER, 0);
        Player waitingOnTheBench = fixture.benchPlayer(Role.FORWARD, 0);
        TeamSheet sheet = fixture.sheetAfter(fixture.kickOff(), fixture.substitution(leaving, entering, 50));
        PlannedMoves moves = fixture.plannedMoves(sheet, DugoutFixture.acceptEverything());

        dugout.open(sheet, moves);

        String screen = output();
        assertThat(screen).contains("In panchina").contains("Non più disponibili");
        assertThat(rowOf(screen, waitingOnTheBench)).contains("attaccante");
        assertThat(rowOf(screen, leaving)).endsWith("sostituito");
        assertThat(rowOf(screen, entering)).contains("a centrocampo (centrocampista)").contains("fresco");
        assertThat(screen).contains("cambi 1 di");
    }

    @Test
    void aPlayerOutOfPositionIsMarkedAsSuch() {
        Player defenderPlayingUpFront = fixture.starter(Role.DEFENDER, 0);
        TeamSheet sheet = fixture.sheetAfter(fixture.kickOff(),
                fixture.moved(defenderPlayingUpFront, Role.FORWARD, 40));
        PlannedMoves moves = fixture.plannedMoves(sheet, DugoutFixture.acceptEverything());

        dugout.open(sheet, moves);

        assertThat(rowOf(output(), defenderPlayingUpFront)).contains("in attacco (difensore, fuori ruolo)");
    }

    @Test
    void aSubstitutionIsAcceptedAndDescribedWithTheNamesOfThePlayers() {
        TeamSheet sheet = fixture.sheetAtKickOff();
        PlannedMoves moves = fixture.plannedMoves(sheet, DugoutFixture.acceptEverything());
        Player entering = fixture.benchPlayer(Role.FORWARD, 0);
        Player leaving = fixture.starter(Role.DEFENDER, 0);
        input.willAnswer("cambio " + fixture.numberOf(sheet, leaving) + " " + fixture.numberOf(sheet, entering));

        dugout.open(sheet, moves);

        assertThat(moves.accepted()).hasSize(1);
        assertThat(output()).contains("Accettato: cambio, fuori " + leaving.name() + " e dentro " + entering.name());
    }

    @Test
    void openingDiscardsTheQueuedLinesBeforeReadingAnything() {
        TeamSheet sheet = fixture.sheetAtKickOff();
        PlannedMoves moves = fixture.plannedMoves(sheet, DugoutFixture.acceptEverything());

        dugout.open(sheet, moves);

        assertThat(input.discards()).isEqualTo(1);
        assertThat(input.discardedBeforeTheFirstRead()).isTrue();
    }

    @Test
    void resumeClosesTheSheetAndReturnsTheAcceptedMovesInOrder() {
        TeamSheet sheet = fixture.sheetAtKickOff();
        PlannedMoves moves = fixture.plannedMoves(sheet, DugoutFixture.acceptEverything());
        Player leaving = fixture.starter(Role.DEFENDER, 0);
        Player entering = fixture.benchPlayer(Role.DEFENDER, 0);
        input.willAnswer("stile offensivo", "cambio " + fixture.numberOf(sheet, leaving) + " "
                + fixture.numberOf(sheet, entering), "riprendi", "stile difensivo");

        List<Intervention> returned = dugout.open(sheet, moves);

        assertThat(returned).hasSize(2);
        assertThat(returned.get(0)).isInstanceOf(Intervention.StyleChange.class);
        assertThat(((Intervention.StyleChange) returned.get(0)).style()).isEqualTo(PlayStyle.ATTACKING);
        assertThat(returned.get(1)).isInstanceOf(Intervention.VoluntarySubstitution.class);
        assertThat(input.remainingLines()).containsExactly("stile difensivo");
        assertThat(output()).contains("Accettato: gioco offensivo").contains("Accettato: cambio, fuori");
    }

    @Test
    void theEndOfTheInputActsAsResumeKeepingTheAcceptedMoves() {
        TeamSheet sheet = fixture.sheetAtKickOff();
        PlannedMoves moves = fixture.plannedMoves(sheet, DugoutFixture.acceptEverything());
        input.willAnswer("stile contropiede");

        List<Intervention> returned = dugout.open(sheet, moves);

        assertThat(returned).hasSize(1);
        assertThat(((Intervention.StyleChange) returned.get(0)).style()).isEqualTo(PlayStyle.COUNTER_ATTACK);
    }

    @Test
    void theEndOfTheInputOnAnEmptySheetReturnsNoMoves() {
        TeamSheet sheet = fixture.sheetAtKickOff();
        PlannedMoves moves = fixture.plannedMoves(sheet, DugoutFixture.acceptEverything());

        List<Intervention> returned = dugout.open(sheet, moves);

        assertThat(returned).isEmpty();
    }

    @Test
    void aSubstitutionRefusedByTheEngineIsShownAtOnceWithItsReasonAndIsNotProgrammed() {
        TeamSheet sheet = fixture.sheetAtKickOff();
        Player goalkeeper = fixture.starter(Role.GOALKEEPER, 0);
        Player midfielder = fixture.benchPlayer(Role.MIDFIELDER, 0);
        PlannedMoves moves = fixture.plannedMoves(sheet, DugoutFixture.refusing(
                move -> move instanceof Intervention.VoluntarySubstitution substitution
                        && substitution.leaving().equals(goalkeeper),
                RefusalReason.GOALKEEPER_NOT_COVERED));
        input.willAnswer("cambio " + fixture.numberOf(sheet, goalkeeper) + " " + fixture.numberOf(sheet, midfielder),
                "riprendi");

        List<Intervention> returned = dugout.open(sheet, moves);

        assertThat(returned).isEmpty();
        assertThat(output()).contains("Rifiutato: in porta non ci sarebbe esattamente un portiere (non programmato)");
    }

    @Test
    void aStyleRefusedByTheEngineIsRefusedWithItsText() {
        TeamSheet sheet = fixture.sheetAtKickOff();
        PlannedMoves moves = fixture.plannedMoves(sheet, DugoutFixture.refusing(
                move -> move instanceof Intervention.StyleChange, RefusalReason.STYLE_ALREADY_IN_USE));
        input.willAnswer("stile possesso", "riprendi");

        List<Intervention> returned = dugout.open(sheet, moves);

        assertThat(returned).isEmpty();
        assertThat(output()).contains("Rifiutato: la squadra gioca già con quello stile (non programmato)");
    }

    @Test
    void anUnknownCommandIsReportedAndProducesNoMove() {
        assertReadingProblem("vai", "comando sconosciuto \"vai\": scrivi aiuto per l'elenco dei comandi");
    }

    @Test
    void aCommandWithTheWrongNumberOfArgumentsIsReported() {
        assertReadingProblem("cambio 3", "gli argomenti del comando cambio non sono quelli attesi");
        assertReadingProblem("riprendi adesso", "gli argomenti del comando riprendi non sono quelli attesi");
    }

    @Test
    void aSubstitutionNumberThatIsNotANumberIsReported() {
        assertReadingProblem("cambio uno 12", "\"uno\" non è un numero");
    }

    @Test
    void aSubstitutionNumberOutsideTheRosterIsReported() {
        TeamSheet sheet = fixture.sheetAtKickOff();
        int rosterSize = sheet.players().size();

        assertReadingProblem("cambio 0 12", "il numero 0 non appartiene alla rosa (da 1 a " + rosterSize + ")");
        assertReadingProblem("cambio 3 99", "il numero 99 non appartiene alla rosa (da 1 a " + rosterSize + ")");
    }

    @Test
    void anUnknownStyleIsReportedWithTheAllowedOnes() {
        assertReadingProblem("stile folle",
                "stile sconosciuto \"folle\", stili ammessi: offensivo, difensivo, equilibrato, possesso, contropiede");
    }

    @Test
    void aMalformedFormationIsReported() {
        assertReadingProblem("modulo quattro", "\"quattro\" non è un modulo nella forma 4-4-2");
    }

    @Test
    void aFormationOutsideTheAvailableOnesIsReportedWithTheAvailableOnes() {
        assertReadingProblem("modulo 6-2-2",
                "il modulo 6-2-2 non è fra quelli disponibili: 5-3-2, 4-5-1, 4-4-2, 3-5-2, 4-3-3");
    }

    @Test
    void theFormationAlreadyInPlaceIsReported() {
        TeamSheet sheet = fixture.sheetAtKickOff();
        String current = sheet.deployment().label();

        assertReadingProblem("modulo " + current, "il modulo " + current + " è già quello in campo");
    }

    @Test
    void whenTheOnlyAvailableFormationIsTheCurrentOneTheSheetSaysThereIsNoOtherOne() {
        TeamSheet sheet = fixture.sheetAtKickOff();
        Deployment current = sheet.deployment();
        FormationCatalog onlyTheCurrent = new FormationCatalog(List.of(new Formation(current.defenders(),
                current.midfielders(), current.forwards(), 0)));
        PlannedMoves moves = fixture.plannedMoves(sheet, onlyTheCurrent, DugoutFixture.acceptEverything());
        input.willAnswer("modulo " + current.label(), "riprendi");

        List<Intervention> returned = dugout.open(sheet, moves);

        assertThat(returned).isEmpty();
        assertThat(output()).contains("non c'è un modulo diverso da quello in campo fra i disponibili");
    }

    @Test
    void aFormationIsProposedAndEntersTheMovesOnlyAfterTheConfirmation() {
        TeamSheet sheet = fixture.sheetAtKickOff();
        PlannedMoves moves = fixture.plannedMoves(sheet, DugoutFixture.acceptEverything());
        Deployment target = anotherFormation(moves, sheet);
        input.willAnswer("modulo " + target.label(), "s", "riprendi");

        List<Intervention> returned = dugout.open(sheet, moves);

        assertThat(returned).hasSize(1);
        assertThat(returned.get(0)).isInstanceOf(Intervention.FormationChange.class);
        String screen = output();
        assertThat(screen).contains("Proposta per il modulo " + target.label() + ": ").contains("Confermi? (s/n)");
        assertThat(screen).contains("Accettato: spostamenti: ").contains("(modulo " + target.label() + ")");
    }

    @Test
    void aFormationProposalAnsweredNoIsDroppedAndAnUnclearAnswerIsAskedAgain() {
        TeamSheet sheet = fixture.sheetAtKickOff();
        PlannedMoves moves = fixture.plannedMoves(sheet, DugoutFixture.acceptEverything());
        Deployment target = anotherFormation(moves, sheet);
        input.willAnswer("modulo " + target.label(), "forse", "n", "riprendi");

        List<Intervention> returned = dugout.open(sheet, moves);

        assertThat(returned).isEmpty();
        assertThat(output()).contains("Rispondi s per confermare oppure n per rinunciare.")
                .contains("Proposta annullata.");
    }

    @Test
    void aFormationProposalPendingWhenTheInputEndsDoesNotEnterTheMoves() {
        TeamSheet sheet = fixture.sheetAtKickOff();
        PlannedMoves moves = fixture.plannedMoves(sheet, DugoutFixture.acceptEverything());
        Deployment target = anotherFormation(moves, sheet);
        Player leaving = fixture.starter(Role.DEFENDER, 0);
        Player entering = fixture.benchPlayer(Role.DEFENDER, 0);
        input.willAnswer("cambio " + fixture.numberOf(sheet, leaving) + " " + fixture.numberOf(sheet, entering),
                "modulo " + target.label());

        List<Intervention> returned = dugout.open(sheet, moves);

        assertThat(returned).hasSize(1);
        assertThat(returned.get(0)).isInstanceOf(Intervention.VoluntarySubstitution.class);
        assertThat(output()).contains("Confermi? (s/n)");
    }

    @Test
    void undoRemovesAllTheAcceptedMoves() {
        TeamSheet sheet = fixture.sheetAtKickOff();
        PlannedMoves moves = fixture.plannedMoves(sheet, DugoutFixture.acceptEverything());
        input.willAnswer("stile offensivo", "annulla", "riprendi");

        List<Intervention> returned = dugout.open(sheet, moves);

        assertThat(returned).isEmpty();
        assertThat(output()).contains("Mosse annullate.");
    }

    @Test
    void helpListsTheCommandsAndABlankLineShowsTheSheetAgain() {
        TeamSheet sheet = fixture.sheetAtKickOff();
        PlannedMoves moves = fixture.plannedMoves(sheet, DugoutFixture.acceptEverything());
        input.willAnswer("aiuto", "", "riprendi");

        dugout.open(sheet, moves);

        String screen = output();
        assertThat(screen).contains("Comandi:").contains("cambio <esce> <entra>").contains("modulo <D-C-A>")
                .contains("stile <stile>").contains("annulla").contains("riprendi");
        assertThat(occurrencesOf(screen, "Panchina di Atletico Mare")).isEqualTo(2);
    }

    @Test
    void theAcceptedMovesAreListedInOrderWithTheResultingLineup() {
        TeamSheet sheet = fixture.sheetAtKickOff();
        PlannedMoves moves = fixture.plannedMoves(sheet, DugoutFixture.acceptEverything());
        input.willAnswer("stile offensivo", "", "riprendi");

        dugout.open(sheet, moves);

        assertThat(output()).contains("Mosse accettate:").contains("1. gioco offensivo")
                .contains("Dopo le mosse: gioco offensivo, modulo " + sheet.deployment().label());
    }

    @Test
    void thePromptIsPrintedBeforeEveryLineRead() {
        TeamSheet sheet = fixture.sheetAtKickOff();
        PlannedMoves moves = fixture.plannedMoves(sheet, DugoutFixture.acceptEverything());
        input.willAnswer("aiuto", "riprendi");

        dugout.open(sheet, moves);

        assertThat(occurrencesOf(output(), System.lineSeparator() + "> ")).isEqualTo(2);
    }

    @Test
    void theOutcomesShowEveryMoveExecutedRefusedOrPending() {
        Player leaving = fixture.starter(Role.DEFENDER, 0);
        Player entering = fixture.benchPlayer(Role.DEFENDER, 0);
        Intervention substitution = new Intervention.VoluntarySubstitution(MatchSide.HOME, leaving, entering,
                ChangeReason.MANAGER_DECISION);
        Intervention style = new Intervention.StyleChange(MatchSide.HOME, PlayStyle.ATTACKING,
                ChangeReason.MANAGER_DECISION);
        Intervention styleAgain = new Intervention.StyleChange(MatchSide.HOME, PlayStyle.DEFENSIVE,
                ChangeReason.MANAGER_DECISION);

        dugout.showOutcomes(List.of(substitution, style, styleAgain), List.of(InterventionResult.executed(),
                InterventionResult.refused(RefusalReason.MATCH_NOT_IN_PLAY), InterventionResult.pending()));

        assertThat(output()).contains("Eseguito: cambio, fuori " + leaving.name() + " e dentro " + entering.name())
                .contains("Rifiutato: gioco offensivo (la partita non è in corso)")
                .contains("In attesa: gioco difensivo");
    }

    @Test
    void preparingForThePauseRunsTheChronicleAlignmentAndThePauseRequestComesFromTheInput() {
        input.requestPause();

        dugout.prepareForPause();

        assertThat(alignments).containsExactly("aligned");
        assertThat(dugout.isPauseRequested()).isTrue();
    }

    private void assertReadingProblem(String line, String expectedMessage) {
        TeamSheet sheet = fixture.sheetAtKickOff();
        PlannedMoves moves = fixture.plannedMoves(sheet, DugoutFixture.acceptEverything());
        ScriptedInput lineInput = new ScriptedInput();
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        ConsoleDugout readingDugout = ConsoleDugout.standard(lineInput, printStream(capturedOutput), messages,
                DugoutFixture.HOME_COACH, () -> { });
        lineInput.willAnswer(line, "riprendi");

        List<Intervention> returned = readingDugout.open(sheet, moves);

        assertThat(returned).as(line).isEmpty();
        assertThat(capturedOutput.toString(StandardCharsets.UTF_8)).as(line).contains(expectedMessage);
    }

    private Deployment anotherFormation(PlannedMoves moves, TeamSheet sheet) {
        Deployment current = sheet.deployment();
        return moves.availableFormations().stream()
                .filter(formation -> !formation.equals(current))
                .findFirst()
                .orElseThrow();
    }

    private String rowOf(String screen, Player player) {
        return screen.lines()
                .filter(line -> line.contains(player.name()))
                .findFirst()
                .orElseThrow();
    }

    private int occurrencesOf(String text, String fragment) {
        return text.split(Pattern.quote(fragment), -1).length - 1;
    }

    private String output() {
        return captured.toString(StandardCharsets.UTF_8);
    }

    private PrintStream printStream() {
        return printStream(captured);
    }

    private PrintStream printStream(ByteArrayOutputStream stream) {
        return new PrintStream(stream, true, StandardCharsets.UTF_8);
    }

    private static final class ScriptedInput implements DugoutInput {

        private final Deque<String> lines = new ArrayDeque<>();
        private boolean pauseRequested;
        private int discards;
        private boolean readBeforeTheDiscard;

        void willAnswer(String... answers) {
            lines.addAll(List.of(answers));
        }

        void requestPause() {
            pauseRequested = true;
        }

        int discards() {
            return discards;
        }

        boolean discardedBeforeTheFirstRead() {
            return discards > 0 && !readBeforeTheDiscard;
        }

        List<String> remainingLines() {
            return List.copyOf(lines);
        }

        @Override
        public boolean isPauseRequested() {
            return pauseRequested;
        }

        @Override
        public void discardQueuedLines() {
            discards++;
        }

        @Override
        public Optional<String> awaitLine() {
            if (discards == 0) {
                readBeforeTheDiscard = true;
            }
            return Optional.ofNullable(lines.pollFirst());
        }
    }
}
