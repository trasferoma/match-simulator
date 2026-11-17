package org.tasktelemetry.matchsimulator.engine;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

final class LiveBallMoments {

    private static final int SEEDS_TO_SCAN = 3000;

    private LiveBallMoments() {
    }

    record Moment(long seed, int stepsBefore) {

        Match replay() {
            Match match = ReferenceMatch.start(seed);
            IntStream.range(0, stepsBefore).forEach(step -> match.step());
            return match;
        }
    }

    record InjuryMoment(Moment moment, MatchSide side, Player injured) {
    }

    private record ScannedStep(Moment moment, MatchState before, List<MatchEvent> events) {

        boolean startsWithTheBallLive() {
            return before.hasCrossInFlight() || before.isAwaitingPenalty();
        }

        boolean contains(EventType type) {
            return events.stream().anyMatch(event -> event.type() == type);
        }
    }

    static Moment withACrossInFlight() {
        return momentWhere(step -> step.before().hasCrossInFlight());
    }

    static Moment withAPenaltyToTake() {
        return momentWhere(step -> step.before().isAwaitingPenalty() && !step.contains(EventType.GOAL));
    }

    static Moment beforeTheStepThatEndsTheFirstHalf() {
        return momentWhere(step -> step.startsWithTheBallLive() && step.contains(EventType.FIRST_HALF_END));
    }

    static Moment beforeTheStepThatEndsTheMatch() {
        return momentWhere(step -> step.startsWithTheBallLive() && step.contains(EventType.MATCH_END));
    }

    static Moment beforeTheStepThatScoresAGoal() {
        return momentWhere(step -> step.startsWithTheBallLive() && step.contains(EventType.GOAL)
                && !step.contains(EventType.FIRST_HALF_END) && !step.contains(EventType.MATCH_END));
    }

    static InjuryMoment beforeTheStepThatInjuresAFieldPlayer() {
        ScannedStep step = firstStepWhere(LiveBallMoments::startsWithTheBallLiveAndInjuresAFieldPlayer);
        MatchEvent injury = injuryOf(step);
        Player injured = injury.participants().primary().orElseThrow();
        return new InjuryMoment(step.moment(), injury.side(), injured);
    }

    private static boolean startsWithTheBallLiveAndInjuresAFieldPlayer(ScannedStep step) {
        return step.startsWithTheBallLive() && step.contains(EventType.INJURY)
                && injuryOf(step).participants().primary().orElseThrow().role() != Role.GOALKEEPER;
    }

    private static MatchEvent injuryOf(ScannedStep step) {
        return step.events().stream()
                .filter(event -> event.type() == EventType.INJURY)
                .findFirst()
                .orElseThrow();
    }

    private static Moment momentWhere(Predicate<ScannedStep> wanted) {
        return firstStepWhere(wanted).moment();
    }

    private static ScannedStep firstStepWhere(Predicate<ScannedStep> wanted) {
        for (long seed = 1; seed <= SEEDS_TO_SCAN; seed++) {
            Optional<ScannedStep> found = firstStepWhereInTheMatchOf(seed, wanted);
            if (found.isPresent()) {
                return found.get();
            }
        }
        throw new IllegalStateException("no step found in the first " + SEEDS_TO_SCAN + " seeds");
    }

    private static Optional<ScannedStep> firstStepWhereInTheMatchOf(long seed, Predicate<ScannedStep> wanted) {
        Match match = ReferenceMatch.start(seed);
        int stepsBefore = 0;
        while (!match.isFinished()) {
            MatchState before = match.state();
            int eventsBefore = match.events().size();
            match.step();
            List<MatchEvent> events = match.events();
            List<MatchEvent> added = events.subList(eventsBefore, events.size());
            ScannedStep step = new ScannedStep(new Moment(seed, stepsBefore), before, added);
            if (wanted.test(step)) {
                return Optional.of(step);
            }
            stepsBefore++;
        }
        return Optional.empty();
    }
}
