package org.tasktelemetry.matchsimulator.cli.dugout;

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.coach.human.DugoutSession;
import org.tasktelemetry.matchsimulator.coach.human.PlannedMoves;
import org.tasktelemetry.matchsimulator.coach.human.SheetPlayer;
import org.tasktelemetry.matchsimulator.coach.human.TeamSheet;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.intervention.PreviewVerdict;
import org.tasktelemetry.matchsimulator.message.UserMessages;

public final class ConsoleDugout implements DugoutSession {

    private final DugoutInput input;
    private final PrintStream out;
    private final DugoutCommandParser parser;
    private final DugoutScreen screen;
    private final MoveWording wording;
    private final Runnable chronicleAlignment;

    private ConsoleDugout(DugoutInput input, PrintStream out, DugoutCommandParser parser, DugoutScreen screen,
            MoveWording wording, Runnable chronicleAlignment) {
        this.input = input;
        this.out = out;
        this.parser = parser;
        this.screen = screen;
        this.wording = wording;
        this.chronicleAlignment = chronicleAlignment;
    }

    public static ConsoleDugout standard(DugoutInput input, PrintStream out, UserMessages messages,
            String coachName, Runnable chronicleAlignment) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(out, "output must not be null");
        Objects.requireNonNull(messages, "messages must not be null");
        Objects.requireNonNull(coachName, "coach name must not be null");
        Objects.requireNonNull(chronicleAlignment, "chronicle alignment must not be null");
        DugoutCommandParser parser = new DugoutCommandParser();
        MoveWording wording = new MoveWording(messages);
        DugoutScreen screen = new DugoutScreen(messages, wording, coachName);
        return new ConsoleDugout(input, out, parser, screen, wording, chronicleAlignment);
    }

    @Override
    public boolean isPauseRequested() {
        return input.isPauseRequested();
    }

    @Override
    public void prepareForPause() {
        chronicleAlignment.run();
    }

    @Override
    public List<Intervention> open(TeamSheet sheet, PlannedMoves plannedMoves) {
        discardLinesWrittenBeforeTheOpening();
        showSheet(sheet, plannedMoves);
        runCommandsUntilResumeOrEndOfInput(sheet, plannedMoves);
        return plannedMoves.accepted();
    }

    @Override
    public void showOutcomes(List<Intervention> moves, List<InterventionResult> results) {
        for (int index = 0; index < moves.size(); index++) {
            String line = wording.outcomeLine(moves.get(index), results.get(index));
            out.println(line);
        }
    }

    private void discardLinesWrittenBeforeTheOpening() {
        input.discardQueuedLines();
    }

    private void runCommandsUntilResumeOrEndOfInput(TeamSheet sheet, PlannedMoves moves) {
        Optional<DugoutCommand> command = readCommand();
        while (command.isPresent() && !isResume(command.get())) {
            runCommand(command.get(), sheet, moves);
            command = readCommand();
        }
    }

    private boolean isResume(DugoutCommand command) {
        return command instanceof DugoutCommand.Resume;
    }

    private Optional<DugoutCommand> readCommand() {
        Optional<String> line = askForALine();
        while (line.isPresent()) {
            Optional<DugoutCommand> command = parseOrReportProblem(line.get());
            if (command.isPresent()) {
                return command;
            }
            line = askForALine();
        }
        return Optional.empty();
    }

    private Optional<String> askForALine() {
        String prompt = screen.promptLine();
        out.print(prompt);
        out.flush();
        Optional<String> line = input.awaitLine();
        if (line.isEmpty()) {
            out.println();
        }
        return line;
    }

    private Optional<DugoutCommand> parseOrReportProblem(String line) {
        try {
            DugoutCommand command = parser.parse(line);
            return Optional.of(command);
        } catch (InvalidDugoutCommandException problem) {
            reportProblem(problem);
            return Optional.empty();
        }
    }

    private void runCommand(DugoutCommand command, TeamSheet sheet, PlannedMoves moves) {
        try {
            execute(command, sheet, moves);
        } catch (InvalidDugoutCommandException problem) {
            reportProblem(problem);
        }
    }

    private void execute(DugoutCommand command, TeamSheet sheet, PlannedMoves moves) {
        switch (command) {
            case DugoutCommand.Substitute substitute -> programSubstitution(substitute, sheet, moves);
            case DugoutCommand.ChangeFormation change -> programFormation(change, moves);
            case DugoutCommand.ChangeStyle change -> programStyle(change, moves);
            case DugoutCommand.Undo undo -> undoMoves(moves);
            case DugoutCommand.Help help -> showHelp();
            case DugoutCommand.Redraw redraw -> showSheet(sheet, moves);
            case DugoutCommand.Resume resume -> {
            }
        }
    }

    private void programSubstitution(DugoutCommand.Substitute command, TeamSheet sheet, PlannedMoves moves) {
        SheetPlayer leaving = findInRoster(sheet, command.leavingNumber());
        SheetPlayer entering = findInRoster(sheet, command.enteringNumber());
        Intervention substitution = moves.substitution(leaving.player(), entering.player());
        submit(substitution, moves);
    }

    private void programStyle(DugoutCommand.ChangeStyle command, PlannedMoves moves) {
        Intervention styleChange = moves.styleChange(command.style());
        submit(styleChange, moves);
    }

    private void programFormation(DugoutCommand.ChangeFormation command, PlannedMoves moves) {
        Deployment target = command.target();
        requireAnotherAvailableFormation(target, moves);
        Intervention.FormationChange proposal = moves.formationChange(target);
        Optional<DugoutAnswer> answer = askToConfirm(target, proposal);
        if (answer.isEmpty()) {
            return;
        }
        settleProposal(answer.get(), proposal, moves);
    }

    private void settleProposal(DugoutAnswer answer, Intervention.FormationChange proposal, PlannedMoves moves) {
        if (answer == DugoutAnswer.YES) {
            submit(proposal, moves);
            return;
        }
        String declined = screen.declinedLine();
        out.println(declined);
    }

    private Optional<DugoutAnswer> askToConfirm(Deployment target, Intervention.FormationChange proposal) {
        String proposalLine = wording.proposalLine(target, proposal.shifts());
        out.println(proposalLine);
        Optional<String> line = askForALine();
        while (line.isPresent()) {
            Optional<DugoutAnswer> answer = DugoutAnswer.of(line.get());
            if (answer.isPresent()) {
                return answer;
            }
            String confirmAgain = screen.confirmAgainLine();
            out.println(confirmAgain);
            line = askForALine();
        }
        return Optional.empty();
    }

    private void submit(Intervention move, PlannedMoves moves) {
        PreviewVerdict verdict = moves.submit(move);
        String line = switch (verdict) {
            case PreviewVerdict.Accepted accepted -> describeAccepted(move, moves);
            case PreviewVerdict.Refused refused -> wording.refusedLine(refused.reason());
        };
        out.println(line);
    }

    private String describeAccepted(Intervention move, PlannedMoves moves) {
        Deployment resultingDeployment = moves.resultingDeployment();
        return wording.acceptedLine(move, resultingDeployment);
    }

    private void undoMoves(PlannedMoves moves) {
        moves.clear();
        String undone = screen.undoneLine();
        out.println(undone);
    }

    private void showHelp() {
        List<String> lines = screen.helpLines();
        lines.forEach(out::println);
    }

    private void showSheet(TeamSheet sheet, PlannedMoves moves) {
        List<String> lines = screen.sheetLines(sheet, moves);
        lines.forEach(out::println);
    }

    private void reportProblem(InvalidDugoutCommandException problem) {
        String line = screen.problemLine(problem);
        out.println(line);
    }

    private SheetPlayer findInRoster(TeamSheet sheet, int number) {
        return sheet.playerNumbered(number)
                .orElseThrow(() -> numberNotInRoster(sheet, number));
    }

    private InvalidDugoutCommandException numberNotInRoster(TeamSheet sheet, int number) {
        List<SheetPlayer> roster = sheet.players();
        return new InvalidDugoutCommandException(DugoutProblem.NUMBER_NOT_IN_ROSTER, number, roster.size());
    }

    private void requireAnotherAvailableFormation(Deployment target, PlannedMoves moves) {
        Deployment resulting = moves.resultingDeployment();
        List<Deployment> available = moves.availableFormations();
        boolean anotherIsAvailable = available.stream().anyMatch(formation -> !formation.equals(resulting));
        if (!anotherIsAvailable) {
            throw new InvalidDugoutCommandException(DugoutProblem.NO_OTHER_FORMATION_AVAILABLE);
        }
        if (!available.contains(target)) {
            throw formationNotAvailable(target, available);
        }
        if (target.equals(resulting)) {
            throw formationAlreadyInPlace(target);
        }
    }

    private InvalidDugoutCommandException formationAlreadyInPlace(Deployment target) {
        String targetLabel = target.label();
        return new InvalidDugoutCommandException(DugoutProblem.FORMATION_ALREADY_IN_PLACE, targetLabel);
    }

    private InvalidDugoutCommandException formationNotAvailable(Deployment target, List<Deployment> available) {
        String targetLabel = target.label();
        String availableLabels = screen.formationLabels(available);
        return new InvalidDugoutCommandException(DugoutProblem.FORMATION_NOT_AVAILABLE, targetLabel,
                availableLabels);
    }
}
