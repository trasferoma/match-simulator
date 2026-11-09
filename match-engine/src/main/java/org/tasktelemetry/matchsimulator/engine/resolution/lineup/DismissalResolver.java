package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class DismissalResolver {

    private final MinimumPlayersRule minimumPlayers;
    private final LineupRepair lineupRepair;

    public DismissalResolver(MinimumPlayersRule minimumPlayers, LineupRepair lineupRepair) {
        this.minimumPlayers = minimumPlayers;
        this.lineupRepair = lineupRepair;
    }

    public LineupOutcome dismiss(TeamOnPitch team, Player dismissed, int lead) {
        Role dismissedRole = team.roleOf(dismissed);
        TeamOnPitch reduced = team.withoutPlayer(dismissed);
        if (minimumPlayers.isBelowMinimum(reduced)) {
            return LineupOutcome.suspended(reduced);
        }
        RepairedLineup repaired = lineupRepair.repairAfterDismissal(reduced, dismissedRole, lead);
        return LineupOutcome.continuing(repaired);
    }
}
