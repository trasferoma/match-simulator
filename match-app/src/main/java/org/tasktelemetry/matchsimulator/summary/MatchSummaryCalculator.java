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
    private static final Set<EventType> YELLOW_TYPES = EnumSet.of(EventType.YELLOW_CARD,
            EventType.SECOND_YELLOW_CARD);
    private static final Set<EventType> RED_TYPES = EnumSet.of(EventType.RED_CARD, EventType.SECOND_YELLOW_CARD);
    private static final Set<EventType> FOUL_TYPES = EnumSet.of(EventType.FOUL);
    private static final Set<EventType> CORNER_TYPES = EnumSet.of(EventType.CORNER_KICK);
    private static final Set<EventType> SUBSTITUTION_TYPES = EnumSet.of(EventType.SUBSTITUTION);

    public MatchSummary summarize(List<MatchEvent> events) {
        Objects.requireNonNull(events, "events must not be null");
        List<ScoredGoal> goals = listScoredGoals(events);
        TeamStatistics home = calculateStatistics(events, MatchSide.HOME);
        TeamStatistics away = calculateStatistics(events, MatchSide.AWAY);
        Suspension suspension = findSuspension(events);
        return new MatchSummary(goals, home, away, suspension);
    }

    private Suspension findSuspension(List<MatchEvent> events) {
        return events.stream()
                .filter(event -> event.type() == EventType.MATCH_SUSPENDED)
                .findFirst()
                .map(this::toSuspension)
                .orElse(null);
    }

    private Suspension toSuspension(MatchEvent suspended) {
        if (suspended.detail() instanceof EventDetail.Suspension detail) {
            return new Suspension(detail.responsibleSide(), detail.officialScore(), suspended.time());
        }
        throw new IllegalStateException("a suspension event must carry the suspension detail: " + suspended);
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
        ContourCounters contour = calculateContour(events, side);
        return new TeamStatistics(shots, shotsOnTarget, expectedGoals, possessionSeconds, contour);
    }

    private ContourCounters calculateContour(List<MatchEvent> events, MatchSide side) {
        List<MatchEvent> sideEvents = selectEventsOf(events, side);
        List<MatchEvent> fouledSideEvents = selectEventsOf(events, side.opposite());
        int corners = countOfTypes(sideEvents, CORNER_TYPES);
        int crosses = countOfTypes(sideEvents, CROSS_TYPES);
        int fouls = countOfTypes(fouledSideEvents, FOUL_TYPES);
        int yellowCards = countOfTypes(sideEvents, YELLOW_TYPES);
        int redCards = countOfTypes(sideEvents, RED_TYPES);
        int substitutions = countOfTypes(sideEvents, SUBSTITUTION_TYPES);
        int penalties = countPenaltiesAwarded(sideEvents);
        return new ContourCounters(corners, crosses, fouls, yellowCards, redCards, 0, substitutions, penalties);
    }

    private List<MatchEvent> selectEventsOf(List<MatchEvent> events, MatchSide side) {
        return events.stream().filter(event -> event.side() == side).toList();
    }

    private int countShots(List<MatchEvent> sideEvents) {
        return countOfTypes(sideEvents, SHOT_TYPES);
    }

    private int countShotsOnTarget(List<MatchEvent> sideEvents) {
        return countOfTypes(sideEvents, SHOT_ON_TARGET_TYPES);
    }

    private int countPenaltiesAwarded(List<MatchEvent> sideEvents) {
        return (int) sideEvents.stream()
                .filter(event -> event.detail() instanceof EventDetail.Foul foul && foul.penalty())
                .count();
    }

    private int countOfTypes(List<MatchEvent> events, Set<EventType> types) {
        return (int) events.stream().filter(event -> types.contains(event.type())).count();
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
            case EventDetail.Foul foul -> throw missingShotProfile(shotDetail);
            case EventDetail.Suspension suspension -> throw missingShotProfile(shotDetail);
        };
    }

    private IllegalStateException missingShotProfile(EventDetail detail) {
        return new IllegalStateException("a shot event must carry a shot profile, but its detail is " + detail);
    }
}
