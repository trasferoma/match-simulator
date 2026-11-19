package org.tasktelemetry.matchsimulator.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.MatchMoment;
import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.narration.ReferenceChronicle;

class LiveMatchTest {

    private static final long SEED = ReferenceChronicle.SEED;

    @Test
    void deliveriesConcatenateToTheEventsOfTheWholeMatch() {
        RecordingObserver observer = new RecordingObserver();

        List<MatchEvent> returned = newLiveMatch().play(preparedMatch(), SEED, observer);

        List<MatchEvent> expected = ReferenceChronicle.play(
                ReferenceChronicle.homeTeamFile(), ReferenceChronicle.awayTeamFile(), SEED);
        assertThat(observer.deliveredEvents()).isEqualTo(expected);
        assertThat(returned).isEqualTo(expected);
    }

    @Test
    void noDeliveryIsEmptyAndDeliveredListsCannotBeModified() {
        RecordingObserver observer = new RecordingObserver();

        newLiveMatch().play(preparedMatch(), SEED, observer);

        assertThat(observer.deliveries()).isNotEmpty().allSatisfy(delivery -> {
            assertThat(delivery).isNotEmpty();
            assertThatThrownBy(() -> delivery.add(delivery.get(0)))
                    .isInstanceOf(UnsupportedOperationException.class);
        });
    }

    @Test
    void matchFinishedArrivesOnceAndAfterTheLastDelivery() {
        RecordingObserver observer = new RecordingObserver();

        newLiveMatch().play(preparedMatch(), SEED, observer);

        assertThat(observer.calls()).last().isEqualTo(RecordingObserver.FINISHED);
        assertThat(observer.calls()).containsOnlyOnce(RecordingObserver.FINISHED);
    }

    @Test
    void observerExceptionRisesFromThePlayAndStopsTheMatch() {
        IllegalStateException failure = new IllegalStateException("observer failed");
        RecordingObserver observer = new RecordingObserver() {
            @Override
            public void eventsHappened(List<MatchEvent> newEvents) {
                super.eventsHappened(newEvents);
                throw failure;
            }
        };

        assertThatThrownBy(() -> newLiveMatch().play(preparedMatch(), SEED, observer)).isSameAs(failure);

        assertThat(observer.deliveries()).hasSize(1);
        assertThat(observer.calls()).doesNotContain(RecordingObserver.FINISHED);
    }

    @Test
    void theSamePreparedMatchWithTheSameCoachObjectsPlayedTwiceGivesTheSameMatch() {
        PreparedMatch prepared = preparedMatch();
        LiveMatch liveMatch = newLiveMatch();

        List<MatchEvent> first = liveMatch.play(prepared, SEED, new RecordingObserver());
        List<MatchEvent> second = liveMatch.play(prepared, SEED, new RecordingObserver());

        assertThat(first).isNotEmpty().anyMatch(MatchEvent::isVoluntaryIntervention);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void theCoachesAreClosedAtTheEndEvenWhenTheObserverFails() {
        PreparedMatch prepared = preparedMatch();
        RecordingObserver failing = new RecordingObserver() {
            @Override
            public void eventsHappened(List<MatchEvent> newEvents) {
                throw new IllegalStateException("observer failed");
            }
        };

        assertThatThrownBy(() -> newLiveMatch().play(prepared, SEED, failing))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> prepared.coaches().decide(List.of(), new MatchMoment(false, requests -> List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no match open");
        List<MatchEvent> afterwards = newLiveMatch().play(prepared, SEED, new RecordingObserver());
        assertThat(afterwards).isNotEmpty();
    }

    private PreparedMatch preparedMatch() {
        return ReferenceChronicle.prepare(ReferenceChronicle.homeTeamFile(), ReferenceChronicle.awayTeamFile());
    }

    private LiveMatch newLiveMatch() {
        return new LiveMatch(new MatchEngine(EngineConfig.defaults()));
    }

    private static class RecordingObserver implements MatchObserver {

        static final String FINISHED = "finished";

        private final List<List<MatchEvent>> deliveries = new ArrayList<>();
        private final List<String> calls = new ArrayList<>();

        @Override
        public void eventsHappened(List<MatchEvent> newEvents) {
            deliveries.add(newEvents);
            calls.add("events");
        }

        @Override
        public void matchFinished() {
            calls.add(FINISHED);
        }

        List<List<MatchEvent>> deliveries() {
            return deliveries;
        }

        List<String> calls() {
            return calls;
        }

        List<MatchEvent> deliveredEvents() {
            return deliveries.stream()
                    .flatMap(List::stream)
                    .toList();
        }
    }
}
