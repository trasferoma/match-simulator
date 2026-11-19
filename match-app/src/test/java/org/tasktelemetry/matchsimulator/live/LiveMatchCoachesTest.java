package org.tasktelemetry.matchsimulator.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.Coach;
import org.tasktelemetry.matchsimulator.coach.MatchCoaches;
import org.tasktelemetry.matchsimulator.coach.MatchMoment;
import org.tasktelemetry.matchsimulator.coach.TieBreakDraw;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.intervention.PreviewVerdict;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
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

    @Test
    void theMomentSaysWhetherTheBallIsLiveExactlyAsTheEngineTreatsARequestMadeAtThatMoment() {
        PreparedMatch base = preparedMatch();
        ProbingCoach homeCoach = new ProbingCoach(new StyleToggle(MatchSide.HOME, base.home().style()));
        ScriptedCoach awayCoach = new ScriptedCoach(MatchSide.AWAY, PlayStyle.BALANCED, 0);

        play(base, new RecordingObserver(), homeCoach, awayCoach);

        List<InterventionResult> settled = homeCoach.settledResults().stream().flatMap(List::stream).toList();
        List<Boolean> ballLiveAtEachRequest = homeCoach.ballLiveAtEachDecision();
        assertThat(settled).hasSameSizeAs(ballLiveAtEachRequest);
        int askedWithTheBallLive = 0;
        int askedWithTheBallStill = 0;
        for (int index = 0; index < settled.size(); index++) {
            InterventionResult result = settled.get(index);
            boolean ballLive = ballLiveAtEachRequest.get(index);
            if (result.isRefused()) {
                continue;
            }
            boolean wasKeptPending = result instanceof InterventionResult.Pending;
            assertThat(wasKeptPending).as("request %d", index).isEqualTo(ballLive);
            askedWithTheBallLive += ballLive ? 1 : 0;
            askedWithTheBallStill += ballLive ? 0 : 1;
        }
        assertThat(askedWithTheBallLive).isPositive();
        assertThat(askedWithTheBallStill).isPositive();
    }

    @Test
    void thePreviewIsUsableWhileDecidingAndDoesNotChangeTheMatch() {
        PreparedMatch base = preparedMatch();
        PreviewBothStyles previewBothStyles = new PreviewBothStyles(MatchSide.HOME, base.home().style());
        ProbingCoach previewing = new ProbingCoach(previewBothStyles);
        ScriptedCoach awayCoach = new ScriptedCoach(MatchSide.AWAY, PlayStyle.BALANCED, 0);
        ScriptedCoach passiveHome = new ScriptedCoach(MatchSide.HOME, PlayStyle.BALANCED, 0);
        ScriptedCoach passiveAway = new ScriptedCoach(MatchSide.AWAY, PlayStyle.BALANCED, 0);

        List<MatchEvent> withThePreview = play(base, new RecordingObserver(), previewing, awayCoach);
        List<MatchEvent> withoutThePreview = play(base, new RecordingObserver(), passiveHome, passiveAway);

        PreviewVerdict alreadyInUse = PreviewVerdict.refused(RefusalReason.STYLE_ALREADY_IN_USE);
        assertThat(previewBothStyles.verdictsOfTheCurrentStyle()).isNotEmpty().containsOnly(alreadyInUse);
        assertThat(previewBothStyles.verdictsOfAnotherStyle()).isNotEmpty().containsOnly(PreviewVerdict.accepted());
        assertThat(withThePreview).isEqualTo(withoutThePreview);
    }

    @Test
    void theRequestsOfTheSameCoachAreAppliedInOrderSoTheSecondCanUseTheEffectOfTheFirst() {
        PreparedMatch base = preparedMatch();
        ScriptedCoach homeCoach = new ScriptedCoach(FIRST_DELIVERY, LiveMatchCoachesTest::substitutionThenShift);
        ScriptedCoach awayCoach = new ScriptedCoach(MatchSide.AWAY, PlayStyle.BALANCED, 0);
        RecordingObserver observer = new RecordingObserver();

        play(base, observer, homeCoach, awayCoach);

        List<InterventionResult> executedBoth = List.of(InterventionResult.executed(),
                InterventionResult.executed());
        assertThat(homeCoach.settledResults()).containsExactly(executedBoth);
        List<EventType> typesOfTheSecondDelivery = observer.deliveries().get(1).stream()
                .map(MatchEvent::type)
                .toList();
        assertThat(typesOfTheSecondDelivery).containsSubsequence(EventType.SUBSTITUTION, EventType.PLAYER_MOVED);
        assertThat(awayCoach.settledResults()).isEmpty();
    }

    @Test
    void eachCoachReceivesTheResultsOfItsOwnRequestsIncludingARefusal() {
        PreparedMatch base = preparedMatch();
        PlayStyle homeNewStyle = styleOtherThan(base.home().style());
        ScriptedCoach homeCoach = new ScriptedCoach(MatchSide.HOME, homeNewStyle, FIRST_DELIVERY);
        ScriptedCoach awayCoach = new ScriptedCoach(MatchSide.AWAY, base.away().style(), FIRST_DELIVERY);

        play(base, new RecordingObserver(), homeCoach, awayCoach);

        InterventionResult refused = InterventionResult.refused(RefusalReason.STYLE_ALREADY_IN_USE);
        assertThat(homeCoach.settledResults()).containsExactly(List.of(InterventionResult.executed()));
        assertThat(awayCoach.settledResults()).containsExactly(List.of(refused));
        assertThat(homeCoach.settledRequests()).hasSize(1);
        assertThat(homeCoach.settledRequests().get(0)).allMatch(request -> request.side() == MatchSide.HOME);
        assertThat(awayCoach.settledRequests()).hasSize(1);
        assertThat(awayCoach.settledRequests().get(0)).allMatch(request -> request.side() == MatchSide.AWAY);
    }

    private static List<Intervention> substitutionThenShift(MatchSide side, Team own) {
        Player leaving = firstOf(own.starters(), Role.MIDFIELDER);
        Player entering = firstOf(own.bench(), Role.MIDFIELDER);
        Intervention substitution = new Intervention.VoluntarySubstitution(side, leaving, entering,
                ChangeReason.CHASE_GAME);
        Intervention.SectorShift shift = new Intervention.SectorShift(entering, Role.FORWARD);
        Intervention formationChange = new Intervention.FormationChange(side, List.of(shift),
                ChangeReason.CHASE_GAME);
        return List.of(substitution, formationChange);
    }

    private static Player firstOf(List<Player> players, Role role) {
        return players.stream()
                .filter(player -> player.role() == role)
                .findFirst()
                .orElseThrow();
    }

    private static PlayStyle styleOtherThan(PlayStyle style) {
        return style == PlayStyle.BALANCED ? PlayStyle.ATTACKING : PlayStyle.BALANCED;
    }

    private PreparedMatch preparedMatch() {
        return ReferenceChronicle.prepare(ReferenceChronicle.homeTeamFile(), ReferenceChronicle.awayTeamFile());
    }

    private List<MatchEvent> play(PreparedMatch prepared, MatchObserver observer, Coach home, Coach away) {
        MatchCoaches coaches = new MatchCoaches(home, away);
        PreparedMatch withTheScriptedCoaches = new PreparedMatch(prepared.homeDecision(),
                prepared.awayDecision(), prepared.names(), coaches);
        LiveMatch liveMatch = new LiveMatch(new MatchEngine(EngineConfig.defaults()));
        return liveMatch.play(withTheScriptedCoaches, SEED, observer);
    }

    private static final class ScriptedCoach implements Coach {

        private final int deliveryToAskAt;
        private final BiFunction<MatchSide, Team, List<Intervention>> plan;
        private final List<List<MatchEvent>> deliveries = new ArrayList<>();
        private final List<List<Intervention>> settledRequests = new ArrayList<>();
        private final List<List<InterventionResult>> settledResults = new ArrayList<>();
        private List<Intervention> requestsToAsk = List.of();
        private boolean open;
        private double firstDraw;

        ScriptedCoach(MatchSide side, PlayStyle styleToAskFor, int deliveryToAskAt) {
            this(deliveryToAskAt, (ownSide, own) -> List.of(new Intervention.StyleChange(side, styleToAskFor,
                    ChangeReason.CHASE_GAME)));
        }

        ScriptedCoach(int deliveryToAskAt, BiFunction<MatchSide, Team, List<Intervention>> plan) {
            this.deliveryToAskAt = deliveryToAskAt;
            this.plan = plan;
        }

        @Override
        public void open(MatchSide ownSide, PreMatchDecision own, Team opponent, TieBreakDraw draw) {
            open = true;
            firstDraw = draw.nextDouble();
            requestsToAsk = plan.apply(ownSide, own.team());
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public List<Intervention> decide(List<MatchEvent> newEvents, MatchMoment moment) {
            assertThat(open).isTrue();
            deliveries.add(newEvents);
            if (deliveries.size() != deliveryToAskAt) {
                return List.of();
            }
            return requestsToAsk;
        }

        @Override
        public void interventionsSettled(List<Intervention> requests, List<InterventionResult> results) {
            settledRequests.add(requests);
            settledResults.add(results);
        }

        List<List<MatchEvent>> deliveries() {
            return deliveries;
        }

        List<List<Intervention>> settledRequests() {
            return settledRequests;
        }

        List<List<InterventionResult>> settledResults() {
            return settledResults;
        }

        double firstDraw() {
            return firstDraw;
        }
    }

    @FunctionalInterface
    private interface Probe {

        List<Intervention> requestsAt(MatchMoment moment);
    }

    private static final class StyleToggle implements Probe {

        private final MatchSide side;
        private final PlayStyle initialStyle;
        private int requests;

        StyleToggle(MatchSide side, PlayStyle initialStyle) {
            this.side = side;
            this.initialStyle = initialStyle;
        }

        @Override
        public List<Intervention> requestsAt(MatchMoment moment) {
            requests++;
            PlayStyle style = requests % 2 == 1 ? styleOtherThan(initialStyle) : initialStyle;
            return List.of(new Intervention.StyleChange(side, style, ChangeReason.CHASE_GAME));
        }
    }

    private static final class PreviewBothStyles implements Probe {

        private final MatchSide side;
        private final PlayStyle currentStyle;
        private final List<PreviewVerdict> verdictsOfTheCurrentStyle = new ArrayList<>();
        private final List<PreviewVerdict> verdictsOfAnotherStyle = new ArrayList<>();

        PreviewBothStyles(MatchSide side, PlayStyle currentStyle) {
            this.side = side;
            this.currentStyle = currentStyle;
        }

        @Override
        public List<Intervention> requestsAt(MatchMoment moment) {
            Intervention sameStyle = new Intervention.StyleChange(side, currentStyle, ChangeReason.CHASE_GAME);
            Intervention otherStyle = new Intervention.StyleChange(side, styleOtherThan(currentStyle),
                    ChangeReason.CHASE_GAME);
            verdictsOfTheCurrentStyle.addAll(moment.preview().preview(List.of(sameStyle)));
            verdictsOfAnotherStyle.addAll(moment.preview().preview(List.of(otherStyle)));
            return List.of();
        }

        List<PreviewVerdict> verdictsOfTheCurrentStyle() {
            return verdictsOfTheCurrentStyle;
        }

        List<PreviewVerdict> verdictsOfAnotherStyle() {
            return verdictsOfAnotherStyle;
        }
    }

    private static final class ProbingCoach implements Coach {

        private final Probe probe;
        private final List<Boolean> ballLiveAtEachDecision = new ArrayList<>();
        private final List<List<InterventionResult>> settledResults = new ArrayList<>();

        ProbingCoach(Probe probe) {
            this.probe = probe;
        }

        @Override
        public void open(MatchSide ownSide, PreMatchDecision own, Team opponent, TieBreakDraw draw) {
        }

        @Override
        public void close() {
        }

        @Override
        public List<Intervention> decide(List<MatchEvent> newEvents, MatchMoment moment) {
            ballLiveAtEachDecision.add(moment.ballLive());
            return probe.requestsAt(moment);
        }

        @Override
        public void interventionsSettled(List<Intervention> requests, List<InterventionResult> results) {
            settledResults.add(results);
        }

        List<Boolean> ballLiveAtEachDecision() {
            return ballLiveAtEachDecision;
        }

        List<List<InterventionResult>> settledResults() {
            return settledResults;
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
