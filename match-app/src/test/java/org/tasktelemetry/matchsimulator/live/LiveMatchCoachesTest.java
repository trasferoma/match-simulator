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
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Team;
import org.tasktelemetry.matchsimulator.narration.ReferenceChronicle;

class LiveMatchCoachesTest {

    private static final long SEED = ReferenceChronicle.SEED;
    private static final int FIRST_DELIVERY = 1;

    @Test
    void bothCoachesReceiveTheSameEventsTheObserverReceives() {
        PreparedMatch base = preparedMatch();
        ScriptedCoach homeCoach = new ScriptedCoach(MatchSide.HOME, PlayStyle.BALANCED, 0);
        ScriptedCoach awayCoach = new ScriptedCoach(MatchSide.AWAY, PlayStyle.BALANCED, 0);
        RecordingObserver observer = new RecordingObserver();

        play(base, observer, homeCoach, awayCoach);

        assertThat(homeCoach.deliveries()).isNotEmpty().isEqualTo(observer.deliveries().subList(0,
                homeCoach.deliveries().size()));
        assertThat(awayCoach.deliveries()).isEqualTo(homeCoach.deliveries());
    }

    @Test
    void theCoachesAreNotAskedAfterTheLastDelivery() {
        PreparedMatch base = preparedMatch();
        ScriptedCoach homeCoach = new ScriptedCoach(MatchSide.HOME, PlayStyle.BALANCED, 0);
        ScriptedCoach awayCoach = new ScriptedCoach(MatchSide.AWAY, PlayStyle.BALANCED, 0);
        RecordingObserver observer = new RecordingObserver();

        play(base, observer, homeCoach, awayCoach);

        int deliveries = observer.deliveries().size();
        List<MatchEvent> lastDelivery = observer.deliveries().get(deliveries - 1);
        assertThat(lastDelivery).extracting(MatchEvent::type).contains(EventType.MATCH_END);
        assertThat(homeCoach.deliveries()).hasSize(deliveries - 1);
        assertThat(awayCoach.deliveries()).hasSize(deliveries - 1);
    }

    @Test
    void bothRequestsAreCollectedBeforeAnyIsAppliedAndTheEventsComeWithTheNextDelivery() {
        PreparedMatch base = preparedMatch();
        PlayStyle homeNewStyle = styleOtherThan(base.home().style());
        PlayStyle awayNewStyle = styleOtherThan(base.away().style());
        ScriptedCoach homeCoach = new ScriptedCoach(MatchSide.HOME, homeNewStyle, FIRST_DELIVERY);
        ScriptedCoach awayCoach = new ScriptedCoach(MatchSide.AWAY, awayNewStyle, FIRST_DELIVERY);
        RecordingObserver observer = new RecordingObserver();

        play(base, observer, homeCoach, awayCoach);

        List<MatchEvent> firstDelivery = observer.deliveries().get(0);
        List<MatchEvent> secondDelivery = observer.deliveries().get(1);
        assertThat(firstDelivery).extracting(MatchEvent::type).doesNotContain(EventType.STYLE_CHANGED);
        assertThat(secondDelivery.get(0).type()).isEqualTo(EventType.STYLE_CHANGED);
        assertThat(secondDelivery.get(0).side()).isEqualTo(MatchSide.HOME);
        assertThat(secondDelivery.get(1).type()).isEqualTo(EventType.STYLE_CHANGED);
        assertThat(secondDelivery.get(1).side()).isEqualTo(MatchSide.AWAY);
        assertThat(homeCoach.deliveries().get(0)).isEqualTo(awayCoach.deliveries().get(0));
        assertThat(homeCoach.deliveries().get(1)).isEqualTo(secondDelivery);
        assertThat(awayCoach.deliveries().get(1)).isEqualTo(secondDelivery);
    }

    @Test
    void eachCoachReceivesItsOwnTieBreakDrawDerivedFromTheMatchSeedAndRepeatableAcrossPlays() {
        PreparedMatch base = preparedMatch();
        ScriptedCoach homeCoach = new ScriptedCoach(MatchSide.HOME, PlayStyle.BALANCED, 0);
        ScriptedCoach awayCoach = new ScriptedCoach(MatchSide.AWAY, PlayStyle.BALANCED, 0);
        ScriptedCoach homeAgain = new ScriptedCoach(MatchSide.HOME, PlayStyle.BALANCED, 0);
        ScriptedCoach awayAgain = new ScriptedCoach(MatchSide.AWAY, PlayStyle.BALANCED, 0);

        play(base, new RecordingObserver(), homeCoach, awayCoach);
        play(base, new RecordingObserver(), homeAgain, awayAgain);

        assertThat(homeCoach.firstDraw()).isNotEqualTo(awayCoach.firstDraw());
        assertThat(homeAgain.firstDraw()).isEqualTo(homeCoach.firstDraw());
        assertThat(awayAgain.firstDraw()).isEqualTo(awayCoach.firstDraw());
    }

    private PlayStyle styleOtherThan(PlayStyle style) {
        return style == PlayStyle.BALANCED ? PlayStyle.ATTACKING : PlayStyle.BALANCED;
    }

    private PreparedMatch preparedMatch() {
        return ReferenceChronicle.prepare(ReferenceChronicle.homeTeamFile(), ReferenceChronicle.awayTeamFile());
    }

    private void play(PreparedMatch prepared, MatchObserver observer, Coach home, Coach away) {
        MatchCoaches coaches = new MatchCoaches(home, away);
        PreparedMatch withTheScriptedCoaches = new PreparedMatch(prepared.homeDecision(),
                prepared.awayDecision(), prepared.names(), coaches);
        LiveMatch liveMatch = new LiveMatch(new MatchEngine(EngineConfig.defaults()));
        liveMatch.play(withTheScriptedCoaches, SEED, observer);
    }

    private static final class ScriptedCoach implements Coach {

        private final MatchSide side;
        private final PlayStyle styleToAskFor;
        private final int deliveryToAskAt;
        private final List<List<MatchEvent>> deliveries = new ArrayList<>();
        private boolean open;
        private double firstDraw;

        ScriptedCoach(MatchSide side, PlayStyle styleToAskFor, int deliveryToAskAt) {
            this.side = side;
            this.styleToAskFor = styleToAskFor;
            this.deliveryToAskAt = deliveryToAskAt;
        }

        @Override
        public void open(MatchSide ownSide, PreMatchDecision own, Team opponent, TieBreakDraw draw) {
            open = true;
            firstDraw = draw.nextDouble();
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public Optional<Intervention> decide(List<MatchEvent> newEvents) {
            assertThat(open).isTrue();
            deliveries.add(newEvents);
            if (deliveries.size() != deliveryToAskAt) {
                return Optional.empty();
            }
            Intervention styleChange = new Intervention.StyleChange(side, styleToAskFor, ChangeReason.CHASE_GAME);
            return Optional.of(styleChange);
        }

        List<List<MatchEvent>> deliveries() {
            return deliveries;
        }

        double firstDraw() {
            return firstDraw;
        }
    }

    private static final class RecordingObserver implements MatchObserver {

        private final List<List<MatchEvent>> deliveries = new ArrayList<>();

        @Override
        public void eventsHappened(List<MatchEvent> newEvents) {
            deliveries.add(newEvents);
        }

        @Override
        public void matchFinished() {
        }

        List<List<MatchEvent>> deliveries() {
            return deliveries;
        }
    }
}
