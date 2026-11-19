package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import java.util.List;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.intervention.Intervention.FormationChange;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention.SectorShift;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class FormationChangeRule {

    private static final List<Role> MOVEMENT_SECTORS = List.of(Role.DEFENDER, Role.MIDFIELDER, Role.FORWARD);

    public Optional<RefusalReason> refusalOf(TeamOnPitch team, FormationChange request) {
        Optional<RefusalReason> shiftRefusal = request.shifts().stream()
                .map(shift -> refusalOfShift(team, shift))
                .flatMap(Optional::stream)
                .findFirst();
        if (shiftRefusal.isPresent()) {
            return shiftRefusal;
        }
        TeamOnPitch changed = change(team, request);
        if (hasEmptyMovementSector(changed)) {
            return Optional.of(RefusalReason.SECTOR_WOULD_BE_EMPTIED);
        }
        return Optional.empty();
    }

    public TeamOnPitch change(TeamOnPitch team, FormationChange request) {
        TeamOnPitch changed = team;
        for (SectorShift shift : request.shifts()) {
            changed = changed.withRole(shift.player(), shift.sector());
        }
        return changed;
    }

    private Optional<RefusalReason> refusalOfShift(TeamOnPitch team, SectorShift shift) {
        Player player = shift.player();
        if (!team.players().contains(player)) {
            return Optional.of(RefusalReason.PLAYER_NOT_ON_PITCH);
        }
        Role currentSector = team.roleOf(player);
        if (currentSector == Role.GOALKEEPER || shift.sector() == Role.GOALKEEPER) {
            return Optional.of(RefusalReason.GOALKEEPER_CANNOT_MOVE);
        }
        if (currentSector == shift.sector()) {
            return Optional.of(RefusalReason.ALREADY_IN_THAT_SECTOR);
        }
        return Optional.empty();
    }

    private boolean hasEmptyMovementSector(TeamOnPitch team) {
        return MOVEMENT_SECTORS.stream().anyMatch(sector -> team.playersIn(sector).isEmpty());
    }
}
