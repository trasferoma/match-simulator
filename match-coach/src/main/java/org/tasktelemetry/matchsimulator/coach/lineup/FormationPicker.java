package org.tasktelemetry.matchsimulator.coach.lineup;

import java.util.Comparator;
import java.util.List;

import org.tasktelemetry.matchsimulator.coach.config.Formation;

final class FormationPicker {

    public Formation pickClosestToGrade(List<Formation> feasible, int targetGrade) {
        Comparator<Formation> closestThenMostPrudent = Comparator
                .comparingInt((Formation formation) -> Math.abs(formation.grade() - targetGrade))
                .thenComparingInt(Formation::grade);
        return feasible.stream()
                .sorted(closestThenMostPrudent)
                .findFirst()
                .orElseThrow();
    }
}
