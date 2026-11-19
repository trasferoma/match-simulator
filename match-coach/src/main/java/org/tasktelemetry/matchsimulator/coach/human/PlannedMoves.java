package org.tasktelemetry.matchsimulator.coach.human;

import java.util.ArrayList;
import java.util.List;

import org.tasktelemetry.matchsimulator.coach.MatchMoment.InterventionPreview;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention.FormationChange;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention.SectorShift;
import org.tasktelemetry.matchsimulator.engine.intervention.PreviewVerdict;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class PlannedMoves {

    private final TeamSheet sheet;
    private final InterventionPreview preview;
    private final AvailableFormations formations;
    private final FormationShiftPlanner planner;
    private final List<Intervention> accepted = new ArrayList<>();

    public PlannedMoves(TeamSheet sheet, InterventionPreview preview, AvailableFormations formations,
            FormationShiftPlanner planner) {
        this.sheet = sheet;
        this.preview = preview;
        this.formations = formations;
        this.planner = planner;
    }

    public List<Intervention> accepted() {
        return List.copyOf(accepted);
    }

    public PreviewVerdict submit(Intervention move) {
        List<Intervention> sequence = new ArrayList<>(accepted);
        sequence.add(move);
        List<PreviewVerdict> verdicts = preview.preview(sequence);
        PreviewVerdict verdictOfTheNewMove = verdicts.get(verdicts.size() - 1);
        if (verdictOfTheNewMove.isAccepted()) {
            accepted.add(move);
        }
        return verdictOfTheNewMove;
    }

    public void clear() {
        accepted.clear();
    }

    public Intervention substitution(Player leaving, Player entering) {
        return new Intervention.VoluntarySubstitution(sheet.side(), leaving, entering, ChangeReason.MANAGER_DECISION);
    }

    public Intervention styleChange(PlayStyle style) {
        return new Intervention.StyleChange(sheet.side(), style, ChangeReason.MANAGER_DECISION);
    }

    public FormationChange formationChange(Deployment target) {
        List<Deployment> available = availableFormations();
        if (!available.contains(target)) {
            throw new IllegalArgumentException("formation " + target.label() + " is not among the available ones");
        }
        ProjectedLineup projected = projectedLineup();
        if (target.equals(projected.deployment())) {
            throw new IllegalArgumentException("formation " + target.label() + " is the one already in place");
        }
        List<SectorShift> shifts = planner.shiftsFor(projected, target);
        return new FormationChange(sheet.side(), shifts, ChangeReason.MANAGER_DECISION);
    }

    public ProjectedLineup projectedLineup() {
        ProjectedLineup projected = sheet.lineup();
        for (Intervention move : accepted) {
            projected = projected.after(move);
        }
        return projected;
    }

    public Deployment resultingDeployment() {
        ProjectedLineup projected = projectedLineup();
        return projected.deployment();
    }

    public PlayStyle resultingStyle() {
        ProjectedLineup projected = projectedLineup();
        return projected.style();
    }

    public List<Deployment> availableFormations() {
        ProjectedLineup projected = projectedLineup();
        return formations.forOutfieldPlayers(projected.outfieldPlayers());
    }
}
