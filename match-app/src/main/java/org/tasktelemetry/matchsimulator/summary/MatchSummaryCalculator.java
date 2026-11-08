package org.tasktelemetry.matchsimulator.summary;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class MatchSummaryCalculator {

    private static final Set<EventType> SHOT_TYPES = EnumSet.of(
            EventType.GOAL, EventType.SHOT_SAVED, EventType.SHOT_OFF_TARGET, EventType.SHOT_BLOCKED);
    private static final Set<EventType> CROSS_TYPES = EnumSet.of(EventType.CROSS_COMPLETED, EventType.CROSS_FAILED);
    private static final Set<EventType> SHOT_ON_TARGET_TYPES = EnumSet.of(EventType.GOAL, EventType.SHOT_SAVED);

    public MatchSummary summarize(List<MatchEvent> events) {
        Objects.requireNonNull(events, "events must not be null");
        List<ScoredGoal> goals = listScoredGoals(events);
        TeamStatistics home = calculateStatistics(events, MatchSide.HOME);
        TeamStatistics away = calculateStatistics(events, MatchSide.AWAY);
        return new MatchSummary(goals, home, away);
    }

    private List<ScoredGoal> listScoredGoals(List<MatchEvent> events) {
        return events.stream()
                .filter(event -> event.type() == EventType.GOAL)
                .map(this::toScoredGoal)
                .toList();
    }

    private ScoredGoal toScoredGoal(MatchEvent goal) {
        Player scorer = goal.participants().primary()
                .orElseThrow(() -> new IllegalStateException("a goal event must name its scorer: " + goal));
        return new ScoredGoal(goal.side(), scorer, goal.time());
    }

    private TeamStatistics calculateStatistics(List<MatchEvent> events, MatchSide side) {
        List<MatchEvent> sideEvents = selectEventsOf(events, side);
        int shots = countShots(sideEvents);
        int shotsOnTarget = countShotsOnTarget(sideEvents);
        double expectedGoals = sumExpectedGoals(sideEvents);
        int possessionSeconds = sumPossessionSeconds(sideEvents);
        ContourCounters contour = calculateContour(sideEvents);
        return new TeamStatistics(shots, shotsOnTarget, expectedGoals, possessionSeconds, contour);
    }

    private ContourCounters calculateContour(List<MatchEvent> sideEvents) {
        int corners = countCorners(sideEvents);
        int crosses = countCrosses(sideEvents);
        return ContourCounters.none().withCorners(corners).withCrosses(crosses);
    }

    private List<MatchEvent> selectEventsOf(List<MatchEvent> events, MatchSide side) {
        return events.stream().filter(event -> event.side() == side).toList();
    }

    private int countShots(List<MatchEvent> sideEvents) {
        return (int) sideEvents.stream().filter(event -> SHOT_TYPES.contains(event.type())).count();
    }

    private int countShotsOnTarget(List<MatchEvent> sideEvents) {
        return (int) sideEvents.stream().filter(event -> SHOT_ON_TARGET_TYPES.contains(event.type())).count();
    }

    private int countCorners(List<MatchEvent> sideEvents) {
        return (int) sideEvents.stream().filter(event -> event.type() == EventType.CORNER_KICK).count();
    }

    private int countCrosses(List<MatchEvent> sideEvents) {
        return (int) sideEvents.stream().filter(event -> CROSS_TYPES.contains(event.type())).count();
    }

    private double sumExpectedGoals(List<MatchEvent> sideEvents) {
        return sideEvents.stream()
                .filter(event -> SHOT_TYPES.contains(event.type()))
                .mapToDouble(event -> expectedGoalsOf(event.detail()))
                .sum();
    }

    private int sumPossessionSeconds(List<MatchEvent> sideEvents) {
        return sideEvents.stream().mapToInt(MatchEvent::durationSeconds).sum();
    }

    private double expectedGoalsOf(EventDetail shotDetail) {
        return switch (shotDetail) {
            case EventDetail.Shot shot -> shot.profile().expectedGoals();
            case EventDetail.Save save -> save.profile().expectedGoals();
            case EventDetail.Goal goal -> goal.profile().expectedGoals();
            case EventDetail.NoDetail noDetail -> throw missingShotProfile(shotDetail);
            case EventDetail.StoppageAnnounced announced -> throw missingShotProfile(shotDetail);
        };
    }

    private IllegalStateException missingShotProfile(EventDetail detail) {
        return new IllegalStateException("a shot event must carry a shot profile, but its detail is " + detail);
    }
}
