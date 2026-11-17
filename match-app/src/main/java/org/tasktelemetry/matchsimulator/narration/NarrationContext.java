package org.tasktelemetry.matchsimulator.narration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.GoalContext;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class NarrationContext {

    private static final int BRACE_GOALS = 2;
    private static final int HAT_TRICK_GOALS = 3;

    private final List<MatchEvent> events;
    private final TeamNames teamNames;
    private final Map<Integer, Score> scoreAfterEvent;
    private final Map<Integer, MatchSetups> setupsAfterEvent;
    private final Map<Integer, MatchEvent> previousEventByOrder;

    public NarrationContext(List<MatchEvent> events, TeamNames teamNames, MatchSetups openingSetups) {
        this.events = List.copyOf(events);
        this.teamNames = teamNames;
        this.scoreAfterEvent = accumulateScoreAfterEachEvent(this.events);
        this.setupsAfterEvent = accumulateSetupsAfterEachEvent(this.events, openingSetups);
        this.previousEventByOrder = indexPreviousEvents(this.events);
    }

    public TeamNames teamNames() {
        return teamNames;
    }

    public Score scoreAfter(MatchEvent event) {
        Score score = scoreAfterEvent.get(event.order());
        return Objects.requireNonNull(score, "the event must belong to the match: " + event);
    }

    public MatchSetups setupsAfter(MatchEvent event) {
        MatchSetups setups = setupsAfterEvent.get(event.order());
        return Objects.requireNonNull(setups, "the event must belong to the match: " + event);
    }

    public Optional<MatchEvent> previousOf(MatchEvent event) {
        MatchEvent previous = previousEventByOrder.get(event.order());
        return Optional.ofNullable(previous);
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

    private static Map<Integer, Score> accumulateScoreAfterEachEvent(List<MatchEvent> events) {
        Map<Integer, Score> scores = new HashMap<>();
        Score score = Score.goalless();
        for (MatchEvent event : events) {
            score = event.type() == EventType.GOAL ? score.withGoalBy(event.side()) : score;
            scores.put(event.order(), score);
        }
        return Map.copyOf(scores);
    }

    private static Map<Integer, MatchSetups> accumulateSetupsAfterEachEvent(List<MatchEvent> events,
            MatchSetups openingSetups) {
        Map<Integer, MatchSetups> setupsByOrder = new HashMap<>();
        MatchSetups setups = openingSetups;
        for (MatchEvent event : events) {
            setups = changeSetupsAfter(event, setups);
            setupsByOrder.put(event.order(), setups);
        }
        return Map.copyOf(setupsByOrder);
    }

    private static MatchSetups changeSetupsAfter(MatchEvent event, MatchSetups setups) {
        MatchSide side = event.side();
        return switch (event.detail()) {
            case EventDetail.StyleChange change -> setups.withStyle(side, change.to());
            case EventDetail.LineupChange change -> setups.withDeployment(side, change.after());
            case EventDetail.PlayerRemoved removal -> setups.withDeployment(side, removal.deploymentAfter());
            default -> setups;
        };
    }

    private static Map<Integer, MatchEvent> indexPreviousEvents(List<MatchEvent> events) {
        Map<Integer, MatchEvent> previousByOrder = new HashMap<>();
        MatchEvent previous = null;
        for (MatchEvent event : events) {
            if (previous != null) {
                previousByOrder.put(event.order(), previous);
            }
            previous = event.type().describesPlayerState() ? previous : event;
        }
        return Map.copyOf(previousByOrder);
    }
}
