package org.tasktelemetry.matchsimulator.narration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_DEFENDER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_FORWARD;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_GOALKEEPER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_BENCH;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_DEFENDER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_FORWARD;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_MIDFIELDER;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.FoulSeverity;
import org.tasktelemetry.matchsimulator.engine.event.GoalContext;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.event.SaveQuality;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.event.ShotProfile;
import org.tasktelemetry.matchsimulator.engine.event.ShotQuality;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.team.Player;

class NarratedEventFilterTest {

    private static final EventDetail NO_DETAIL = EventDetail.none();

    private final NarratedEventFilter filter = new NarratedEventFilter();
    private final NarrationEvents fixture = new NarrationEvents();
    private final List<MatchEvent> expected = new ArrayList<>();

    @Test
    void theDefaultChronicleContainsAllAndOnlyTheEventsOfTheList() {
        buildMatchWithOneEventForEveryRuleAndOneForEveryExclusion();

        List<MatchEvent> selected = filter.select(fixture.list(), ChronicleMode.DEFAULT);

        assertThat(selected).containsExactlyElementsOf(expected);
    }

    @Test
    void theFullChronicleContainsEveryEventInOrder() {
        buildMatchWithOneEventForEveryRuleAndOneForEveryExclusion();

        List<MatchEvent> selected = filter.select(fixture.list(), ChronicleMode.FULL);

        assertThat(selected).containsExactlyElementsOf(fixture.list());
        assertThat(selected).hasSizeGreaterThan(expected.size());
    }

    @Test
    void theKickOffAfterAGoalIsNotConfusedWithTheKickOffOfAHalf() {
        MatchEvent halfKickOff = fixture.add(EventType.KICK_OFF, MatchSide.HOME, ZoneRole.MIDFIELD,
                Participants.nobody(), NO_DETAIL);
        fixture.add(EventType.KICK_OFF_AFTER_GOAL, MatchSide.AWAY, ZoneRole.MIDFIELD, Participants.nobody(),
                NO_DETAIL);

        assertThat(filter.select(fixture.list(), ChronicleMode.DEFAULT)).containsExactly(halfKickOff);
    }

    @ParameterizedTest
    @EnumSource(value = EventType.class, names = {"KICK_OFF", "KICK_OFF_AFTER_GOAL"})
    void aKickOffDoesNotPrepareAShotAndStopsTheSearchBackwards(EventType kickOff) {
        MatchEvent pass = fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        fixture.add(kickOff, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.nobody(), NO_DETAIL);
        MatchEvent dribble = fixture.add(EventType.DRIBBLE_COMPLETED, MatchSide.HOME, HOME_FORWARD);
        MatchEvent shot = shotEvent(EventType.SHOT_OFF_TARGET, MatchSide.HOME, HOME_FORWARD);

        List<MatchEvent> selected = filter.select(fixture.list(), ChronicleMode.DEFAULT);

        assertThat(selected).doesNotContain(pass).contains(dribble, shot);
        assertThat(selected).extracting(MatchEvent::type).doesNotContain(EventType.KICK_OFF_AFTER_GOAL);
    }

    @Test
    void aFoulFollowedByACardForAnotherPlayerIsNotNarratedWhileTheCardIs() {
        fixture.add(EventType.FOUL, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.of(AWAY_DEFENDER, HOME_FORWARD),
                EventDetail.foul(FoulSeverity.LIGHT, false));
        MatchEvent card = fixture.add(EventType.YELLOW_CARD, MatchSide.AWAY, AWAY_FORWARD);

        assertThat(filter.select(fixture.list(), ChronicleMode.DEFAULT)).containsExactly(card);
    }

    @Test
    void aFoulWithAPenaltyIsNarratedEvenWithoutACard() {
        MatchEvent foul = fixture.add(EventType.FOUL, MatchSide.HOME, ZoneRole.OPPONENT_BOX,
                Participants.of(AWAY_DEFENDER, HOME_FORWARD), EventDetail.foul(FoulSeverity.HARD, true));

        assertThat(filter.select(fixture.list(), ChronicleMode.DEFAULT)).containsExactly(foul);
    }

    @Test
    void theEventsBeforeAShotCountOnlyWhenTheSideThatPlayedThemIsTheSideOfTheShot() {
        fixture.add(EventType.PASS_COMPLETED, MatchSide.AWAY, AWAY_DEFENDER);
        fixture.add(EventType.INTERCEPTION, MatchSide.AWAY, HOME_DEFENDER);
        MatchEvent dribble = fixture.add(EventType.DRIBBLE_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        MatchEvent shot = shotEvent(EventType.SHOT_OFF_TARGET, MatchSide.HOME, HOME_FORWARD);

        List<MatchEvent> selected = filter.select(fixture.list(), ChronicleMode.DEFAULT);

        assertThat(selected).extracting(MatchEvent::type)
                .containsExactly(EventType.INTERCEPTION, EventType.DRIBBLE_COMPLETED, EventType.SHOT_OFF_TARGET);
        assertThat(selected).contains(dribble, shot);
    }

    @Test
    void theBallCirculationIsNarratedOnlyWhenItPreparesAShot() {
        fixture.add(EventType.BALL_CIRCULATED, MatchSide.HOME, HOME_MIDFIELDER);
        fixture.add(EventType.PASS_COMPLETED, MatchSide.AWAY, AWAY_DEFENDER);
        MatchEvent circulation = fixture.add(EventType.BALL_CIRCULATED, MatchSide.HOME, HOME_MIDFIELDER);
        MatchEvent shot = shotEvent(EventType.SHOT_OFF_TARGET, MatchSide.HOME, HOME_FORWARD);

        List<MatchEvent> selected = filter.select(fixture.list(), ChronicleMode.DEFAULT);

        assertThat(selected).containsExactly(circulation, shot);
    }

    private void buildMatchWithOneEventForEveryRuleAndOneForEveryExclusion() {
        addMatchFlowAndRestarts();
        addPlayThatIsNotNarrated();
        addShotWithTwoPreparingEvents();
        addShotAfterTheOtherSide();
        addCounterAttackGoal();
        addPenaltyAndCards();
        addSetPieces();
        addIncidents();
        addEndOfTheHalvesAndSuspension();
    }

    private void addMatchFlowAndRestarts() {
        included(EventType.KICK_OFF, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.nobody(), NO_DETAIL);
        excluded(EventType.KICK_OFF_AFTER_GOAL, MatchSide.AWAY, ZoneRole.MIDFIELD, Participants.nobody(), NO_DETAIL);
    }

    private void addPlayThatIsNotNarrated() {
        excluded(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        excluded(EventType.DRIBBLE_COMPLETED, MatchSide.HOME, HOME_FORWARD);
        excluded(EventType.INTERCEPTION, MatchSide.HOME, AWAY_DEFENDER);
        excluded(EventType.TACKLE, MatchSide.AWAY, HOME_DEFENDER);
        excluded(EventType.GOAL_KICK, MatchSide.AWAY, AWAY_GOALKEEPER);
        excluded(EventType.FREE_KICK, MatchSide.HOME, ZoneRole.DEFENCE, Participants.of(HOME_MIDFIELDER), NO_DETAIL);
        excluded(EventType.OFFSIDE_FREE_KICK, MatchSide.AWAY, ZoneRole.DEFENCE, Participants.of(AWAY_DEFENDER),
                NO_DETAIL);
        excluded(EventType.BALL_CIRCULATED, MatchSide.HOME, HOME_MIDFIELDER);
        excluded(EventType.CROSS_FAILED, MatchSide.HOME, HOME_MIDFIELDER);
        excluded(EventType.CROSS_DEFLECTED_TO_CORNER, MatchSide.HOME, ZoneRole.ATTACKING_THIRD,
                Participants.of(AWAY_DEFENDER, HOME_MIDFIELDER), NO_DETAIL);
        excluded(EventType.CLEARANCE, MatchSide.HOME, ZoneRole.OPPONENT_BOX,
                Participants.of(AWAY_DEFENDER, HOME_MIDFIELDER), NO_DETAIL);
        excluded(EventType.CROSS_COMPLETED, MatchSide.AWAY, AWAY_FORWARD);
        excluded(EventType.FOUL, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.of(AWAY_DEFENDER, HOME_FORWARD),
                EventDetail.foul(FoulSeverity.LIGHT, false));
        excluded(EventType.FREE_KICK_CROSS, MatchSide.HOME, ZoneRole.DEFENCE, Participants.of(HOME_MIDFIELDER),
                NO_DETAIL);
    }

    private void addShotWithTwoPreparingEvents() {
        excluded(EventType.PASS_COMPLETED, MatchSide.AWAY, AWAY_DEFENDER);
        excluded(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_DEFENDER);
        included(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        included(EventType.CROSS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        included(shotEvent(EventType.SHOT_OFF_TARGET, MatchSide.HOME, HOME_FORWARD));
    }

    private void addShotAfterTheOtherSide() {
        excluded(EventType.PASS_COMPLETED, MatchSide.AWAY, AWAY_DEFENDER);
        included(shotEvent(EventType.SHOT_BLOCKED, MatchSide.HOME, HOME_FORWARD));
    }

    private void addCounterAttackGoal() {
        excluded(EventType.PASS_COMPLETED, MatchSide.AWAY, AWAY_DEFENDER);
        included(EventType.INTERCEPTION, MatchSide.AWAY, HOME_MIDFIELDER);
        included(EventType.DRIBBLE_COMPLETED, MatchSide.HOME, HOME_FORWARD);
        EventDetail detail = NarrationEvents.goalDetail(GoalContext.TAKES_THE_LEAD);
        included(EventType.GOAL, MatchSide.HOME, ZoneRole.OPPONENT_BOX, Participants.of(HOME_FORWARD), detail);
        excluded(EventType.KICK_OFF_AFTER_GOAL, MatchSide.AWAY, ZoneRole.MIDFIELD, Participants.nobody(), NO_DETAIL);
    }

    private void addPenaltyAndCards() {
        Participants foulParticipants = Participants.of(HOME_DEFENDER, AWAY_FORWARD);
        included(EventType.FOUL, MatchSide.AWAY, ZoneRole.OPPONENT_BOX, foulParticipants,
                EventDetail.foul(FoulSeverity.RECKLESS, true));
        ShotProfile penalty = ShotProfile.ofPenalty(0.79, ShotQuality.BIG_CHANCE);
        EventDetail save = EventDetail.save(penalty, SaveQuality.DIFFICULT);
        included(EventType.SHOT_SAVED, MatchSide.AWAY, ZoneRole.OPPONENT_BOX,
                Participants.of(AWAY_FORWARD, AWAY_GOALKEEPER), save);
        excluded(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        included(EventType.FOUL, MatchSide.AWAY, ZoneRole.MIDFIELD, foulParticipants,
                EventDetail.foul(FoulSeverity.HARD, false));
        included(EventType.YELLOW_CARD, MatchSide.HOME, HOME_DEFENDER);
        included(EventType.SECOND_YELLOW_CARD, MatchSide.HOME, HOME_MIDFIELDER);
        included(EventType.RED_CARD, MatchSide.AWAY, AWAY_DEFENDER);
    }

    private void addSetPieces() {
        included(EventType.CORNER_KICK, MatchSide.HOME, ZoneRole.OPPONENT_BOX, Participants.of(HOME_MIDFIELDER),
                NO_DETAIL);
        excluded(EventType.PASS_COMPLETED, MatchSide.AWAY, AWAY_DEFENDER);
        included(EventType.FREE_KICK_CROSS, MatchSide.HOME, ZoneRole.ATTACKING_THIRD,
                Participants.of(HOME_MIDFIELDER), NO_DETAIL);
        included(EventType.FREE_KICK, MatchSide.HOME, ZoneRole.ATTACKING_THIRD, Participants.of(HOME_MIDFIELDER),
                NO_DETAIL);
    }

    private void addIncidents() {
        included(EventType.OFFSIDE, MatchSide.HOME, ZoneRole.ATTACKING_THIRD,
                Participants.of(HOME_FORWARD, HOME_MIDFIELDER), NO_DETAIL);
        included(EventType.INJURY, MatchSide.AWAY, AWAY_DEFENDER);
        included(EventType.SUBSTITUTION, MatchSide.HOME, ZoneRole.MIDFIELD,
                Participants.of(HOME_MIDFIELDER, HOME_BENCH), NO_DETAIL);
    }

    private void addEndOfTheHalvesAndSuspension() {
        included(EventType.STOPPAGE_ANNOUNCED, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.nobody(),
                EventDetail.stoppageAnnounced(2));
        included(EventType.FIRST_HALF_END, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.nobody(), NO_DETAIL);
        included(EventType.SECOND_HALF_START, MatchSide.AWAY, ZoneRole.MIDFIELD, Participants.nobody(), NO_DETAIL);
        EventDetail suspension = EventDetail.suspension(MatchSide.HOME, new Score(0, 1), new Score(0, 3));
        included(EventType.MATCH_SUSPENDED, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.nobody(), suspension);
        included(EventType.MATCH_END, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.nobody(), NO_DETAIL);
    }

    private MatchEvent shotEvent(EventType type, MatchSide side, Player shooter) {
        ShotProfile profile = ShotProfile.ofShot(0.2, ShotKind.FOOT, ShotQuality.HALF_CHANCE);
        return fixture.add(type, side, ZoneRole.OPPONENT_BOX, Participants.of(shooter), EventDetail.shot(profile));
    }

    private void included(MatchEvent event) {
        expected.add(event);
    }

    private void included(EventType type, MatchSide side, Player primary) {
        expected.add(fixture.add(type, side, primary));
    }

    private void included(EventType type, MatchSide side, ZoneRole zoneRole, Participants participants,
            EventDetail detail) {
        expected.add(fixture.add(type, side, zoneRole, participants, detail));
    }

    private void excluded(EventType type, MatchSide side, Player primary) {
        fixture.add(type, side, primary);
    }

    private void excluded(EventType type, MatchSide side, ZoneRole zoneRole, Participants participants,
            EventDetail detail) {
        fixture.add(type, side, zoneRole, participants, detail);
    }
}
