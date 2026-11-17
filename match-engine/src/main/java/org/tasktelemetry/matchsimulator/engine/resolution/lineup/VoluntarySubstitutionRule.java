package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.config.LineupConfig;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention.VoluntarySubstitution;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class VoluntarySubstitutionRule {

    private final LineupConfig config;

    public VoluntarySubstitutionRule(LineupConfig config) {
        this.config = config;
    }

    public Optional<RefusalReason> refusalOf(TeamOnPitch team, VoluntarySubstitution request) {
        if (!team.players().contains(request.leaving())) {
            return Optional.of(RefusalReason.PLAYER_NOT_ON_PITCH);
        }
        if (!team.availableBench().contains(request.entering())) {
            return Optional.of(RefusalReason.SUBSTITUTE_NOT_AVAILABLE);
        }
        if (team.substitutionsMade() >= config.maximumSubstitutions()) {
            return Optional.of(RefusalReason.NO_SUBSTITUTIONS_LEFT);
        }
        TeamOnPitch changed = substitute(team, request);
        if (changed.playersIn(Role.GOALKEEPER).size() != 1) {
            return Optional.of(RefusalReason.GOALKEEPER_NOT_COVERED);
        }
        Role sectorOfWhoLeaves = team.roleOf(request.leaving());
        if (changed.playersIn(sectorOfWhoLeaves).isEmpty()) {
            return Optional.of(RefusalReason.SECTOR_WOULD_BE_EMPTIED);
        }
        return Optional.empty();
    }

    public TeamOnPitch substitute(TeamOnPitch team, VoluntarySubstitution request) {
        Player entering = request.entering();
        return team.withSubstitution(request.leaving(), entering, entering.role());
    }
}
