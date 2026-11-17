package org.tasktelemetry.matchsimulator.coach.reading;

import java.util.List;

import org.tasktelemetry.matchsimulator.coach.config.FlowConfig;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;

public final class GameFlowReader {

    private static final double NO_DATA_QUOTA = 1.0 / MatchSide.values().length;

    private final FlowConfig config;

    public GameFlowReader(FlowConfig config) {
        this.config = config;
    }

    public GameFlow flowOf(MatchSide side, EventWindow window) {
        List<MatchEvent> events = window.within(config.windowMinutes());
        FlowQuotas quotas = quotasOf(side, events);
        double dominanceIndex = quotas.dominanceIndex();
        return classify(dominanceIndex);
    }

    public FlowQuotas quotasOf(MatchSide side, List<MatchEvent> events) {
        SideTotals own = new SideTotals();
        SideTotals opponent = new SideTotals();
        for (MatchEvent event : events) {
            SideTotals totals = event.side() == side ? own : opponent;
            totals.add(event);
        }
        double expectedGoals = quotaOf(own.expectedGoals, opponent.expectedGoals);
        double shots = quotaOf(own.shots, opponent.shots);
        double possession = quotaOf(own.possessionSeconds, opponent.possessionSeconds);
        double attackingTime = quotaOf(own.attackingSeconds, opponent.attackingSeconds);
        return new FlowQuotas(expectedGoals, shots, possession, attackingTime);
    }

    private GameFlow classify(double dominanceIndex) {
        if (dominanceIndex <= config.dominatedIndex()) {
            return GameFlow.DOMINATED;
        }
        if (dominanceIndex >= config.dominantIndex()) {
            return GameFlow.DOMINANT;
        }
        return GameFlow.EVEN;
    }

    private double quotaOf(double own, double opponent) {
        double total = own + opponent;
        if (total == 0.0) {
            return NO_DATA_QUOTA;
        }
        return own / total;
    }

    private static final class SideTotals {

        private double expectedGoals;
        private int shots;
        private int possessionSeconds;
        private int attackingSeconds;

        void add(MatchEvent event) {
            if (event.type().isShot()) {
                shots++;
                expectedGoals += event.expectedGoals();
            }
            if (event.type().countsForPossession()) {
                possessionSeconds += event.durationSeconds();
                if (event.zoneRole().isInAttackingArea()) {
                    attackingSeconds += event.durationSeconds();
                }
            }
        }
    }
}
