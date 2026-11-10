package org.tasktelemetry.matchsimulator.narration;

import java.util.List;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.GoalContext;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class NarrationContext {

    private static final int BRACE_GOALS = 2;
    private static final int HAT_TRICK_GOALS = 3;

    private final List<MatchEvent> events;
    private final TeamNames teamNames;

    public NarrationContext(List<MatchEvent> events, TeamNames teamNames) {
        this.events = List.copyOf(events);
        this.teamNames = teamNames;
    }

    public TeamNames teamNames() {
        return teamNames;
    }

    public Optional<GoalHighlight> highlightOf(MatchEvent event) {
        if (event.type() != EventType.GOAL) {
            return Optional.empty();
        }
        int scorerGoals = countGoalsOfTheScorerUpTo(event);
        if (scorerGoals == HAT_TRICK_GOALS) {
            return Optional.of(GoalHighlight.HAT_TRICK);
        }
        if (scorerGoals == BRACE_GOALS) {
            return Optional.of(GoalHighlight.BRACE);
        }
        return highlightFromTheScore(event);
    }

    private int countGoalsOfTheScorerUpTo(MatchEvent goal) {
        Optional<Player> scorer = goal.participants().primary();
        return (int) events.stream()
                .filter(event -> event.type() == EventType.GOAL)
                .filter(event -> event.order() <= goal.order())
                .filter(event -> event.side() == goal.side())
                .filter(event -> event.participants().primary().equals(scorer))
                .count();
    }

    private Optional<GoalHighlight> highlightFromTheScore(MatchEvent goal) {
        GoalContext context = goalContextOf(goal);
        return switch (context) {
            case TAKES_THE_LEAD -> Optional.of(GoalHighlight.TAKES_THE_LEAD);
            case EQUALISER -> Optional.of(GoalHighlight.EQUALISER);
            case EXTENDS_THE_LEAD, CONSOLATION -> Optional.empty();
        };
    }

    private GoalContext goalContextOf(MatchEvent goal) {
        if (goal.detail() instanceof EventDetail.Goal detail) {
            return detail.context();
        }
        throw new IllegalStateException("a goal event must carry the goal detail: " + goal);
    }
}
