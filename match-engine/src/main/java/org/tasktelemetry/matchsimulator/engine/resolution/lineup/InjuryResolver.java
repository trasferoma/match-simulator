package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import java.util.List;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.config.DurationRange;
import org.tasktelemetry.matchsimulator.engine.config.InjuryConfig;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class InjuryResolver {

    private final InjuryConfig config;
    private final MatchRandom random;
    private final InjurySubstitution injurySubstitution;
    private final MinimumPlayersRule minimumPlayers;
    private final LineupRepair lineupRepair;

    public InjuryResolver(InjuryConfig config, MatchRandom random, InjurySubstitution injurySubstitution,
            MinimumPlayersRule minimumPlayers, LineupRepair lineupRepair) {
        this.config = config;
        this.random = random;
        this.injurySubstitution = injurySubstitution;
        this.minimumPlayers = minimumPlayers;
        this.lineupRepair = lineupRepair;
    }

    public double injuryProbability() {
        return config.probabilityPerAction();
    }

    public Optional<Player> injuredIn(TeamOnPitch team) {
        if (!random.occurs(injuryProbability())) {
            return Optional.empty();
        }
        Player injured = pickAmongPlayersOnThePitch(team);
        return Optional.of(injured);
    }

    public int drawInjurySeconds() {
        DurationRange range = config.duration();
        return random.nextIntInclusive(range.minSeconds(), range.maxSeconds());
    }

    public LineupOutcome replaceInjured(TeamOnPitch team, Player injured) {
        Optional<Player> replacement = injurySubstitution.chooseReplacement(team, injured);
        if (replacement.isEmpty()) {
            return leaveWithoutReplacement(team, injured);
        }
        return bringInReplacement(team, injured, replacement.get());
    }

    private Player pickAmongPlayersOnThePitch(TeamOnPitch team) {
        List<Player> players = team.players();
        int index = random.nextIntInclusive(0, players.size() - 1);
        return players.get(index);
    }

    private LineupOutcome leaveWithoutReplacement(TeamOnPitch team, Player injured) {
        TeamOnPitch reduced = team.withoutPlayer(injured);
        List<Substitution> noSubstitutions = List.of();
        return countThenRepair(reduced, noSubstitutions);
    }

    private LineupOutcome bringInReplacement(TeamOnPitch team, Player injured, Player replacement) {
        TeamOnPitch changed = injurySubstitution.substitute(team, injured, replacement);
        Substitution substitution = new Substitution(injured, replacement);
        List<Substitution> substitutions = List.of(substitution);
        return countThenRepair(changed, substitutions);
    }

    private LineupOutcome countThenRepair(TeamOnPitch team, List<Substitution> substitutions) {
        if (minimumPlayers.isBelowMinimum(team)) {
            return LineupOutcome.suspended(team);
        }
        TeamOnPitch repaired = lineupRepair.repairAfterInjury(team);
        RepairedLineup repairedLineup = new RepairedLineup(repaired, substitutions);
        return LineupOutcome.continuing(repairedLineup);
    }
}
