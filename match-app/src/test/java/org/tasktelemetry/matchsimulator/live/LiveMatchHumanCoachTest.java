package org.tasktelemetry.matchsimulator.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.tasktelemetry.matchsimulator.coach.Coach;
import org.tasktelemetry.matchsimulator.coach.MatchMoment;
import org.tasktelemetry.matchsimulator.coach.TieBreakDraw;
import org.tasktelemetry.matchsimulator.coach.human.DugoutSession;
import org.tasktelemetry.matchsimulator.coach.human.PlannedMoves;
import org.tasktelemetry.matchsimulator.coach.human.SheetPlayer;
import org.tasktelemetry.matchsimulator.coach.human.TeamSheet;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.intervention.PreviewVerdict;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;
import org.tasktelemetry.matchsimulator.narration.ReferenceChronicle;

class LiveMatchHumanCoachTest {

    private static final long SEED = ReferenceChronicle.SEED;
    private static final int EARLY_DECISION = 4;

    private final MatchPreparation preparation = MatchPreparation.standard();
    private final LiveMatch liveMatch = new LiveMatch(new MatchEngine(EngineConfig.defaults()));

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void withoutAnyEnterTheMatchIsEventForEventTheOneWithAnIdleCoachForThatSide(MatchSide side) {
        ScriptedSession session = new ScriptedSession();

        Run controlled = playControlled(side, session, index -> { });
        List<MatchEvent> withTheIdleCoach = playWithAnIdleCoach(side);

        assertThat(controlled.events()).isNotEmpty().isEqualTo(withTheIdleCoach);
        assertThat(session.opens()).isZero();
        assertThat(controlled.events()).noneMatch(this::isDecidedByTheManager);
    }

    @Test
    void aPauseRequestedWithTheBallLiveOpensAtTheFirstDeadBallMomentAndNeverWithTheBallLive() {
        Run probe = playControlled(MatchSide.HOME, new ScriptedSession(), index -> { });
        int firstLiveBall = probe.coach().ballLiveAtEachDecision().indexOf(true);
        assertThat(firstLiveBall).as("a moment with the ball live exists in this match").isNotNegative();
        ScriptedSession session = new ScriptedSession();

        Run run = playControlled(MatchSide.HOME, session, index -> requestPauseAt(index, firstLiveBall, session));

        List<Boolean> opened = run.coach().openedAtEachDecision();
        List<Boolean> ballLive = run.coach().ballLiveAtEachDecision();
        int openingMoment = opened.indexOf(true);
        assertThat(session.opens()).isEqualTo(1);
        assertThat(opened.get(firstLiveBall)).isFalse();
        assertThat(openingMoment).isGreaterThan(firstLiveBall);
        assertThat(ballLive.get(openingMoment)).isFalse();
        assertThat(ballLive.subList(firstLiveBall, openingMoment)).containsOnly(true);
    }

    @Test
    void severalRequestsBeforeTheOpeningAreOneScreenOnly() {
        ScriptedSession session = new ScriptedSession();

        playControlled(MatchSide.AWAY, session, index -> requestThreeTimesAt(index, EARLY_DECISION, session));

        assertThat(session.opens()).isEqualTo(1);
    }

    @Test
    void aPauseRequestedBeforeTheKickOffOpensAfterTheKickOff() {
        ScriptedSession session = new ScriptedSession();
        session.requestPause();

        Run run = playControlled(MatchSide.HOME, session, index -> { });

        int openingMoment = run.coach().openedAtEachDecision().indexOf(true);
        List<MatchEvent> deliveredByThen = run.coach().eventsDeliveredUpTo(openingMoment);
        assertThat(session.opens()).isEqualTo(1);
        assertThat(deliveredByThen).extracting(MatchEvent::type).contains(EventType.KICK_OFF);
        assertThat(session.sheetsSeen().get(0).time()).isNotNull();
    }

    @Test
    void aPauseRequestedWhenTheMatchIsFinishedOpensNothing() {
        ScriptedSession session = new ScriptedSession();
        MatchObserver requestingAtTheEnd = new MatchObserver() {
            @Override
            public void eventsHappened(List<MatchEvent> newEvents) {
            }

            @Override
            public void matchFinished() {
                session.requestPause();
            }
        };

        playControlled(MatchSide.HOME, session, index -> { }, requestingAtTheEnd);

        assertThat(session.opens()).isZero();
        assertThat(session.isPauseRequested()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void aPauseWithoutMovesLeavesTheMatchUnchangedEvenWhenTheEngineRefusedMovesDuringIt(MatchSide side) {
        ScriptedSession session = new ScriptedSession();
        session.script((sheet, moves) -> tryMovesTheEngineRefuses(session, sheet, moves));

        Run controlled = playControlled(side, session, index -> requestPauseAt(index, EARLY_DECISION, session));
        List<MatchEvent> withTheIdleCoach = playWithAnIdleCoach(side);

        assertThat(session.opens()).isEqualTo(1);
        assertThat(controlled.events()).isEqualTo(withTheIdleCoach);
        assertThat(session.outcomes()).isEmpty();
    }

    @Test
    void theGoalkeeperLeavingForAMidfielderAndTheReserveGoalkeeperEnteringForAnOutfieldPlayerAreRefusedOnTheRealMatch() {
        ScriptedSession session = new ScriptedSession();
        session.script((sheet, moves) -> tryMovesTheEngineRefuses(session, sheet, moves));

        playControlled(MatchSide.HOME, session, index -> requestPauseAt(index, EARLY_DECISION, session));

        PreviewVerdict goalkeeperNotCovered = PreviewVerdict.refused(RefusalReason.GOALKEEPER_NOT_COVERED);
        assertThat(session.verdicts()).containsExactly(goalkeeperNotCovered, goalkeeperNotCovered);
    }

    @Test
    void afterTheEndOfTheInputNoScreenOpensAnyMoreAndTheMatchGoesOnUnchanged() {
        ScriptedSession session = new ScriptedSession();
        session.script((sheet, moves) -> endTheInputWithoutMoves(session, moves));

        Run controlled = playControlled(MatchSide.HOME, session, index -> session.requestPause());
        List<MatchEvent> withTheIdleCoach = playWithAnIdleCoach(MatchSide.HOME);

        assertThat(session.opens()).isEqualTo(1);
        assertThat(controlled.events()).isEqualTo(withTheIdleCoach);
    }

    @Test
    void theAcceptedMovesAreExecutedRightAfterThePauseWithTheHumanReasonAndTheOutcomesGoBackToTheSession() {
        ScriptedSession session = new ScriptedSession();
        session.script((sheet, moves) -> submitSubstitutionAndStyle(sheet, moves));

        Run run = playControlled(MatchSide.HOME, session, index -> requestPauseAt(index, EARLY_DECISION, session));

        List<MatchEvent> humanDecisions = run.events().stream()
                .filter(this::isDecidedByTheManager)
                .toList();
        assertThat(humanDecisions).extracting(MatchEvent::type)
                .containsExactly(EventType.SUBSTITUTION, EventType.STYLE_CHANGED);
        assertThat(humanDecisions).extracting(MatchEvent::side).containsOnly(MatchSide.HOME);
        assertThat(session.outcomes()).containsExactly(InterventionResult.executed(), InterventionResult.executed());
    }

    @Test
    void theSameMovesAtTheSameMomentsGiveTheSameMatchWhichIsNotTheOneWithoutThem() {
        ScriptedSession first = new ScriptedSession();
        first.script((sheet, moves) -> submitSubstitutionAndStyle(sheet, moves));
        ScriptedSession second = new ScriptedSession();
        second.script((sheet, moves) -> submitSubstitutionAndStyle(sheet, moves));

        Run firstRun = playControlled(MatchSide.AWAY, first, index -> requestPauseAt(index, EARLY_DECISION, first));
        Run secondRun = playControlled(MatchSide.AWAY, second,
                index -> requestPauseAt(index, EARLY_DECISION, second));
        List<MatchEvent> withTheIdleCoach = playWithAnIdleCoach(MatchSide.AWAY);

        assertThat(firstRun.events()).isEqualTo(secondRun.events());
        assertThat(firstRun.events()).isNotEqualTo(withTheIdleCoach);
    }

    private List<Intervention> tryMovesTheEngineRefuses(ScriptedSession session, TeamSheet sheet,
            PlannedMoves moves) {
        SheetPlayer goalkeeper = onPitch(sheet, Role.GOALKEEPER);
        SheetPlayer benchMidfielder = onBench(sheet, Role.MIDFIELDER);
        SheetPlayer reserveGoalkeeper = onBench(sheet, Role.GOALKEEPER);
        SheetPlayer defender = onPitch(sheet, Role.DEFENDER);
        Intervention goalkeeperForMidfielder = moves.substitution(goalkeeper.player(), benchMidfielder.player());
        session.recordVerdict(moves.submit(goalkeeperForMidfielder));
        Intervention reserveForDefender = moves.substitution(defender.player(), reserveGoalkeeper.player());
        session.recordVerdict(moves.submit(reserveForDefender));
        return moves.accepted();
    }

    private List<Intervention> endTheInputWithoutMoves(ScriptedSession session, PlannedMoves moves) {
        session.endInput();
        return moves.accepted();
    }

    private List<Intervention> submitSubstitutionAndStyle(TeamSheet sheet, PlannedMoves moves) {
        SheetPlayer leaving = onPitch(sheet, Role.DEFENDER);
        SheetPlayer entering = onBench(sheet, Role.DEFENDER);
        PlayStyle currentStyle = sheet.style();
        PlayStyle newStyle = currentStyle == PlayStyle.ATTACKING ? PlayStyle.DEFENSIVE : PlayStyle.ATTACKING;
        Intervention substitution = moves.substitution(leaving.player(), entering.player());
        Intervention styleChange = moves.styleChange(newStyle);
        moves.submit(substitution);
        moves.submit(styleChange);
        return moves.accepted();
    }

    private SheetPlayer onPitch(TeamSheet sheet, Role role) {
        return sheet.players().stream()
                .filter(SheetPlayer::isOnPitch)
                .filter(player -> player.player().role() == role)
                .findFirst()
                .orElseThrow();
    }

    private SheetPlayer onBench(TeamSheet sheet, Role role) {
        return sheet.players().stream()
                .filter(SheetPlayer::isAvailableOnBench)
                .filter(player -> player.player().role() == role)
                .findFirst()
                .orElseThrow();
    }

    private boolean isDecidedByTheManager(MatchEvent event) {
        return event.detail() instanceof EventDetail.LineupChange change
                && change.reason() == ChangeReason.MANAGER_DECISION
                || event.detail() instanceof EventDetail.StyleChange styleChange
                        && styleChange.reason() == ChangeReason.MANAGER_DECISION;
    }

    private void requestPauseAt(int decision, int requestedAt, ScriptedSession session) {
        if (decision == requestedAt) {
            session.requestPause();
        }
    }

    private void requestThreeTimesAt(int decision, int requestedAt, ScriptedSession session) {
        if (decision == requestedAt) {
            session.requestPause();
            session.requestPause();
            session.requestPause();
        }
    }

    private PreparedMatch freshMatch() {
        return ReferenceChronicle.prepare(ReferenceChronicle.homeTeamFile(), ReferenceChronicle.awayTeamFile());
    }

    private List<MatchEvent> playWithAnIdleCoach(MatchSide side) {
        PreparedMatch match = freshMatch().withCoach(side, new IdleCoach());
        return liveMatch.play(match, SEED, new SilentObserver());
    }

    private Run playControlled(MatchSide side, ScriptedSession session, IntConsumer beforeEachDecision) {
        return playControlled(side, session, beforeEachDecision, new SilentObserver());
    }

    private Run playControlled(MatchSide side, ScriptedSession session, IntConsumer beforeEachDecision,
            MatchObserver observer) {
        PreparedMatch handedOver = preparation.handControlTo(freshMatch(), side, session);
        Coach humanCoach = coachOf(handedOver, side);
        WatchedCoach watched = new WatchedCoach(humanCoach, session, beforeEachDecision);
        PreparedMatch match = handedOver.withCoach(side, watched);
        List<MatchEvent> events = liveMatch.play(match, SEED, observer);
        return new Run(events, watched);
    }

    private Coach coachOf(PreparedMatch match, MatchSide side) {
        if (side == MatchSide.HOME) {
            return match.coaches().home();
        }
        return match.coaches().away();
    }

    private record Run(List<MatchEvent> events, WatchedCoach coach) {
    }

    private static final class IdleCoach implements Coach {

        @Override
        public void open(MatchSide side, PreMatchDecision own, Team opponent, TieBreakDraw draw) {
        }

        @Override
        public List<Intervention> decide(List<MatchEvent> newEvents, MatchMoment moment) {
            return List.of();
        }

        @Override
        public void close() {
        }
    }

    private static final class WatchedCoach implements Coach {

        private final Coach delegate;
        private final ScriptedSession session;
        private final IntConsumer beforeEachDecision;
        private final List<Boolean> ballLiveAtEachDecision = new ArrayList<>();
        private final List<Boolean> openedAtEachDecision = new ArrayList<>();
        private final List<List<MatchEvent>> deliveries = new ArrayList<>();

        WatchedCoach(Coach delegate, ScriptedSession session, IntConsumer beforeEachDecision) {
            this.delegate = delegate;
            this.session = session;
            this.beforeEachDecision = beforeEachDecision;
        }

        @Override
        public void open(MatchSide side, PreMatchDecision own, Team opponent, TieBreakDraw draw) {
            delegate.open(side, own, opponent, draw);
        }

        @Override
        public List<Intervention> decide(List<MatchEvent> newEvents, MatchMoment moment) {
            int decision = deliveries.size();
            deliveries.add(newEvents);
            beforeEachDecision.accept(decision);
            int opensBefore = session.opens();
            List<Intervention> requests = delegate.decide(newEvents, moment);
            ballLiveAtEachDecision.add(moment.ballLive());
            openedAtEachDecision.add(session.opens() > opensBefore);
            return requests;
        }

        @Override
        public void interventionsSettled(List<Intervention> requests, List<InterventionResult> results) {
            delegate.interventionsSettled(requests, results);
        }

        @Override
        public void close() {
            delegate.close();
        }

        List<Boolean> ballLiveAtEachDecision() {
            return ballLiveAtEachDecision;
        }

        List<Boolean> openedAtEachDecision() {
            return openedAtEachDecision;
        }

        List<MatchEvent> eventsDeliveredUpTo(int decision) {
            return deliveries.subList(0, decision + 1).stream()
                    .flatMap(List::stream)
                    .toList();
        }
    }

    private static final class ScriptedSession implements DugoutSession {

        private final List<TeamSheet> sheetsSeen = new ArrayList<>();
        private final List<PreviewVerdict> verdicts = new ArrayList<>();
        private final List<InterventionResult> outcomes = new ArrayList<>();
        private boolean pauseRequested;
        private boolean inputEnded;
        private BiFunction<TeamSheet, PlannedMoves, List<Intervention>> script =
                (sheet, moves) -> moves.accepted();

        void script(BiFunction<TeamSheet, PlannedMoves, List<Intervention>> newScript) {
            script = newScript;
        }

        void requestPause() {
            pauseRequested = true;
        }

        void endInput() {
            inputEnded = true;
        }

        void recordVerdict(PreviewVerdict verdict) {
            verdicts.add(verdict);
        }

        int opens() {
            return sheetsSeen.size();
        }

        List<TeamSheet> sheetsSeen() {
            return sheetsSeen;
        }

        List<PreviewVerdict> verdicts() {
            return verdicts;
        }

        List<InterventionResult> outcomes() {
            return outcomes;
        }

        @Override
        public boolean isPauseRequested() {
            return pauseRequested && !inputEnded;
        }

        @Override
        public void prepareForPause() {
        }

        @Override
        public List<Intervention> open(TeamSheet sheet, PlannedMoves plannedMoves) {
            sheetsSeen.add(sheet);
            pauseRequested = false;
            return script.apply(sheet, plannedMoves);
        }

        @Override
        public void showOutcomes(List<Intervention> moves, List<InterventionResult> results) {
            outcomes.addAll(results);
        }
    }
}
