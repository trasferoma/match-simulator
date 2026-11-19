package org.tasktelemetry.matchsimulator.narration.highlights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_DEFENDER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_GOALKEEPER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_BENCH;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_FORWARD;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_MIDFIELDER;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.event.SaveQuality;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.event.ShotQuality;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.live.LiveMatch;
import org.tasktelemetry.matchsimulator.live.MatchObserver;
import org.tasktelemetry.matchsimulator.live.PreparedMatch;
import org.tasktelemetry.matchsimulator.narration.MatchSetups;
import org.tasktelemetry.matchsimulator.narration.NarratedEventFilter;
import org.tasktelemetry.matchsimulator.narration.NarrationEvents;
import org.tasktelemetry.matchsimulator.narration.ReferenceChronicle;
import org.tasktelemetry.matchsimulator.narration.TeamNames;

class LiveHighlightsTest {

    private static final String SEGMENT = "segment";
    private static final String FINISHED = "finished";
    private static final String PAUSED = "paused";

    private final HighlightsSelector selector = new HighlightsSelector(
            new HighlightsConfigReader().readDefault(), new NarratedEventFilter());
    private final HighlightsNarrator narrator = HighlightsNarrator.italian();
    private final NarrationEvents fixture = new NarrationEvents();
    private final RecordingListener listener = new RecordingListener();

    @Test
    void theExampleSequenceDeliversEachSegmentWhenItIsDecided() {
        addExampleSequence();
        List<MatchEvent> events = fixture.list();
        LiveHighlights live = newLiveHighlights(NarrationEvents.TEAM_NAMES, NarrationEvents.OPENING_SETUPS);

        deliverOneAtATime(live, events);

        assertThat(listener.eventCounts()).containsExactly(6, 7, 8, 9, 10, 11, 12, 15);
        assertThat(listener.calls()).doesNotContain(FINISHED);

        live.matchFinished();

        List<NarratedSegment> expected = narratedAtTheEnd(events, NarrationEvents.TEAM_NAMES, NarrationEvents.OPENING_SETUPS);
        assertThat(expected).hasSize(13);
        assertThat(listener.segments()).isEqualTo(expected);
        assertThat(listener.calls()).last().isEqualTo(FINISHED);
        assertThat(listener.calls()).containsOnlyOnce(FINISHED);
    }

    @Test
    void afterEachDeliveryExactlyTheDecidedSegmentsHaveBeenDelivered() {
        addExampleSequence();
        List<MatchEvent> events = fixture.list();
        List<HighlightSegment> finalSegments = selector.select(events);
        LiveHighlights live = newLiveHighlights(NarrationEvents.TEAM_NAMES, NarrationEvents.OPENING_SETUPS);

        for (int happenedCount = 1; happenedCount <= events.size(); happenedCount++) {
            listener.setEventsHappened(happenedCount);
            live.eventsHappened(List.of(events.get(happenedCount - 1)));

            int decidedCount = countDecided(finalSegments, happenedCount);
            assertThat(listener.segments()).as("after %d events", happenedCount).hasSize(decidedCount);
        }
    }

    @Test
    void aStopLengthenedByTheNextEventsIsDeliveredOnceAndWhole() {
        addHomePasses(4);
        addHomeShot();
        addHomeCorner();
        addAwayPasses(8);
        List<MatchEvent> events = fixture.list();
        LiveHighlights live = newLiveHighlights(NarrationEvents.TEAM_NAMES, NarrationEvents.OPENING_SETUPS);

        deliverOneAtATime(live, events);

        NarratedSegment.Stop wholeStop = (NarratedSegment.Stop) listener.segments().get(0);
        assertThat(wholeStop.lines()).hasSize(6);
        assertThat(listener.eventCounts().get(0)).isEqualTo(11);
        assertThat(listener.segments().stream().filter(NarratedSegment.Stop.class::isInstance)).hasSize(1);
        assertThat(wholeStop).isEqualTo(narratedAtTheEnd(events, NarrationEvents.TEAM_NAMES, NarrationEvents.OPENING_SETUPS).get(0));
    }

    @Test
    void theRecoveriesInTheMiddleDoNotCountInTheWaitForTheDecision() {
        fixture.add(EventType.KICK_OFF, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.nobody(), EventDetail.none());
        for (int index = 0; index < 5; index++) {
            fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
            fixture.addRecovered(MatchSide.HOME, HOME_MIDFIELDER, FatigueBand.FRESH);
        }
        List<MatchEvent> events = fixture.list();
        LiveHighlights live = newLiveHighlights(NarrationEvents.TEAM_NAMES, NarrationEvents.OPENING_SETUPS);

        deliverOneAtATime(live, events);

        int eventsWhenTheSixthEventThatIsNotARecoveryHappened = 10;
        assertThat(listener.eventCounts().get(0)).isEqualTo(eventsWhenTheSixthEventThatIsNotARecoveryHappened);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 42})
    void theLiveChronicleEqualsTheEndOfMatchChronicle(long seed) {
        PreparedMatch prepared = prepareReferenceMatch();
        TeamNames teamNames = prepared.names();
        LiveHighlights live = newLiveHighlights(teamNames, prepared.openingSetups());

        List<MatchEvent> events = newLiveMatch().play(prepared, seed, live);

        assertThat(listener.segments()).isEqualTo(narratedAtTheEnd(events, teamNames, prepared.openingSetups()));
        assertThat(listener.calls()).last().isEqualTo(FINISHED);
    }

    @Test
    void withSeed42TheKickOffStopArrivesInTheFirstDeliveryReachingSixEvents() {
        PreparedMatch prepared = prepareReferenceMatch();
        TeamNames teamNames = prepared.names();
        ObserverSpy spy = new ObserverSpy(newLiveHighlights(teamNames, prepared.openingSetups()), listener);

        List<MatchEvent> events = newLiveMatch().play(prepared, ReferenceChronicle.SEED, spy);

        List<HighlightSegment> selected = selector.select(events);
        assertThat(selected.get(0).events()).hasSize(1);
        assertThat(selected.get(1)).isInstanceOf(HighlightSegment.Frame.class);
        int firstDeliveryWithSixEvents = IntStream.range(0, spy.happenedCounts().size())
                .filter(index -> spy.happenedCounts().get(index) >= 6)
                .findFirst()
                .orElseThrow();
        assertThat(spy.segmentCounts().subList(0, firstDeliveryWithSixEvents)).containsOnly(0);
        assertThat(spy.segmentCounts().get(firstDeliveryWithSixEvents)).isPositive();
        assertThat(listener.segments().get(0)).isEqualTo(narratedAtTheEnd(events, teamNames, prepared.openingSetups()).get(0));
        assertThat(listener.calls().indexOf(SEGMENT)).isLessThan(listener.calls().indexOf(FINISHED));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 4, 5, 6, 7, 8})
    void withoutAPauseEachDeliveryHasExactlyTheDecidedSegmentsOfTheWholeSelection(long seed) {
        PreparedMatch prepared = prepareReferenceMatch();
        ObserverSpy spy = new ObserverSpy(newLiveHighlights(prepared.names(), prepared.openingSetups()), listener);

        List<MatchEvent> events = newLiveMatch().play(prepared, seed, spy);

        List<Integer> expectedCounts = spy.happenedCounts().stream()
                .map(happenedCount -> countDecidedByTheSelectionOfTheFirst(events, happenedCount))
                .toList();
        assertThat(spy.segmentCounts()).isEqualTo(expectedCounts);
        assertThat(listener.segments()).isEqualTo(narratedAtTheEnd(events, prepared.names(), prepared.openingSetups()));
    }

    @Test
    void theCatchUpNarratesEverythingHappenedWithoutWaitingAndTheMatchGoesOnFromThere() {
        fixture.add(EventType.KICK_OFF, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.nobody(), EventDetail.none());
        addHomePasses(10);
        List<MatchEvent> beforeThePause = fixture.list();
        LiveHighlights live = newLiveHighlights(NarrationEvents.TEAM_NAMES, NarrationEvents.OPENING_SETUPS);
        deliverOneAtATime(live, beforeThePause);

        live.catchUpForPause();

        List<NarratedSegment> expected = narratedAtTheEnd(beforeThePause, NarrationEvents.TEAM_NAMES,
                NarrationEvents.OPENING_SETUPS);
        assertThat(expected).hasSize(11);
        assertThat(listener.segments()).isEqualTo(expected);
        assertThat(listener.calls()).last().isEqualTo(PAUSED);
        assertThat(listener.calls()).doesNotContain(FINISHED);
    }

    @Test
    void aChanceAfterThePauseDoesNotPullTheEventsAlreadyNarratedAsPreparation() {
        fixture.add(EventType.KICK_OFF, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.nobody(), EventDetail.none());
        addHomePasses(10);
        List<MatchEvent> beforeThePause = fixture.list();
        LiveHighlights live = newLiveHighlights(NarrationEvents.TEAM_NAMES, NarrationEvents.OPENING_SETUPS);
        deliverOneAtATime(live, beforeThePause);
        live.catchUpForPause();
        addHomePasses(1);
        addHomeShot();
        addAwayPasses(8);
        List<MatchEvent> events = fixture.list();

        deliverOneAtATime(live, events.subList(beforeThePause.size(), events.size()));
        live.matchFinished();

        assertThat(countNarratedItems(listener.segments())).isEqualTo(events.size());
        List<NarratedSegment.Stop> chances = findChanceStops(listener.segments());
        assertThat(chances).hasSize(1);
        assertThat(chances.get(0).lines()).hasSize(2);
        assertThat(listener.calls()).containsOnlyOnce(FINISHED);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 7, 25, 90})
    void noEventIsNarratedTwiceOrLostWhateverTheMomentsOfThePauses(int deliveriesBetweenPauses) {
        PreparedMatch prepared = prepareReferenceMatch();
        LiveHighlights live = newLiveHighlights(prepared.names(), prepared.openingSetups());
        PausingObserver pausing = new PausingObserver(live, deliveriesBetweenPauses);

        List<MatchEvent> events = newLiveMatch().play(prepared, ReferenceChronicle.SEED, pausing);

        List<NarratedSegment> withoutPauses = narratedAtTheEnd(events, prepared.names(), prepared.openingSetups());
        assertThat(countNarratedItems(listener.segments())).isEqualTo(countNarratedItems(withoutPauses));
        assertThat(listener.calls().stream().filter(PAUSED::equals)).isNotEmpty();
        assertThat(listener.calls()).last().isEqualTo(FINISHED);
    }

    @Test
    void theInterventionsOfTheManagerDeliveredLiveAreAmongTheNarratedSegments() {
        Deployment deployment = new Deployment(4, 4, 2);
        EventDetail style = EventDetail.styleChange(PlayStyle.BALANCED, PlayStyle.ATTACKING,
                ChangeReason.MANAGER_DECISION);
        EventDetail lineup = EventDetail.lineupChange(Role.MIDFIELDER, ChangeReason.MANAGER_DECISION, deployment,
                deployment);
        Participants substituted = Participants.of(HOME_MIDFIELDER, MatchSide.HOME, HOME_BENCH, MatchSide.HOME);
        fixture.add(EventType.STYLE_CHANGED, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.nobody(), style);
        addHomePasses(1);
        fixture.add(EventType.SUBSTITUTION, MatchSide.HOME, ZoneRole.MIDFIELD, substituted, lineup);
        addHomePasses(1);
        fixture.add(EventType.PLAYER_MOVED, MatchSide.HOME, ZoneRole.MIDFIELD,
                NarrationEvents.participantsOf(HOME_MIDFIELDER), lineup);
        addHomePasses(8);
        LiveHighlights live = newLiveHighlights(NarrationEvents.TEAM_NAMES, NarrationEvents.OPENING_SETUPS);

        deliverOneAtATime(live, fixture.list());

        List<NarratedSegment.Stop> stops = listener.segments().stream()
                .filter(NarratedSegment.Stop.class::isInstance)
                .map(NarratedSegment.Stop.class::cast)
                .toList();
        assertThat(stops).hasSize(3);
        assertThat(stops).allSatisfy(stop -> assertThat(stop.lines()).hasSize(1));
        assertThat(stops).allSatisfy(stop -> assertThat(stop.lines().get(0).text()).isNotBlank());
    }

    private void addExampleSequence() {
        fixture.add(EventType.KICK_OFF, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.nobody(), EventDetail.none());
        addHomePasses(5);
        addAwayPasses(1);
        addHomePasses(1);
        Participants crosser = NarrationEvents.participantsOf(HOME_MIDFIELDER);
        fixture.add(EventType.CROSS_COMPLETED, MatchSide.HOME, ZoneRole.ATTACKING_THIRD, crosser, EventDetail.none());
        addHomeShot();
        addAwayPasses(5);
    }

    private void addHomePasses(int count) {
        for (int index = 0; index < count; index++) {
            fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        }
    }

    private void addAwayPasses(int count) {
        for (int index = 0; index < count; index++) {
            fixture.add(EventType.PASS_COMPLETED, MatchSide.AWAY, AWAY_DEFENDER);
        }
    }

    private void addHomeShot() {
        Participants shooter = NarrationEvents.participantsOf(HOME_FORWARD, AWAY_GOALKEEPER);
        EventDetail save = EventDetail.save(NarrationEvents.shotProfile(ShotKind.FOOT, ShotQuality.HALF_CHANCE),
                SaveQuality.ROUTINE);
        fixture.add(EventType.SHOT_SAVED, MatchSide.HOME, ZoneRole.OPPONENT_BOX, shooter, save);
    }

    private void addHomeCorner() {
        Participants taker = NarrationEvents.participantsOf(HOME_MIDFIELDER);
        fixture.add(EventType.CORNER_KICK, MatchSide.HOME, ZoneRole.OPPONENT_BOX, taker, EventDetail.none());
    }

    private void deliverOneAtATime(LiveHighlights live, List<MatchEvent> events) {
        for (int index = 0; index < events.size(); index++) {
            listener.setEventsHappened(index + 1);
            live.eventsHappened(List.of(events.get(index)));
        }
    }

    private int countDecided(List<HighlightSegment> segments, int happenedCount) {
        int lastDecidedPosition = happenedCount - selector.lookAhead() - 2;
        int decidedCount = 0;
        int endPosition = -1;
        for (HighlightSegment segment : segments) {
            endPosition += segment.events().size();
            if (endPosition <= lastDecidedPosition) {
                decidedCount++;
            }
        }
        return decidedCount;
    }

    private int countDecidedByTheSelectionOfTheFirst(List<MatchEvent> events, int happenedCount) {
        List<MatchEvent> happened = events.subList(0, happenedCount);
        List<HighlightSegment> segments = selector.select(happened);
        int lastDecidedPosition = selector.findLastDecidedPosition(happened);
        int decidedCount = 0;
        int endPosition = -1;
        for (HighlightSegment segment : segments) {
            endPosition += segment.events().size();
            if (endPosition <= lastDecidedPosition) {
                decidedCount++;
            }
        }
        return decidedCount;
    }

    private int countNarratedItems(List<NarratedSegment> segments) {
        return segments.stream()
                .mapToInt(this::countItemsOf)
                .sum();
    }

    private int countItemsOf(NarratedSegment segment) {
        return switch (segment) {
            case NarratedSegment.Frame frame -> 1;
            case NarratedSegment.Stop stop -> stop.lines().size();
        };
    }

    private List<NarratedSegment.Stop> findChanceStops(List<NarratedSegment> segments) {
        return segments.stream()
                .filter(NarratedSegment.Stop.class::isInstance)
                .map(NarratedSegment.Stop.class::cast)
                .filter(NarratedSegment.Stop::containsChance)
                .toList();
    }

    private List<NarratedSegment> narratedAtTheEnd(List<MatchEvent> events, TeamNames teamNames,
            MatchSetups openingSetups) {
        List<HighlightSegment> selected = selector.select(events);
        return narrator.narrate(selected, events, teamNames, openingSetups);
    }

    private LiveHighlights newLiveHighlights(TeamNames teamNames, MatchSetups openingSetups) {
        return new LiveHighlights(selector, narrator, teamNames, openingSetups, listener);
    }

    private LiveMatch newLiveMatch() {
        return new LiveMatch(new MatchEngine(EngineConfig.defaults()));
    }

    private PreparedMatch prepareReferenceMatch() {
        Path homeFile = ReferenceChronicle.homeTeamFile();
        Path awayFile = ReferenceChronicle.awayTeamFile();
        return ReferenceChronicle.prepare(homeFile, awayFile);
    }

    private static class ObserverSpy implements MatchObserver {

        private final LiveHighlights live;
        private final RecordingListener listener;
        private final List<Integer> happenedCounts = new ArrayList<>();
        private final List<Integer> segmentCounts = new ArrayList<>();
        private int happenedCount;

        ObserverSpy(LiveHighlights live, RecordingListener listener) {
            this.live = live;
            this.listener = listener;
        }

        @Override
        public void eventsHappened(List<MatchEvent> newEvents) {
            happenedCount += newEvents.size();
            live.eventsHappened(newEvents);
            happenedCounts.add(happenedCount);
            segmentCounts.add(listener.segments().size());
        }

        @Override
        public void matchFinished() {
            live.matchFinished();
        }

        List<Integer> happenedCounts() {
            return happenedCounts;
        }

        List<Integer> segmentCounts() {
            return segmentCounts;
        }
    }

    private static class PausingObserver implements MatchObserver {

        private final LiveHighlights live;
        private final int deliveriesBetweenPauses;
        private int deliveries;

        PausingObserver(LiveHighlights live, int deliveriesBetweenPauses) {
            this.live = live;
            this.deliveriesBetweenPauses = deliveriesBetweenPauses;
        }

        @Override
        public void eventsHappened(List<MatchEvent> newEvents) {
            live.eventsHappened(newEvents);
            deliveries++;
            if (deliveries % deliveriesBetweenPauses == 0) {
                live.catchUpForPause();
            }
        }

        @Override
        public void matchFinished() {
            live.matchFinished();
        }
    }

    private static class RecordingListener implements HighlightsListener {

        private final List<NarratedSegment> segments = new ArrayList<>();
        private final List<Integer> eventCounts = new ArrayList<>();
        private final List<String> calls = new ArrayList<>();
        private int eventsHappened;

        void setEventsHappened(int eventsHappened) {
            this.eventsHappened = eventsHappened;
        }

        @Override
        public void segmentNarrated(NarratedSegment segment) {
            segments.add(segment);
            eventCounts.add(eventsHappened);
            calls.add(SEGMENT);
        }

        @Override
        public void chronicleFinished() {
            calls.add(FINISHED);
        }

        @Override
        public void chroniclePaused() {
            calls.add(PAUSED);
        }

        List<NarratedSegment> segments() {
            return segments;
        }

        List<Integer> eventCounts() {
            return eventCounts;
        }

        List<String> calls() {
            return calls;
        }
    }
}
