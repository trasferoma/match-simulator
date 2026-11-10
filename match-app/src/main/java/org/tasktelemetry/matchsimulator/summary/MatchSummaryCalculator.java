package org.tasktelemetry.matchsimulator.summary;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class MatchSummaryCalculator {

    private static final Set<EventType> SHOT_TYPES = EnumSet.of(
            EventType.GOAL, EventType.SHOT_SAVED, EventType.SHOT_OFF_TARGET, EventType.SHOT_BLOCKED);
    private static final Set<EventType> CROSS_TYPES = EnumSet.of(EventType.CROSS_COMPLETED, EventType.CROSS_FAILED,
            EventType.CROSS_DEFLECTED_TO_CORNER);
    private static final Set<EventType> SHOT_ON_TARGET_TYPES = EnumSet.of(EventType.GOAL, EventType.SHOT_SAVED);
    private static final Set<EventType> YELLOW_TYPES = EnumSet.of(EventType.YELLOW_CARD,
            EventType.SECOND_YELLOW_CARD);
    private static final Set<EventType> RED_TYPES = EnumSet.of(EventType.RED_CARD, EventType.SECOND_YELLOW_CARD);
    private static final Set<EventType> DIRECT_RED_TYPES = EnumSet.of(EventType.RED_CARD);
    private static final Set<EventType> SECOND_YELLOW_TYPES = EnumSet.of(EventType.SECOND_YELLOW_CARD);
    private static final Set<EventType> DEFLECTED_CROSS_TYPES = EnumSet.of(EventType.CROSS_DEFLECTED_TO_CORNER);
    private static final Set<EventType> FOUL_TYPES = EnumSet.of(EventType.FOUL);
    private static final Set<EventType> CORNER_TYPES = EnumSet.of(EventType.CORNER_KICK);
    private static final Set<EventType> SUBSTITUTION_TYPES = EnumSet.of(EventType.SUBSTITUTION);
    private static final Set<EventType> OFFSIDE_TYPES = EnumSet.of(EventType.OFFSIDE);
    private static final Set<EventType> INJURY_TYPES = EnumSet.of(EventType.INJURY);
    private static final Set<EventType> STOPPED_BALL_TYPES = EnumSet.of(EventType.INJURY, EventType.SUBSTITUTION);

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
        int crosses = countOpenPlayCrosses(sideEvents);
        int fouls = countOfTypes(fouledSideEvents, FOUL_TYPES);
        int yellowCards = countOfTypes(sideEvents, YELLOW_TYPES);
        int redCards = countOfTypes(sideEvents, RED_TYPES);
        int substitutions = countOfTypes(sideEvents, SUBSTITUTION_TYPES);
        int offsides = countOfTypes(sideEvents, OFFSIDE_TYPES);
        int penalties = countPenaltiesAwarded(sideEvents);
        int injuries = countOfTypes(sideEvents, INJURY_TYPES);
        int directRedCards = countOfTypes(sideEvents, DIRECT_RED_TYPES);
        int secondYellowCards = countOfTypes(sideEvents, SECOND_YELLOW_TYPES);
        int deflectedCrosses = countOfTypes(sideEvents, DEFLECTED_CROSS_TYPES);
        return new ContourCounters(corners, crosses, fouls, yellowCards, redCards, offsides, substitutions, penalties,
                injuries, directRedCards, secondYellowCards, deflectedCrosses);
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

    private int countOpenPlayCrosses(List<MatchEvent> sideEvents) {
        return (int) sideEvents.stream()
                .filter(event -> CROSS_TYPES.contains(event.type()))
                .filter(event -> event.phase() != PlayPhase.SET_PIECE)
                .count();
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
        return sideEvents.stream()
                .filter(event -> !STOPPED_BALL_TYPES.contains(event.type()))
                .mapToInt(MatchEvent::durationSeconds)
                .sum();
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
