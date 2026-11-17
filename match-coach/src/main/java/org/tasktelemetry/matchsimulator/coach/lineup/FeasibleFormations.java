package org.tasktelemetry.matchsimulator.coach.lineup;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.coach.config.Formation;
import org.tasktelemetry.matchsimulator.coach.config.FormationCatalog;
import org.tasktelemetry.matchsimulator.coach.squad.SquadRule;
import org.tasktelemetry.matchsimulator.coach.squad.SquadRuleViolation;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class FeasibleFormations {

    private final FormationCatalog catalog;

    public FeasibleFormations(FormationCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "formation catalog must not be null");
    }

    public List<Formation> amongPlayers(List<Player> players) {
        Map<Role, Long> availableByRole = countByRole(players);
        return catalog.formations().stream()
                .filter(formation -> isFeasible(formation, availableByRole))
                .toList();
    }

    public List<Formation> requireAtLeastOne(List<Player> players) {
        List<Formation> feasible = amongPlayers(players);
        if (feasible.isEmpty()) {
            throw noFeasibleFormation();
        }
        return feasible;
    }

    private Map<Role, Long> countByRole(List<Player> players) {
        Map<Role, Long> countByRole = new EnumMap<>(Role.class);
        for (Player player : players) {
            countByRole.merge(player.role(), 1L, Long::sum);
        }
        return countByRole;
    }

    private boolean isFeasible(Formation formation, Map<Role, Long> availableByRole) {
        for (Role role : Role.values()) {
            long available = availableByRole.getOrDefault(role, 0L);
            if (available < formation.playersIn(role)) {
                return false;
            }
        }
        return true;
    }

    private SquadRuleViolation noFeasibleFormation() {
        List<String> catalogLabels = catalog.formations().stream()
                .map(Formation::label)
                .toList();
        String labelList = String.join(", ", catalogLabels);
        String message = "the squad has no feasible formation, the formations of the catalog are: " + labelList;
        return new SquadRuleViolation(SquadRule.NO_FEASIBLE_FORMATION, message, catalogLabels);
    }
}
