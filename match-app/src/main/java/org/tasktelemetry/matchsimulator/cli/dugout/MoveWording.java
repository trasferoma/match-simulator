package org.tasktelemetry.matchsimulator.cli.dugout;

import java.util.List;
import java.util.stream.Collectors;

import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention.SectorShift;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.message.UserMessages;

final class MoveWording {

    private static final String SHIFT_SEPARATOR = ", ";

    private final UserMessages messages;

    MoveWording(UserMessages messages) {
        this.messages = messages;
    }

    String describe(Intervention move) {
        return switch (move) {
            case Intervention.StyleChange change -> describeStyle(change);
            case Intervention.VoluntarySubstitution substitution -> describeSubstitution(substitution);
            case Intervention.SectorMove sectorMove -> describeSectorMove(sectorMove);
            case Intervention.FormationChange change -> describeShiftsOfFormationChange(change);
        };
    }

    String acceptedLine(Intervention move, Deployment resultingDeployment) {
        String description = describe(move);
        return messages.text(DugoutLabel.ACCEPTED, description, resultingDeployment.label());
    }

    String refusedLine(RefusalReason reason) {
        String reasonText = messages.text(reason);
        return messages.text(DugoutLabel.REFUSED, reasonText);
    }

    String proposalLine(Deployment target, List<SectorShift> shifts) {
        String shiftsText = describeShifts(shifts);
        String yes = DugoutAnswer.YES.word();
        String no = DugoutAnswer.NO.word();
        return messages.text(DugoutLabel.PROPOSAL, target.label(), shiftsText, yes, no);
    }

    String outcomeLine(Intervention move, InterventionResult result) {
        String description = describe(move);
        return switch (result) {
            case InterventionResult.Executed executed -> messages.text(DugoutLabel.OUTCOME_EXECUTED, description);
            case InterventionResult.Pending pending -> messages.text(DugoutLabel.OUTCOME_PENDING, description);
            case InterventionResult.Refused refused -> describeRefusedOutcome(description, refused);
        };
    }

    private String describeRefusedOutcome(String description, InterventionResult.Refused refused) {
        String reasonText = messages.text(refused.reason());
        return messages.text(DugoutLabel.OUTCOME_REFUSED, description, reasonText);
    }

    private String describeStyle(Intervention.StyleChange change) {
        String styleText = messages.text(change.style());
        return messages.text(DugoutLabel.MOVE_STYLE, styleText);
    }

    private String describeSubstitution(Intervention.VoluntarySubstitution substitution) {
        String leaving = substitution.leaving().name();
        String entering = substitution.entering().name();
        return messages.text(DugoutLabel.MOVE_SUBSTITUTION, leaving, entering);
    }

    private String describeSectorMove(Intervention.SectorMove sectorMove) {
        SectorShift shift = new SectorShift(sectorMove.player(), sectorMove.sector());
        List<SectorShift> shifts = List.of(shift);
        String shiftsText = describeShifts(shifts);
        return messages.text(DugoutLabel.MOVE_SHIFTS, shiftsText);
    }

    private String describeShiftsOfFormationChange(Intervention.FormationChange change) {
        String shiftsText = describeShifts(change.shifts());
        return messages.text(DugoutLabel.MOVE_SHIFTS, shiftsText);
    }

    private String describeShifts(List<SectorShift> shifts) {
        return shifts.stream()
                .map(this::describeShift)
                .collect(Collectors.joining(SHIFT_SEPARATOR));
    }

    private String describeShift(SectorShift shift) {
        String sectorText = messages.text(shift.sector());
        return messages.text(DugoutLabel.SHIFT, shift.player().name(), sectorText);
    }
}
