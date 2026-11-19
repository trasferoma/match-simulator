package org.tasktelemetry.matchsimulator.coach;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class CoachSimultaneityTest {

    private static final int SEEDS = 30;

    private final CoachedMatch coachedMatch = new CoachedMatch();

    @Test
    void askingTheAutomaticCoachesInSwappedOrderGivesTheSameMatchEventByEvent() {
        int matchesWithInterventions = 0;
        for (long seed : seeds()) {
            CoachedMatch.Outcome homeFirst = coachedMatch.playWithAutomaticCoaches(seed, false);
            CoachedMatch.Outcome awayFirst = coachedMatch.playWithAutomaticCoaches(seed, true);

            assertThat(awayFirst.events()).as("seed %d", seed).isEqualTo(homeFirst.events());
            matchesWithInterventions += homeFirst.events().stream().anyMatch(MatchEvent::isVoluntaryIntervention)
                    ? 1 : 0;
        }

        assertThat(matchesWithInterventions).isPositive();
    }

    @Test
    void theSwappedOrderGivesTheSameMatchAlsoWhenARequestStaysPendingWithTheBallInPlay() {
        CoachedMatch.Lineups lineups = coachedMatch.chooseLineups();
        int pendingRequests = 0;
        for (long seed : seeds()) {
            CoachedMatch.Outcome homeFirst = coachedMatch.play(lineups, seed, togglingCoaches(), false);
            CoachedMatch.Outcome awayFirst = coachedMatch.play(lineups, seed, togglingCoaches(), true);

            assertThat(awayFirst.events()).as("seed %d", seed).isEqualTo(homeFirst.events());
            assertThat(awayFirst.pendingRequests()).isEqualTo(homeFirst.pendingRequests());
            pendingRequests += homeFirst.pendingRequests();
        }

        assertThat(pendingRequests).isPositive();
    }

    @Test
    void aCoachNeverSeesTheInterventionOfTheOtherInTheMomentItIsMadeButInTheNextDelivery() {
        CoachedMatch.Lineups lineups = coachedMatch.chooseLineups();
        PlayStyle homeRequestedStyle = styleOtherThan(lineups.home().team().style());
        PlayStyle awayRequestedStyle = styleOtherThan(lineups.away().team().style());
        RecordingCoach home = new RecordingCoach(MatchSide.HOME, homeRequestedStyle);
        RecordingCoach away = new RecordingCoach(MatchSide.AWAY, awayRequestedStyle);

        coachedMatch.play(lineups, 1L, new MatchCoaches(home, away), false);

        assertThat(home.deliveries()).hasSizeGreaterThan(2);
        assertThat(away.deliveries()).isEqualTo(home.deliveries());
        List<MatchEvent> firstDelivery = home.deliveries().get(0);
        List<MatchEvent> secondDelivery = home.deliveries().get(1);
        assertThat(firstDelivery).noneMatch(MatchEvent::isVoluntaryIntervention);
        assertThat(secondDelivery).filteredOn(MatchEvent::isVoluntaryIntervention)
                .extracting(MatchEvent::side)
                .containsExactly(MatchSide.HOME, MatchSide.AWAY);
    }

    @Test
    void theSameCoachesUsedInTwoMatchesDecideInTheSecondLikeNewOnes() {
        CoachedMatch.Lineups lineups = coachedMatch.chooseLineups();
        MatchCoaches reused = coachedMatch.automaticCoaches();
        coachedMatch.play(lineups, 3L, reused, false);

        CoachedMatch.Outcome secondWithTheReusedCoaches = coachedMatch.play(lineups, 5L, reused, false);
        CoachedMatch.Outcome secondWithNewCoaches = coachedMatch.play(lineups, 5L,
                coachedMatch.automaticCoaches(), false);

        assertThat(secondWithTheReusedCoaches.events()).isEqualTo(secondWithNewCoaches.events());
        assertThat(secondWithNewCoaches.events()).anyMatch(MatchEvent::isVoluntaryIntervention);
    }

    private PlayStyle styleOtherThan(PlayStyle style) {
        return style == PlayStyle.BALANCED ? PlayStyle.ATTACKING : PlayStyle.BALANCED;
    }

    private List<Long> seeds() {
        return LongStream.rangeClosed(1, SEEDS).boxed().toList();
    }

    private MatchCoaches togglingCoaches() {
        Coach home = new TogglingCoach(MatchSide.HOME, PlayStyle.POSSESSION, PlayStyle.COUNTER_ATTACK);
        Coach away = new TogglingCoach(MatchSide.AWAY, PlayStyle.ATTACKING, PlayStyle.DEFENSIVE);
        return new MatchCoaches(home, away);
    }

    private static final class TogglingCoach implements Coach {

        private final MatchSide side;
        private final PlayStyle first;
        private final PlayStyle second;
        private int requests;

        TogglingCoach(MatchSide side, PlayStyle first, PlayStyle second) {
            this.side = side;
            this.first = first;
            this.second = second;
        }

        @Override
        public void open(MatchSide ownSide, PreMatchDecision own, Team opponent, TieBreakDraw draw) {
        }

        @Override
        public void close() {
        }

        @Override
        public List<Intervention> decide(List<MatchEvent> newEvents, MatchMoment moment) {
            PlayStyle style = requests % 2 == 0 ? first : second;
            requests++;
            Intervention request = new Intervention.StyleChange(side, style, ChangeReason.CHASE_GAME);
            return List.of(request);
        }
    }

    private static final class RecordingCoach implements Coach {

        private final MatchSide side;
        private final PlayStyle styleToAskFor;
        private final List<List<MatchEvent>> deliveries = new ArrayList<>();

        RecordingCoach(MatchSide side, PlayStyle styleToAskFor) {
            this.side = side;
            this.styleToAskFor = styleToAskFor;
        }

        @Override
        public void open(MatchSide ownSide, PreMatchDecision own, Team opponent, TieBreakDraw draw) {
        }

        @Override
        public void close() {
        }

        @Override
        public List<Intervention> decide(List<MatchEvent> newEvents, MatchMoment moment) {
            deliveries.add(newEvents);
            if (deliveries.size() != 1) {
                return List.of();
            }
            Intervention request = new Intervention.StyleChange(side, styleToAskFor, ChangeReason.CHASE_GAME);
            return List.of(request);
        }

        List<List<MatchEvent>> deliveries() {
            return deliveries;
        }
    }
}
