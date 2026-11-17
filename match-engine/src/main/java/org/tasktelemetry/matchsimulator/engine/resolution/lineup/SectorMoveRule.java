package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.intervention.Intervention.SectorMove;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class SectorMoveRule {

    public Optional<RefusalReason> refusalOf(TeamOnPitch team, SectorMove request) {
        Player player = request.player();
        Role currentSector = team.roleOf(player);
        if (!team.players().contains(player)) {
            return Optional.of(RefusalReason.PLAYER_NOT_ON_PITCH);
        }
        if (currentSector == Role.GOALKEEPER || request.sector() == Role.GOALKEEPER) {
            return Optional.of(RefusalReason.GOALKEEPER_CANNOT_MOVE);
        }
        if (currentSector == request.sector()) {
            return Optional.of(RefusalReason.ALREADY_IN_THAT_SECTOR);
        }
        if (isTheOnlyOneIn(team, currentSector)) {
            return Optional.of(RefusalReason.SECTOR_WOULD_BE_EMPTIED);
        }
        return Optional.empty();
    }

    public TeamOnPitch move(TeamOnPitch team, SectorMove request) {
        return team.withRole(request.player(), request.sector());
    }

    private boolean isTheOnlyOneIn(TeamOnPitch team, Role sector) {
        return team.playersIn(sector).size() == 1;
    }
}
