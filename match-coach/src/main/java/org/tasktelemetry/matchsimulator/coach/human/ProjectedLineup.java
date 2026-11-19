package org.tasktelemetry.matchsimulator.coach.human;

import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention.SectorShift;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public record ProjectedLineup(PlayStyle style, List<SheetPlayer> players) {

    public ProjectedLineup {
        Objects.requireNonNull(style, "style must not be null");
        Objects.requireNonNull(players, "players must not be null");
        players = List.copyOf(players);
    }

    public List<SheetPlayer> onPitch() {
        return players.stream()
                .filter(SheetPlayer::isOnPitch)
                .toList();
    }

    public List<SheetPlayer> playersIn(Role sector) {
        return onPitch().stream()
                .filter(player -> player.sector() == sector)
                .toList();
    }

    public Deployment deployment() {
        int defenders = playersIn(Role.DEFENDER).size();
        int midfielders = playersIn(Role.MIDFIELDER).size();
        int forwards = playersIn(Role.FORWARD).size();
        return new Deployment(defenders, midfielders, forwards);
    }

    public int outfieldPlayers() {
        Deployment deployment = deployment();
        return deployment.defenders() + deployment.midfielders() + deployment.forwards();
    }

    public ProjectedLineup after(Intervention move) {
        return switch (move) {
            case Intervention.StyleChange change -> new ProjectedLineup(change.style(), players);
            case Intervention.VoluntarySubstitution substitution -> afterSubstitution(substitution);
            case Intervention.SectorMove sectorMove -> afterSectorMove(sectorMove);
            case Intervention.FormationChange change -> afterShifts(change.shifts());
        };
    }

    private ProjectedLineup afterSubstitution(Intervention.VoluntarySubstitution substitution) {
        List<SheetPlayer> substituted = players.stream()
                .map(member -> afterSubstitution(member, substitution))
                .toList();
        return new ProjectedLineup(style, substituted);
    }

    private SheetPlayer afterSubstitution(SheetPlayer member, Intervention.VoluntarySubstitution substitution) {
        if (member.player().equals(substitution.leaving())) {
            return member.substitutedOut();
        }
        if (member.player().equals(substitution.entering())) {
            return member.enteringTheMatch();
        }
        return member;
    }

    private ProjectedLineup afterShifts(List<SectorShift> shifts) {
        List<SheetPlayer> shifted = players.stream()
                .map(member -> afterShifts(member, shifts))
                .toList();
        return new ProjectedLineup(style, shifted);
    }

    private SheetPlayer afterShifts(SheetPlayer member, List<SectorShift> shifts) {
        return shifts.stream()
                .filter(shift -> shift.player().equals(member.player()))
                .findFirst()
                .map(shift -> member.shiftedTo(shift.sector()))
                .orElse(member);
    }

    private ProjectedLineup afterSectorMove(Intervention.SectorMove sectorMove) {
        SectorShift shift = new SectorShift(sectorMove.player(), sectorMove.sector());
        List<SectorShift> shifts = List.of(shift);
        return afterShifts(shifts);
    }
}
