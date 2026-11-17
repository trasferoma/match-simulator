package org.tasktelemetry.matchsimulator.coach.decision;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.coach.config.FatigueResponseConfig;
import org.tasktelemetry.matchsimulator.coach.reading.FatigueReading;
import org.tasktelemetry.matchsimulator.coach.reading.LineupReading;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

final class FatigueRules {

    private final FatigueResponseConfig config;
    private final SubstitutionBudget budget;
    private final PlayerSelection selection;

    FatigueRules(FatigueResponseConfig config, SubstitutionBudget budget, PlayerSelection selection) {
        this.config = config;
        this.budget = budget;
        this.selection = selection;
    }

    Optional<Intervention> restoreFreshness(TeamNow team, int lead, FatigueReading opponent) {
        return answerFreshAttackers(team, lead, opponent).or(() -> replaceTiredPlayer(team));
    }

    Optional<Intervention> replaceTiredForward(ChangeReason reason, TeamNow team) {
        List<Player> forwards = team.lineup().playersIn(Role.FORWARD);
        return replaceMostTiredOf(forwards, FatigueBand.TIRED, reason, team);
    }

    private Optional<Intervention> answerFreshAttackers(TeamNow team, int lead, FatigueReading opponent) {
        boolean isAhead = lead > 0;
        if (!isAhead || !hasFreshAttackers(team, opponent)) {
            return Optional.empty();
        }
        List<Player> defenders = team.lineup().playersIn(Role.DEFENDER);
        return replaceMostTiredOf(defenders, FatigueBand.TIRED, ChangeReason.ANSWER_FRESH_ATTACKERS, team);
    }

    private Optional<Intervention> replaceTiredPlayer(TeamNow team) {
        FatigueBand leastTiredToReplace = leastTiredToReplaceAt(team.minute());
        List<Player> outfieldPlayers = outfieldPlayersOf(team.lineup());
        return replaceMostTiredOf(outfieldPlayers, leastTiredToReplace, ChangeReason.REPLACE_TIRED_PLAYER, team);
    }

    private boolean hasFreshAttackers(TeamNow team, FatigueReading opponent) {
        int windowMinutes = config.freshAttackersWindowMinutes();
        List<Player> freshAttackers = opponent.forwardsEnteredWithin(team.playedTime(), windowMinutes);
        return !freshAttackers.isEmpty();
    }

    private FatigueBand leastTiredToReplaceAt(int minute) {
        boolean isLateEnoughForTheTired = minute >= config.tiredReplacementMinute();
        return isLateEnoughForTheTired ? FatigueBand.TIRED : FatigueBand.EXHAUSTED;
    }

    private List<Player> outfieldPlayersOf(LineupReading lineup) {
        return lineup.sectors().entrySet().stream()
                .filter(entry -> entry.getValue() != Role.GOALKEEPER)
                .map(Map.Entry::getKey)
                .toList();
    }

    private Optional<Intervention> replaceMostTiredOf(List<Player> candidates, FatigueBand leastTired,
            ChangeReason reason, TeamNow team) {
        List<Player> tired = thoseAtLeast(leastTired, candidates, team.fatigue());
        if (tired.isEmpty() || !budget.allowsAnotherSubstitution(team)) {
            return Optional.empty();
        }
        Optional<Player> leaving = selection.weakestAmong(tired, team);
        return leaving.flatMap(player -> substituteFromTheBench(player, reason, team));
    }

    private List<Player> thoseAtLeast(FatigueBand leastTired, List<Player> players, FatigueReading fatigue) {
        return players.stream()
                .filter(player -> fatigue.bandOf(player).compareTo(leastTired) >= 0)
                .toList();
    }

    private Optional<Intervention> substituteFromTheBench(Player leaving, ChangeReason reason, TeamNow team) {
        LineupReading lineup = team.lineup();
        Role currentSector = lineup.sectorOf(leaving);
        Optional<Player> entering = selection.bestReserveWithRole(lineup, currentSector);
        return entering.map(player -> substitution(leaving, player, reason, team));
    }

    private Intervention substitution(Player leaving, Player entering, ChangeReason reason, TeamNow team) {
        return new Intervention.VoluntarySubstitution(team.side(), leaving, entering, reason);
    }
}
