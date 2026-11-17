package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;

class MatchInterventionTest {

    private static final long SEED = ReferenceMatch.SEED;
    private static final int STEPS_INTO_THE_FIRST_HALF = 60;

    @Test
    void anExecutedStyleChangeIsAnEventWithPreviousStyleNewStyleAndReasonAndNoDurationNorPlayers() {
        Match match = matchInTheFirstHalf(SEED);
        int eventsBefore = match.events().size();

        InterventionResult result = match.apply(styleChange(MatchSide.HOME, PlayStyle.DEFENSIVE));

        assertThat(result).isEqualTo(InterventionResult.executed());
        List<MatchEvent> events = match.events();
        assertThat(events).hasSize(eventsBefore + 1);
        MatchEvent event = events.get(eventsBefore);
        assertThat(event.type()).isEqualTo(EventType.STYLE_CHANGED);
        assertThat(event.side()).isEqualTo(MatchSide.HOME);
        assertThat(event.durationSeconds()).isZero();
        assertThat(event.participants()).isEqualTo(Participants.nobody());
        assertThat(event.detail()).isEqualTo(
                EventDetail.styleChange(PlayStyle.ATTACKING, PlayStyle.DEFENSIVE, ChangeReason.PROTECT_LEAD));
        assertThat(event.order()).isEqualTo(eventsBefore + 1);
    }

    @Test
    void theEventOfAStyleChangeIsDatedAtTheClockOfTheLastEvent() {
        Match match = matchInTheFirstHalf(SEED);
        MatchEvent last = lastEventOf(match);

        match.apply(styleChange(MatchSide.AWAY, PlayStyle.ATTACKING));

        assertThat(lastEventOf(match).time()).isEqualTo(last.time());
    }

    @Test
    void afterTheChangeTheRulesUseTheNewStyleSoTheMatchDivergesFromTheOneWithoutIt() {
        List<MatchEvent> without = ReferenceMatch.play(SEED);
        Match match = matchInTheFirstHalf(SEED);
        match.apply(styleChange(MatchSide.HOME, PlayStyle.DEFENSIVE));
        match.apply(styleChange(MatchSide.AWAY, PlayStyle.ATTACKING));
        playToTheEnd(match);

        List<MatchEvent> with = match.events();

        int changeIndex = indexOfTheFirstStyleChange(with);
        assertThat(normalized(with.subList(0, changeIndex))).isEqualTo(normalized(without.subList(0, changeIndex)));
        List<MatchEvent> afterWith = withoutStyleChanges(with.subList(changeIndex, with.size()));
        List<MatchEvent> afterWithout = without.subList(changeIndex, without.size());
        assertThat(normalized(afterWith)).isNotEqualTo(normalized(afterWithout));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 42})
    void changingTheStyleAndBackInTheSameBoundaryLeavesTheRestOfTheMatchUntouched(long seed) {
        List<MatchEvent> without = ReferenceMatch.play(seed);
        Match match = matchInTheFirstHalf(seed);
        match.apply(styleChange(MatchSide.HOME, PlayStyle.DEFENSIVE));
        match.apply(styleChange(MatchSide.HOME, PlayStyle.ATTACKING));
        playToTheEnd(match);

        List<MatchEvent> with = match.events();

        assertThat(with).hasSize(without.size() + 2);
        assertThat(with).extracting(MatchEvent::order).containsExactlyElementsOf(consecutiveOrdersUpTo(with.size()));
        assertThat(normalized(withoutStyleChanges(with))).isEqualTo(normalized(without));
    }

    @Test
    void aRequestBeforeTheKickOffIsRefusedBecauseTheMatchIsNotInPlay() {
        Match match = ReferenceMatch.start(SEED);

        InterventionResult result = match.apply(styleChange(MatchSide.HOME, PlayStyle.DEFENSIVE));

        assertThat(result).isEqualTo(InterventionResult.refused(RefusalReason.MATCH_NOT_IN_PLAY));
        assertThat(match.events()).isEmpty();
    }

    @Test
    void aRequestAfterTheFinalWhistleIsRefusedBecauseTheMatchIsNotInPlay() {
        Match match = ReferenceMatch.start(SEED);
        playToTheEnd(match);
        List<MatchEvent> before = match.events();

        InterventionResult result = match.apply(styleChange(MatchSide.HOME, PlayStyle.DEFENSIVE));

        assertThat(result).isEqualTo(InterventionResult.refused(RefusalReason.MATCH_NOT_IN_PLAY));
        assertThat(match.events()).isEqualTo(before);
    }

    @Test
    void aRequestForTheStyleAlreadyInUseIsRefusedAndLeavesTheEventsUntouched() {
        Match match = matchInTheFirstHalf(SEED);
        List<MatchEvent> before = match.events();

        InterventionResult result = match.apply(styleChange(MatchSide.HOME, PlayStyle.ATTACKING));

        assertThat(result).isEqualTo(InterventionResult.refused(RefusalReason.STYLE_ALREADY_IN_USE));
        assertThat(match.events()).isEqualTo(before);
    }

    @Test
    void theStyleInUseIsTheOneOfTheLastChangeNotTheOneOfTheTeam() {
        Match match = matchInTheFirstHalf(SEED);
        match.apply(styleChange(MatchSide.HOME, PlayStyle.DEFENSIVE));

        InterventionResult sameAsNow = match.apply(styleChange(MatchSide.HOME, PlayStyle.DEFENSIVE));
        InterventionResult backToTheStart = match.apply(styleChange(MatchSide.HOME, PlayStyle.ATTACKING));

        assertThat(sameAsNow).isEqualTo(InterventionResult.refused(RefusalReason.STYLE_ALREADY_IN_USE));
        assertThat(backToTheStart).isEqualTo(InterventionResult.executed());
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 42})
    void theRefusedRequestsLeaveTheMatchEqualEventByEventToTheOneWithoutRequests(long seed) {
        Match match = ReferenceMatch.start(seed);
        List<InterventionResult> results = new ArrayList<>();
        while (!match.isFinished()) {
            results.add(match.apply(styleChange(MatchSide.HOME, PlayStyle.ATTACKING)));
            results.add(match.apply(styleChange(MatchSide.AWAY, PlayStyle.DEFENSIVE)));
            match.step();
        }
        results.add(match.apply(styleChange(MatchSide.HOME, PlayStyle.DEFENSIVE)));

        assertThat(results).allMatch(InterventionResult::isRefused);
        assertThat(match.events()).isEqualTo(ReferenceMatch.play(seed));
    }

    @Test
    void atHalfTimeTheStyleChangeIsExecutedAtTheStartOfTheSecondHalfBeforeItsStartEvent() {
        Match match = ReferenceMatch.start(SEED);
        stepUntilHalfTime(match);
        int eventsBefore = match.events().size();

        InterventionResult result = match.apply(styleChange(MatchSide.AWAY, PlayStyle.ATTACKING));
        match.step();

        assertThat(result).isEqualTo(InterventionResult.executed());
        List<MatchEvent> events = match.events();
        assertThat(events).hasSize(eventsBefore + 2);
        MatchEvent styleChanged = events.get(eventsBefore);
        MatchEvent secondHalfStart = events.get(eventsBefore + 1);
        assertThat(styleChanged.type()).isEqualTo(EventType.STYLE_CHANGED);
        assertThat(styleChanged.time()).isEqualTo(new MatchTime(MatchPeriod.SECOND_HALF, 0));
        assertThat(styleChanged.durationSeconds()).isZero();
        assertThat(styleChanged.side()).isEqualTo(MatchSide.AWAY);
        assertThat(styleChanged.detail()).isEqualTo(
                EventDetail.styleChange(PlayStyle.DEFENSIVE, PlayStyle.ATTACKING, ChangeReason.PROTECT_LEAD));
        assertThat(secondHalfStart.type()).isEqualTo(EventType.SECOND_HALF_START);
        assertThat(secondHalfStart.time()).isEqualTo(new MatchTime(MatchPeriod.SECOND_HALF, 0));
        assertThat(secondHalfStart.order()).isEqualTo(styleChanged.order() + 1);
    }

    @Test
    void atHalfTimeARequestForTheStyleAlreadyInUseIsRefusedAndTheMatchGoesOnLikeTheOneWithoutRequests() {
        Match match = ReferenceMatch.start(SEED);
        stepUntilHalfTime(match);

        InterventionResult result = match.apply(styleChange(MatchSide.HOME, PlayStyle.ATTACKING));
        playToTheEnd(match);

        assertThat(result).isEqualTo(InterventionResult.refused(RefusalReason.STYLE_ALREADY_IN_USE));
        assertThat(match.events()).isEqualTo(ReferenceMatch.play(SEED));
    }

    @Test
    void aStyleChangeAtHalfTimeLeavesTheStoppageOfTheSecondHalfAndTheDrawsUntouched() {
        List<MatchEvent> without = ReferenceMatch.play(SEED);
        Match match = ReferenceMatch.start(SEED);
        stepUntilHalfTime(match);
        match.apply(styleChange(MatchSide.HOME, PlayStyle.DEFENSIVE));
        match.apply(styleChange(MatchSide.HOME, PlayStyle.ATTACKING));
        playToTheEnd(match);

        List<MatchEvent> with = match.events();

        assertThat(normalized(withoutStyleChanges(with))).isEqualTo(normalized(without));
    }

    private Intervention styleChange(MatchSide side, PlayStyle style) {
        return new Intervention.StyleChange(side, style, ChangeReason.PROTECT_LEAD);
    }

    private Match matchInTheFirstHalf(long seed) {
        Match match = ReferenceMatch.start(seed);
        IntStream.range(0, STEPS_INTO_THE_FIRST_HALF).forEach(step -> match.step());
        return match;
    }

    private void stepUntilHalfTime(Match match) {
        while (lastEventTypeOf(match) != EventType.FIRST_HALF_END) {
            match.step();
        }
    }

    private EventType lastEventTypeOf(Match match) {
        List<MatchEvent> events = match.events();
        if (events.isEmpty()) {
            return null;
        }
        return events.get(events.size() - 1).type();
    }

    private MatchEvent lastEventOf(Match match) {
        List<MatchEvent> events = match.events();
        return events.get(events.size() - 1);
    }

    private void playToTheEnd(Match match) {
        while (!match.isFinished()) {
            match.step();
        }
    }

    private int indexOfTheFirstStyleChange(List<MatchEvent> events) {
        return IntStream.range(0, events.size())
                .filter(index -> events.get(index).type() == EventType.STYLE_CHANGED)
                .findFirst()
                .orElseThrow();
    }

    private List<MatchEvent> withoutStyleChanges(List<MatchEvent> events) {
        return events.stream()
                .filter(event -> event.type() != EventType.STYLE_CHANGED)
                .toList();
    }

    private List<Integer> consecutiveOrdersUpTo(int size) {
        return IntStream.rangeClosed(1, size).boxed().toList();
    }

    private List<MatchEvent> normalized(List<MatchEvent> events) {
        return events.stream().map(this::withOrderOne).toList();
    }

    private MatchEvent withOrderOne(MatchEvent event) {
        return new MatchEvent(1, event.time(), event.type(), event.side(), event.zoneRole(), event.ball(),
                event.phase(), event.participants(), event.durationSeconds(), event.detail());
    }
}
