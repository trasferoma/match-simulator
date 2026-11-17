package org.tasktelemetry.matchsimulator.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.Coach;
import org.tasktelemetry.matchsimulator.coach.MatchCoaches;
import org.tasktelemetry.matchsimulator.coach.TieBreakDraw;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;
import org.tasktelemetry.matchsimulator.narration.ReferenceChronicle;

class LiveMatchClosingInterventionTest {

    private static final int SEEDS = 300;
    private static final int FIRST_HALF_FROM_MINUTE = 43;
    private static final int SECOND_HALF_FROM_MINUTE = 87;

    @Test
    void aSubstitutionThatEndsTheMatchIsDeliveredToTheObserverTogetherWithTheEndOfTheMatch() {
        int matchesEndedByASubstitution = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            RecordingObserver observer = new RecordingObserver();

            List<MatchEvent> events = play(seed, MatchPeriod.SECOND_HALF, SECOND_HALF_FROM_MINUTE, observer);

            assertThat(observer.delivered()).as("seed %d", seed).isEqualTo(events);
            assertThat(observer.finishedAfter()).as("seed %d", seed).isEqualTo(events.size());
            matchesEndedByASubstitution += isEndedByAVoluntarySubstitution(events) ? 1 : 0;
        }

        assertThat(matchesEndedByASubstitution).isPositive();
    }

    @Test
    void aSubstitutionThatEndsTheFirstHalfMakesTheCoachesDecideAtHalfTimeBeforeTheSecondHalfStarts() {
        int halvesEndedByASubstitution = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            RecordingObserver observer = new RecordingObserver();

            List<MatchEvent> events = play(seed, MatchPeriod.FIRST_HALF, FIRST_HALF_FROM_MINUTE, observer);

            if (!isFirstHalfEndedByAVoluntarySubstitution(events)) {
                continue;
            }
            halvesEndedByASubstitution++;
            assertThat(observer.delivered()).as("seed %d", seed).isEqualTo(events);
            assertThat(deliveryWith(observer, EventType.FIRST_HALF_END)).as("seed %d", seed)
                    .extracting(MatchEvent::type).doesNotContain(EventType.SECOND_HALF_START);
            assertThat(halfTimeSubstitutions(events)).as("seed %d", seed).isNotEmpty().allSatisfy(event -> {
                assertThat(event.durationSeconds()).isZero();
                assertThat(event.time().period()).isEqualTo(MatchPeriod.SECOND_HALF);
                assertThat(event.time().seconds()).isZero();
            });
        }

        assertThat(halvesEndedByASubstitution).isPositive();
    }

    private List<MatchEvent> play(long seed, MatchPeriod period, int fromMinute, RecordingObserver observer) {
        PreparedMatch base = ReferenceChronicle.prepare(ReferenceChronicle.homeTeamFile(),
                ReferenceChronicle.awayTeamFile());
        Coach substituting = new LateSubstitutionCoach(period, fromMinute);
        Coach idle = new LateSubstitutionCoach(period, Integer.MAX_VALUE);
        MatchCoaches coaches = new MatchCoaches(substituting, idle);
        PreparedMatch prepared = new PreparedMatch(base.homeDecision(), base.awayDecision(), base.names(), coaches);
        LiveMatch liveMatch = new LiveMatch(new MatchEngine(EngineConfig.defaults()));
        return liveMatch.play(prepared, seed, observer);
    }

    private boolean isEndedByAVoluntarySubstitution(List<MatchEvent> events) {
        int last = events.size() - 1;
        return events.get(last).type() == EventType.MATCH_END && isVoluntarySubstitution(events.get(last - 1));
    }

    private boolean isFirstHalfEndedByAVoluntarySubstitution(List<MatchEvent> events) {
        int end = indexOf(events, EventType.FIRST_HALF_END);
        return isVoluntarySubstitution(events.get(end - 1));
    }

    private boolean isVoluntarySubstitution(MatchEvent event) {
        return event.type() == EventType.SUBSTITUTION && event.isVoluntaryIntervention();
    }

    private int indexOf(List<MatchEvent> events, EventType type) {
        for (int index = 0; index < events.size(); index++) {
            if (events.get(index).type() == type) {
                return index;
            }
        }
        throw new IllegalStateException("no " + type + " event");
    }

    private List<MatchEvent> halfTimeSubstitutions(List<MatchEvent> events) {
        int end = indexOf(events, EventType.FIRST_HALF_END);
        int restart = indexOf(events, EventType.SECOND_HALF_START);
        return events.subList(end + 1, restart).stream()
                .filter(this::isVoluntarySubstitution)
                .toList();
    }

    private List<MatchEvent> deliveryWith(RecordingObserver observer, EventType type) {
        return observer.deliveries().stream()
                .filter(delivery -> delivery.stream().anyMatch(event -> event.type() == type))
                .findFirst()
                .orElseThrow();
    }

    private static final class LateSubstitutionCoach implements Coach {

        private final MatchPeriod period;
        private final int fromMinute;
        private final List<Intervention> planned = new ArrayList<>();
        private int next;

        LateSubstitutionCoach(MatchPeriod period, int fromMinute) {
            this.period = period;
            this.fromMinute = fromMinute;
        }

        @Override
        public void open(MatchSide side, PreMatchDecision own, Team opponent, TieBreakDraw draw) {
            planned.clear();
            next = 0;
            Team team = own.team();
            for (Role role : List.of(Role.DEFENDER, Role.MIDFIELDER, Role.FORWARD)) {
                List<Player> leaving = team.starters().stream().filter(player -> player.role() == role).toList();
                List<Player> entering = team.bench().stream().filter(player -> player.role() == role).toList();
                for (int index = 0; index < Math.min(leaving.size(), entering.size()); index++) {
                    planned.add(new Intervention.VoluntarySubstitution(side, leaving.get(index),
                            entering.get(index), ChangeReason.PROTECT_LEAD));
                }
            }
        }

        @Override
        public Optional<Intervention> decide(List<MatchEvent> newEvents) {
            MatchEvent last = newEvents.get(newEvents.size() - 1);
            boolean isTime = last.time().period() == period && last.time().minute() >= fromMinute;
            boolean isHalfTime = period == MatchPeriod.FIRST_HALF && last.type() == EventType.FIRST_HALF_END;
            if ((!isTime && !isHalfTime) || next >= planned.size()) {
                return Optional.empty();
            }
            Intervention request = planned.get(next);
            next++;
            return Optional.of(request);
        }

        @Override
        public void close() {
            planned.clear();
        }
    }

    private static final class RecordingObserver implements MatchObserver {

        private final List<List<MatchEvent>> deliveries = new ArrayList<>();
        private int deliveredWhenFinished = -1;

        @Override
        public void eventsHappened(List<MatchEvent> newEvents) {
            deliveries.add(newEvents);
        }

        @Override
        public void matchFinished() {
            deliveredWhenFinished = delivered().size();
        }

        List<List<MatchEvent>> deliveries() {
            return deliveries;
        }

        List<MatchEvent> delivered() {
            return deliveries.stream().flatMap(List::stream).toList();
        }

        int finishedAfter() {
            return deliveredWhenFinished;
        }
    }
}
