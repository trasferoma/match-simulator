package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import java.util.List;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.config.LineupConfig;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class LineupRepair {

    private static final List<Role> SECTORS = List.of(Role.DEFENDER, Role.MIDFIELDER, Role.FORWARD);
    private static final List<Role> SECTORS_FROM_THE_ATTACK = List.of(Role.FORWARD, Role.MIDFIELDER, Role.DEFENDER);

    private final LineupConfig config;

    public LineupRepair(LineupConfig config) {
        this.config = config;
    }

    public RepairedLineup repairAfterDismissal(TeamOnPitch team, Role dismissedRole, int lead) {
        if (dismissedRole != Role.GOALKEEPER) {
            return fillEmptiedSector(team);
        }
        RepairedLineup covered = coverTheGoal(team, lead);
        RepairedLineup refilled = fillEmptiedSector(covered.team());
        return covered.followedBy(refilled);
    }

    public RepairedLineup repairAfterInjury(TeamOnPitch team) {
        RepairedLineup withGoalkeeper = coverTheGoalIfUncovered(team);
        RepairedLineup refilled = fillEmptiedSector(withGoalkeeper.team());
        return withGoalkeeper.followedBy(refilled);
    }

    private RepairedLineup coverTheGoalIfUncovered(TeamOnPitch team) {
        if (team.playersIn(Role.GOALKEEPER).isEmpty()) {
            return moveBestGoalkeeperBetweenThePosts(team);
        }
        return RepairedLineup.withoutChanges(team);
    }

    private RepairedLineup coverTheGoal(TeamOnPitch team, int lead) {
        Optional<Player> reserve = reserveGoalkeeperWithSubstitutionsLeft(team);
        if (reserve.isEmpty()) {
            return moveBestGoalkeeperBetweenThePosts(team);
        }
        Player reserveGoalkeeper = reserve.get();
        return bringInReserveGoalkeeper(team, reserveGoalkeeper, lead);
    }

    private Optional<Player> reserveGoalkeeperWithSubstitutionsLeft(TeamOnPitch team) {
        if (team.substitutionsMade() >= config.maximumSubstitutions()) {
            return Optional.empty();
        }
        return team.availableBench().stream()
                .filter(player -> player.role() == Role.GOALKEEPER)
                .findFirst();
    }

    private RepairedLineup bringInReserveGoalkeeper(TeamOnPitch team, Player reserve, int lead) {
        Player leaving = chooseWhoLeavesForTheReserveGoalkeeper(team, lead);
        TeamOnPitch changed = team.withSubstitution(leaving, reserve, Role.GOALKEEPER);
        Substitution substitution = new Substitution(leaving, reserve, Role.GOALKEEPER,
                ChangeReason.RESERVE_GOALKEEPER);
        return RepairedLineup.substituting(changed, substitution);
    }

    private Player chooseWhoLeavesForTheReserveGoalkeeper(TeamOnPitch team, int lead) {
        Role sector = isBehind(lead) ? sectorToThinWhenBehind(team) : sectorToThinWhenNotBehind(team);
        return lowestAverageIn(team, sector);
    }

    private boolean isBehind(int lead) {
        return lead < 0;
    }

    private Role sectorToThinWhenBehind(TeamOnPitch team) {
        int defenders = team.playersIn(Role.DEFENDER).size();
        int midfielders = team.playersIn(Role.MIDFIELDER).size();
        boolean defenceAndMidfieldCannotRelease = defenders == 1 && midfielders == 1;
        if (defenceAndMidfieldCannotRelease) {
            return Role.FORWARD;
        }
        return defenders > midfielders ? Role.DEFENDER : Role.MIDFIELDER;
    }

    private Role sectorToThinWhenNotBehind(TeamOnPitch team) {
        Role mostNumerous = mostNumerousIn(team, SECTORS_FROM_THE_ATTACK);
        boolean canRelease = team.playersIn(mostNumerous).size() >= 2;
        if (!canRelease) {
            throw new IllegalStateException("no sector can release a player for the reserve goalkeeper");
        }
        return mostNumerous;
    }

    private RepairedLineup moveBestGoalkeeperBetweenThePosts(TeamOnPitch team) {
        Player best = null;
        for (Player candidate : team.players()) {
            if (best == null || goalkeepingOf(candidate) > goalkeepingOf(best)) {
                best = candidate;
            }
        }
        TeamOnPitch withGoalkeeper = team.withRole(best, Role.GOALKEEPER);
        PlayerMove move = new PlayerMove(best, Role.GOALKEEPER, ChangeReason.MAKESHIFT_GOALKEEPER);
        return RepairedLineup.moving(withGoalkeeper, move);
    }

    private RepairedLineup fillEmptiedSector(TeamOnPitch team) {
        for (Role sector : SECTORS) {
            if (team.playersIn(sector).isEmpty()) {
                return fillSector(team, sector);
            }
        }
        return RepairedLineup.withoutChanges(team);
    }

    private RepairedLineup fillSector(TeamOnPitch team, Role emptySector) {
        Role donor = donorFor(team, emptySector);
        Player mover = highestAverageIn(team, donor);
        TeamOnPitch refilled = team.withRole(mover, emptySector);
        PlayerMove move = new PlayerMove(mover, emptySector, ChangeReason.EMPTIED_SECTOR);
        return RepairedLineup.moving(refilled, move);
    }

    private Role donorFor(TeamOnPitch team, Role emptySector) {
        Role preferred = emptySector == Role.MIDFIELDER ? moreNumerousOfDefenceAndAttack(team) : Role.MIDFIELDER;
        if (team.playersIn(preferred).size() >= 2) {
            return preferred;
        }
        return mostNumerousIn(team, SECTORS);
    }

    private Role moreNumerousOfDefenceAndAttack(TeamOnPitch team) {
        int defenders = team.playersIn(Role.DEFENDER).size();
        int forwards = team.playersIn(Role.FORWARD).size();
        return forwards > defenders ? Role.FORWARD : Role.DEFENDER;
    }

    private Role mostNumerousIn(TeamOnPitch team, List<Role> sectorsInTieBreakOrder) {
        Role mostNumerous = sectorsInTieBreakOrder.getFirst();
        for (Role sector : sectorsInTieBreakOrder) {
            if (team.playersIn(sector).size() > team.playersIn(mostNumerous).size()) {
                mostNumerous = sector;
            }
        }
        return mostNumerous;
    }

    private Player lowestAverageIn(TeamOnPitch team, Role sector) {
        Player lowest = null;
        for (Player candidate : team.playersIn(sector)) {
            if (lowest == null || averageOf(candidate) < averageOf(lowest)) {
                lowest = candidate;
            }
        }
        return lowest;
    }

    private Player highestAverageIn(TeamOnPitch team, Role sector) {
        Player highest = null;
        for (Player candidate : team.playersIn(sector)) {
            if (highest == null || averageOf(candidate) > averageOf(highest)) {
                highest = candidate;
            }
        }
        return highest;
    }

    private double averageOf(Player player) {
        return player.attributes().movementAverage();
    }

    private int goalkeepingOf(Player player) {
        return player.attributes().goalkeeping();
    }
}
