package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.ignoringOrder;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.lastEventOf;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.move;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.onPitch;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.onTheBench;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.playToTheEnd;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.substitution;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.withoutEventsOf;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.LiveBallMoments.InjuryMoment;
import org.tasktelemetry.matchsimulator.engine.LiveBallMoments.Moment;
import org.tasktelemetry.matchsimulator.engine.config.DurationRange;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.LineupConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class PendingInterventionTest {

    private static final MatchSide HOME = MatchSide.HOME;
    private static final MatchSide AWAY = MatchSide.AWAY;
    private static final MatchTime START_OF_THE_SECOND_HALF = new MatchTime(MatchPeriod.SECOND_HALF, 0);
    private static final int STEPS_TO_WAIT_FOR_THE_BALL_TO_STOP = 10;
    private static final int SEEDS_TO_SCAN = 3000;
    private static final DurationRange SUBSTITUTION_DURATION = LineupConfig.defaults().substitutionDuration();

    private static final Moment CROSS_IN_FLIGHT = LiveBallMoments.withACrossInFlight();
    private static final Moment PENALTY_TO_TAKE = LiveBallMoments.withAPenaltyToTake();
    private static final Moment SCORING_A_GOAL = LiveBallMoments.beforeTheStepThatScoresAGoal();
    private static final Moment ENDING_THE_FIRST_HALF = LiveBallMoments.beforeTheStepThatEndsTheFirstHalf();
    private static final Moment ENDING_THE_MATCH = LiveBallMoments.beforeTheStepThatEndsTheMatch();
    private static final InjuryMoment INJURING_A_FIELD_PLAYER = LiveBallMoments.beforeTheStepThatInjuresAFieldPlayer();

    @Test
    void withACrossInFlightAValidRequestIsPendingAndChangesNothingUntilTheBallStops() {
        assertPendingUntilTheBallStops(CROSS_IN_FLIGHT);
    }

    @Test
    void withAPenaltyToTakeAValidRequestIsPendingAndChangesNothingUntilTheBallStops() {
        assertPendingUntilTheBallStops(PENALTY_TO_TAKE);
    }

    @Test
    void aPendingSubstitutionIsExecutedWhenTheBallStops() {
        Match match = CROSS_IN_FLIGHT.replay();
        Player leaving = onPitch(match, HOME, Role.DEFENDER, 0);
        Player entering = onTheBench(match, HOME, Role.DEFENDER, 0);

        InterventionResult result = match.apply(substitution(HOME, leaving, entering));
        MatchEvent executed = stepUntilExecuted(match, EventType.SUBSTITUTION);

        assertThat(result).isEqualTo(InterventionResult.pending());
        assertThat(executed.participants().primary()).contains(leaving);
        assertThat(executed.participants().secondary()).contains(entering);
        assertThat(executed.durationSeconds()).isBetween(SUBSTITUTION_DURATION.minSeconds(),
                SUBSTITUTION_DURATION.maxSeconds());
        assertThat(match.teamOnPitch(HOME).players()).contains(entering).doesNotContain(leaving);
    }

    @Test
    void aPendingMoveIsExecutedWhenTheBallStops() {
        Match match = PENALTY_TO_TAKE.replay();
        Player player = onPitch(match, HOME, Role.MIDFIELDER, 0);

        InterventionResult result = match.apply(move(HOME, player, Role.FORWARD));
        MatchEvent executed = stepUntilExecuted(match, EventType.PLAYER_MOVED);

        assertThat(result).isEqualTo(InterventionResult.pending());
        assertThat(executed.participants().primary()).contains(player);
        assertThat(executed.durationSeconds()).isZero();
        assertThat(match.teamOnPitch(HOME).roleOf(player)).isEqualTo(Role.FORWARD);
    }

    @Test
    void anInvalidRequestIsRefusedAtOnceEvenWithTheBallInPlayAndDoesNotTakeThePendingSlot() {
        Match match = CROSS_IN_FLIGHT.replay();
        List<MatchEvent> eventsBefore = match.events();

        InterventionResult invalid = match.apply(styleChange(HOME, PlayStyle.ATTACKING));
        InterventionResult valid = match.apply(styleChange(HOME, PlayStyle.DEFENSIVE));

        assertThat(invalid).isEqualTo(InterventionResult.refused(RefusalReason.STYLE_ALREADY_IN_USE));
        assertThat(valid).isEqualTo(InterventionResult.pending());
        assertThat(match.events()).isEqualTo(eventsBefore);
    }

    @Test
    void aSecondRequestOfTheSameTeamIsRefusedWhileTheFirstIsPendingButTheOtherTeamCanHaveItsOwn() {
        Match match = CROSS_IN_FLIGHT.replay();
        Player midfielder = onPitch(match, HOME, Role.MIDFIELDER, 0);

        InterventionResult first = match.apply(styleChange(HOME, PlayStyle.DEFENSIVE));
        InterventionResult secondOfTheSameKind = match.apply(styleChange(HOME, PlayStyle.BALANCED));
        InterventionResult secondOfAnotherKind = match.apply(move(HOME, midfielder, Role.FORWARD));
        InterventionResult secondEvenIfNotValid = match.apply(styleChange(HOME, PlayStyle.ATTACKING));
        InterventionResult otherTeam = match.apply(styleChange(AWAY, PlayStyle.ATTACKING));

        assertThat(first).isEqualTo(InterventionResult.pending());
        RefusalReason alreadyPending = RefusalReason.INTERVENTION_ALREADY_PENDING;
        assertThat(secondOfTheSameKind).isEqualTo(InterventionResult.refused(alreadyPending));
        assertThat(secondOfAnotherKind).isEqualTo(InterventionResult.refused(alreadyPending));
        assertThat(secondEvenIfNotValid).isEqualTo(InterventionResult.refused(alreadyPending));
        assertThat(otherTeam).isEqualTo(InterventionResult.pending());
    }

    @Test
    void theSlotOfATeamIsFreeAgainOnceItsPendingInterventionHasBeenExecuted() {
        Match match = CROSS_IN_FLIGHT.replay();
        match.apply(styleChange(HOME, PlayStyle.DEFENSIVE));
        stepUntilExecuted(match, EventType.STYLE_CHANGED);

        InterventionResult again = match.apply(styleChange(HOME, PlayStyle.BALANCED));

        assertThat(again).isEqualTo(InterventionResult.executed());
    }

    @Test
    void thePendingInterventionsOfTheTwoTeamsAreExecutedHomeFirstWhateverTheOrderOfTheRequests() {
        Match match = CROSS_IN_FLIGHT.replay();
        int eventsBefore = match.events().size();
        match.apply(styleChange(AWAY, PlayStyle.ATTACKING));
        match.apply(styleChange(HOME, PlayStyle.DEFENSIVE));

        stepUntilBothAreExecuted(match);

        List<MatchEvent> styleChanges = match.events().subList(eventsBefore, match.events().size()).stream()
                .filter(event -> event.type() == EventType.STYLE_CHANGED)
                .toList();
        assertThat(styleChanges).extracting(MatchEvent::side).containsExactly(HOME, AWAY);
        assertThat(styleChanges.get(1).order()).isEqualTo(styleChanges.get(0).order() + 1);
    }

    @Test
    void aPendingInterventionThatIsNoLongerValidWhenTheBallStopsIsDiscardedWithoutEventsNorDraws() {
        InjuryMoment moment = INJURING_A_FIELD_PLAYER;
        Match match = moment.moment().replay();
        Role target = sectorOtherThanTheCurrentOf(match, moment.side(), moment.injured());

        InterventionResult result = match.apply(move(moment.side(), moment.injured(), target));
        playToTheEnd(match);

        assertThat(result).isEqualTo(InterventionResult.pending());
        assertThat(match.events()).isEqualTo(ReferenceMatch.play(moment.moment().seed()));
    }

    @Test
    void aPendingSubstitutionOfAPlayerWhoGetsInjuredIsDiscardedAndTheMatchEqualsTheOneWithoutRequests() {
        InjuryMoment moment = INJURING_A_FIELD_PLAYER;
        Match match = moment.moment().replay();
        Player entering = onTheBench(match, moment.side(), moment.injured().role(), 0);

        InterventionResult result = match.apply(substitution(moment.side(), moment.injured(), entering));
        playToTheEnd(match);

        assertThat(result).isEqualTo(InterventionResult.pending());
        assertThat(match.events()).isEqualTo(ReferenceMatch.play(moment.moment().seed()));
    }

    @Test
    void aPendingSubstitutionIsDiscardedWhenAForcedSubstitutionHasUsedTheLastOneLeft() {
        EngineConfig config = TestConfigs.withLineup(EngineConfig.defaults(), onlyOneSubstitutionAllowed());
        InjuryWithTheBallLive moment = findAnInjuryOfATeamWithoutSubstitutions(config);
        Match match = replayUntil(moment, config);
        Role injuredSector = match.teamOnPitch(moment.side()).roleOf(moment.injured());
        Role otherSector = injuredSector == Role.DEFENDER ? Role.MIDFIELDER : Role.DEFENDER;
        Player leaving = onPitch(match, moment.side(), otherSector, 0);
        Player entering = onTheBench(match, moment.side(), otherSector, 0);
        Match without = ReferenceMatch.start(moment.seed(), config);
        playToTheEnd(without);

        InterventionResult result = match.apply(substitution(moment.side(), leaving, entering));
        playToTheEnd(match);

        assertThat(result).isEqualTo(InterventionResult.pending());
        assertThat(match.events()).isEqualTo(without.events());
        assertThat(match.teamOnPitch(moment.side()).substitutionsMade()).isEqualTo(1);
    }

    @Test
    void anInterventionPendingWhenTheTimeEndsIsExecutedAtHalfTimeAtTheStartOfTheSecondHalfBeforeItsStartEvent() {
        Match match = ENDING_THE_FIRST_HALF.replay();
        int eventsBefore = match.events().size();
        Player leaving = onPitch(match, HOME, Role.MIDFIELDER, 0);
        Player entering = onTheBench(match, HOME, Role.MIDFIELDER, 0);

        InterventionResult result = match.apply(substitution(HOME, leaving, entering));
        match.step();
        match.step();

        assertThat(result).isEqualTo(InterventionResult.pending());
        List<MatchEvent> added = match.events().subList(eventsBefore, match.events().size());
        int halfEndIndex = indexOfTheFirst(added, EventType.FIRST_HALF_END);
        MatchEvent substitution = added.get(halfEndIndex + 1);
        MatchEvent secondHalfStart = added.get(halfEndIndex + 2);
        assertThat(substitution.type()).isEqualTo(EventType.SUBSTITUTION);
        assertThat(substitution.time()).isEqualTo(START_OF_THE_SECOND_HALF);
        assertThat(substitution.durationSeconds()).isZero();
        assertThat(secondHalfStart.type()).isEqualTo(EventType.SECOND_HALF_START);
        assertThat(secondHalfStart.order()).isEqualTo(substitution.order() + 1);
        assertThat(added).hasSize(halfEndIndex + 3);
    }

    @Test
    void anInterventionPendingWhenTheMatchEndsIsDiscardedAndTheMatchEqualsTheOneWithoutRequests() {
        Match match = ENDING_THE_MATCH.replay();
        Player player = onPitch(match, HOME, Role.MIDFIELDER, 0);

        InterventionResult result = match.apply(move(HOME, player, Role.FORWARD));
        match.step();

        assertThat(result).isEqualTo(InterventionResult.pending());
        assertThat(match.isFinished()).isTrue();
        assertThat(match.events()).isEqualTo(ReferenceMatch.play(ENDING_THE_MATCH.seed()));
        assertThat(match.apply(move(HOME, player, Role.FORWARD)))
                .isEqualTo(InterventionResult.refused(RefusalReason.MATCH_NOT_IN_PLAY));
    }

    @Test
    void aPendingStyleChangeIsDiscardedWithoutEventsWhenAGoalIsScoredBeforeTheBallStops() {
        assertDiscardedWhenAGoalIsScored(match -> styleChange(HOME, styleOtherThanTheCurrentOf(match, HOME)));
    }

    @Test
    void aPendingSubstitutionIsDiscardedWithoutEventsWhenAGoalIsScoredBeforeTheBallStops() {
        assertDiscardedWhenAGoalIsScored(match -> substitution(HOME, onPitch(match, HOME, Role.MIDFIELDER, 0),
                onTheBench(match, HOME, Role.MIDFIELDER, 0)));
    }

    @Test
    void aPendingMoveIsDiscardedWithoutEventsWhenAGoalIsScoredBeforeTheBallStops() {
        assertDiscardedWhenAGoalIsScored(match -> move(HOME, onPitch(match, HOME, Role.MIDFIELDER, 0), Role.FORWARD));
    }

    private void assertDiscardedWhenAGoalIsScored(Function<Match, Intervention> intervention) {
        Match match = SCORING_A_GOAL.replay();

        InterventionResult result = match.apply(intervention.apply(match));
        playToTheEnd(match);

        assertThat(result).isEqualTo(InterventionResult.pending());
        assertThat(match.events()).isEqualTo(ReferenceMatch.play(SCORING_A_GOAL.seed()));
    }

    @Test
    void aPendingInterventionConsumesNoDrawSoTheChangeAndBackLeavesTheRestOfTheMatchUntouched() {
        Match match = CROSS_IN_FLIGHT.replay();
        match.apply(styleChange(HOME, PlayStyle.DEFENSIVE));
        stepUntilExecuted(match, EventType.STYLE_CHANGED);
        InterventionResult back = match.apply(styleChange(HOME, PlayStyle.ATTACKING));
        playToTheEnd(match);

        List<MatchEvent> withoutTheStyleChanges = withoutEventsOf(match.events(), EventType.STYLE_CHANGED);

        assertThat(back).isEqualTo(InterventionResult.executed());
        List<MatchEvent> without = ReferenceMatch.play(CROSS_IN_FLIGHT.seed());
        assertThat(ignoringOrder(withoutTheStyleChanges)).isEqualTo(ignoringOrder(without));
    }

    private void assertPendingUntilTheBallStops(Moment moment) {
        Match match = moment.replay();
        List<MatchEvent> eventsBefore = match.events();
        TeamOnPitch homeBefore = match.teamOnPitch(HOME);
        TeamOnPitch awayBefore = match.teamOnPitch(AWAY);

        InterventionResult result = match.apply(styleChange(HOME, PlayStyle.DEFENSIVE));

        assertThat(result).isEqualTo(InterventionResult.pending());
        assertThat(match.events()).isEqualTo(eventsBefore);
        assertThat(match.teamOnPitch(HOME)).isEqualTo(homeBefore);
        assertThat(match.teamOnPitch(AWAY)).isEqualTo(awayBefore);
        assertThat(isBallLive(match)).isTrue();

        MatchEvent executed = stepUntilExecuted(match, EventType.STYLE_CHANGED);

        assertThat(executed.side()).isEqualTo(HOME);
        assertThat(match.teamOnPitch(HOME).style()).isEqualTo(PlayStyle.DEFENSIVE);
        assertThat(isBallLive(match)).isFalse();
        List<MatchEvent> events = match.events();
        assertThat(events.getLast()).isEqualTo(executed);
        assertThat(events.size()).isGreaterThan(eventsBefore.size() + 1);
        assertThat(events).extracting(MatchEvent::order)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, events.size()).boxed().toList());
    }

    @Test
    void aPendingSubstitutionExecutedAfterTheCrossThatTakesTheClockPastTheStoppageEndsTheMatchRightAfterIt() {
        List<EventType> lastTypes = lastTypesAfterAPendingSubstitutionOutlastingTheStoppage();

        assertThat(lastTypes).containsExactly(EventType.SUBSTITUTION, EventType.MATCH_END);
    }

    @Test
    void aPendingStyleChangeOfTheAwayTeamIsDiscardedWhenTheHomeSubstitutionEndsTheMatch() {
        assertAwayInterventionIsDiscardedWhenTheHomeSubstitutionEndsTheMatch(
                match -> styleChange(AWAY, styleOtherThanTheCurrentOf(match, AWAY)));
    }

    @Test
    void aPendingMoveOfTheAwayTeamIsDiscardedWhenTheHomeSubstitutionEndsTheMatch() {
        assertAwayInterventionIsDiscardedWhenTheHomeSubstitutionEndsTheMatch(
                match -> move(AWAY, onPitch(match, AWAY, Role.MIDFIELDER, 0), Role.FORWARD));
    }

    @Test
    void aPendingSubstitutionOfTheAwayTeamIsDiscardedWhenTheHomeSubstitutionEndsTheMatch() {
        assertAwayInterventionIsDiscardedWhenTheHomeSubstitutionEndsTheMatch(
                match -> substitution(AWAY, onPitch(match, AWAY, Role.MIDFIELDER, 0),
                        onTheBench(match, AWAY, Role.MIDFIELDER, 0)));
    }

    private void assertAwayInterventionIsDiscardedWhenTheHomeSubstitutionEndsTheMatch(
            Function<Match, Intervention> awayIntervention) {
        for (long seed = 1; seed <= SEEDS_TO_SCAN; seed++) {
            Match match = ReferenceMatch.start(seed);
            while (!match.isFinished()) {
                match.step();
                if (hasCrossInFlightInTheStoppageWithRoomForNothingElse(match)
                        && endsWithTheHomeSubstitutionAndNothingAfter(match, awayIntervention)) {
                    return;
                }
            }
        }
        throw new IllegalStateException("no moment found in the first " + SEEDS_TO_SCAN + " seeds");
    }

    private boolean endsWithTheHomeSubstitutionAndNothingAfter(Match match,
            Function<Match, Intervention> awayIntervention) {
        Player leaving = onPitch(match, HOME, Role.MIDFIELDER, 0);
        Player entering = onTheBench(match, HOME, Role.MIDFIELDER, 0);
        InterventionResult homeResult = match.apply(substitution(HOME, leaving, entering));
        InterventionResult awayResult = match.apply(awayIntervention.apply(match));
        match.step();
        assertThat(homeResult).isEqualTo(InterventionResult.pending());
        assertThat(awayResult).isEqualTo(InterventionResult.pending());
        List<MatchEvent> events = match.events();
        boolean substituted = events.stream().anyMatch(event -> event.type() == EventType.SUBSTITUTION
                && event.participants().secondary().equals(Optional.of(entering)));
        if (!substituted) {
            return false;
        }
        List<MatchEvent> lastTwo = events.subList(events.size() - 2, events.size());
        assertThat(lastTwo).extracting(MatchEvent::type)
                .containsExactly(EventType.SUBSTITUTION, EventType.MATCH_END);
        assertThat(lastTwo.get(0).side()).isEqualTo(HOME);
        assertThat(match.isFinished()).isTrue();
        return true;
    }

    private PlayStyle styleOtherThanTheCurrentOf(Match match, MatchSide side) {
        return match.teamOnPitch(side).style() == PlayStyle.ATTACKING ? PlayStyle.DEFENSIVE : PlayStyle.ATTACKING;
    }

    private List<EventType> lastTypesAfterAPendingSubstitutionOutlastingTheStoppage() {
        for (long seed = 1; seed <= SEEDS_TO_SCAN; seed++) {
            Match match = ReferenceMatch.start(seed);
            while (!match.isFinished()) {
                match.step();
                if (hasCrossInFlightInTheStoppageWithRoomForNothingElse(match)) {
                    Optional<List<EventType>> found = lastTypesOfThePendingSubstitution(match);
                    if (found.isPresent()) {
                        return found.get();
                    }
                }
            }
        }
        throw new IllegalStateException("no moment found in the first " + SEEDS_TO_SCAN + " seeds");
    }

    private boolean hasCrossInFlightInTheStoppageWithRoomForNothingElse(Match match) {
        MatchTime now = lastEventOf(match).orElseThrow().time();
        OptionalInt expiry = match.events().stream()
                .filter(event -> event.type() == EventType.STOPPAGE_ANNOUNCED
                        && event.time().period() == MatchPeriod.SECOND_HALF)
                .mapToInt(event -> expiryAnnouncedBy(event))
                .findFirst();
        if (match.isFinished() || expiry.isEmpty() || now.period() != MatchPeriod.SECOND_HALF) {
            return false;
        }
        int endOfTheSubstitution = now.seconds() + SUBSTITUTION_DURATION.minSeconds();
        return match.state().hasCrossInFlight() && endOfTheSubstitution > expiry.getAsInt();
    }

    private int expiryAnnouncedBy(MatchEvent announcement) {
        EventDetail.StoppageAnnounced detail = (EventDetail.StoppageAnnounced) announcement.detail();
        return MatchPeriod.REGULATION_SECONDS + detail.minutes() * 60;
    }

    private Optional<List<EventType>> lastTypesOfThePendingSubstitution(Match match) {
        Player leaving = onPitch(match, HOME, Role.MIDFIELDER, 0);
        Player entering = onTheBench(match, HOME, Role.MIDFIELDER, 0);
        InterventionResult result = match.apply(substitution(HOME, leaving, entering));
        assertThat(result).isEqualTo(InterventionResult.pending());
        match.step();
        List<MatchEvent> events = match.events();
        boolean substituted = events.stream().anyMatch(event -> event.type() == EventType.SUBSTITUTION
                && event.participants().secondary().equals(Optional.of(entering)));
        if (!substituted) {
            return Optional.empty();
        }
        List<EventType> lastTypes = events.subList(events.size() - 2, events.size()).stream()
                .map(MatchEvent::type)
                .toList();
        return Optional.of(lastTypes);
    }

    private MatchEvent stepUntilExecuted(Match match, EventType type) {
        for (int step = 0; step < STEPS_TO_WAIT_FOR_THE_BALL_TO_STOP; step++) {
            match.step();
            MatchEvent last = lastEventOf(match).orElseThrow();
            if (last.type() == type) {
                return last;
            }
        }
        throw new AssertionError("the pending intervention was not executed in time");
    }

    private void stepUntilBothAreExecuted(Match match) {
        for (int step = 0; step < STEPS_TO_WAIT_FOR_THE_BALL_TO_STOP; step++) {
            match.step();
            MatchEvent last = lastEventOf(match).orElseThrow();
            if (last.type() == EventType.STYLE_CHANGED && last.side() == AWAY) {
                return;
            }
        }
        throw new AssertionError("the pending interventions were not executed in time");
    }

    private record InjuryWithTheBallLive(long seed, int stepsBefore, MatchSide side, Player injured) {
    }

    private LineupConfig onlyOneSubstitutionAllowed() {
        LineupConfig defaults = LineupConfig.defaults();
        return new LineupConfig(1, defaults.outOfPositionMultiplier(), defaults.substitutionDuration());
    }

    private Match replayUntil(InjuryWithTheBallLive moment, EngineConfig config) {
        Match match = ReferenceMatch.start(moment.seed(), config);
        IntStream.range(0, moment.stepsBefore()).forEach(step -> match.step());
        return match;
    }

    private InjuryWithTheBallLive findAnInjuryOfATeamWithoutSubstitutions(EngineConfig config) {
        for (long seed = 1; seed <= SEEDS_TO_SCAN; seed++) {
            Optional<InjuryWithTheBallLive> found = findAnInjuryInTheMatchOf(seed, config);
            if (found.isPresent()) {
                return found.get();
            }
        }
        throw new IllegalStateException("no injury found in the first " + SEEDS_TO_SCAN + " seeds");
    }

    private Optional<InjuryWithTheBallLive> findAnInjuryInTheMatchOf(long seed, EngineConfig config) {
        Match match = ReferenceMatch.start(seed, config);
        int stepsBefore = 0;
        while (!match.isFinished()) {
            boolean ballWasLive = isBallLive(match);
            Map<MatchSide, Integer> madeBefore = substitutionsMadeBySide(match);
            int eventsBefore = match.events().size();
            match.step();
            List<MatchEvent> added = match.events().subList(eventsBefore, match.events().size());
            Optional<MatchEvent> injury = added.stream()
                    .filter(event -> event.type() == EventType.INJURY)
                    .findFirst();
            boolean isWantedInjury = ballWasLive && injury.isPresent()
                    && madeBefore.get(injury.get().side()) == 0;
            if (isWantedInjury) {
                Player injured = injury.get().participants().primary().orElseThrow();
                return Optional.of(new InjuryWithTheBallLive(seed, stepsBefore, injury.get().side(), injured));
            }
            stepsBefore++;
        }
        return Optional.empty();
    }

    private Map<MatchSide, Integer> substitutionsMadeBySide(Match match) {
        Map<MatchSide, Integer> made = new EnumMap<>(MatchSide.class);
        for (MatchSide side : MatchSide.values()) {
            made.put(side, match.teamOnPitch(side).substitutionsMade());
        }
        return made;
    }

    private boolean isBallLive(Match match) {
        return match.state().hasCrossInFlight() || match.state().isAwaitingPenalty();
    }

    private Role sectorOtherThanTheCurrentOf(Match match, MatchSide side, Player player) {
        Role current = match.teamOnPitch(side).roleOf(player);
        return current == Role.MIDFIELDER ? Role.FORWARD : Role.MIDFIELDER;
    }

    private int indexOfTheFirst(List<MatchEvent> events, EventType type) {
        return IntStream.range(0, events.size())
                .filter(index -> events.get(index).type() == type)
                .findFirst()
                .orElseThrow();
    }

    private Intervention styleChange(MatchSide side, PlayStyle style) {
        return new Intervention.StyleChange(side, style, ChangeReason.PROTECT_LEAD);
    }
}
