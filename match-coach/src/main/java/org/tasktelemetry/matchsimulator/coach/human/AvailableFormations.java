package org.tasktelemetry.matchsimulator.coach.human;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.tasktelemetry.matchsimulator.coach.config.Formation;
import org.tasktelemetry.matchsimulator.coach.config.FormationCatalog;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public final class AvailableFormations {

    private static final int FULL_OUTFIELD_PLAYERS = Team.STARTERS_COUNT - 1;
    private static final Comparator<Deployment> MOST_DEFENDERS_THEN_MOST_MIDFIELDERS = Comparator
            .comparingInt(Deployment::defenders)
            .thenComparingInt(Deployment::midfielders)
            .reversed();

    private final List<Deployment> catalog;

    public AvailableFormations(FormationCatalog formations) {
        Objects.requireNonNull(formations, "formation catalog must not be null");
        this.catalog = formations.formations().stream()
                .map(AvailableFormations::deploymentOf)
                .toList();
    }

    public List<Deployment> forOutfieldPlayers(int outfieldPlayers) {
        int missing = FULL_OUTFIELD_PLAYERS - outfieldPlayers;
        if (missing <= 0) {
            return catalog;
        }
        return reducedCatalog(missing);
    }

    private List<Deployment> reducedCatalog(int missing) {
        Set<Deployment> distinct = new LinkedHashSet<>();
        for (Deployment formation : catalog) {
            List<Deployment> reduced = withoutMen(formation, missing);
            distinct.addAll(reduced);
        }
        return distinct.stream()
                .sorted(MOST_DEFENDERS_THEN_MOST_MIDFIELDERS)
                .toList();
    }

    private List<Deployment> withoutMen(Deployment formation, int missing) {
        List<Deployment> reduced = new ArrayList<>();
        for (int defenders = 0; defenders < formation.defenders(); defenders++) {
            for (int midfielders = 0; midfielders < formation.midfielders(); midfielders++) {
                int forwards = missing - defenders - midfielders;
                if (forwards >= 0 && forwards < formation.forwards()) {
                    Deployment smaller = removing(formation, defenders, midfielders, forwards);
                    reduced.add(smaller);
                }
            }
        }
        return reduced;
    }

    private Deployment removing(Deployment formation, int defenders, int midfielders, int forwards) {
        return new Deployment(formation.defenders() - defenders, formation.midfielders() - midfielders,
                formation.forwards() - forwards);
    }

    private static Deployment deploymentOf(Formation formation) {
        return new Deployment(formation.defenders(), formation.midfielders(), formation.forwards());
    }
}
