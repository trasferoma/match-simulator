package org.tasktelemetry.matchsimulator.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.FoulSeverity;
import org.tasktelemetry.matchsimulator.engine.event.GoalContext;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.event.SaveQuality;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.event.ShotProfile;
import org.tasktelemetry.matchsimulator.engine.event.ShotQuality;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class MatchSummaryCalculatorTest {

    private static final double TOLERANCE = 1e-9;

    private final MatchSummaryCalculator calculator = new MatchSummaryCalculator();
    private final Player homeForward = player("Rossi", Role.FORWARD);
    private final Player homeMidfielder = player("Bianchi", Role.MIDFIELDER);
    private final Player awayForward = player("Verdi", Role.FORWARD);
    private final Player awayDefender = player("Neri", Role.DEFENDER);
    private final Player awayGoalkeeper = player("Gialli", Role.GOALKEEPER);
    private final List<MatchEvent> events = new ArrayList<>();

    @Test
    void anEmptyEventListGivesAGoallessSummaryWithoutPossession() {
        MatchSummary summary = calculator.summarize(events);

        assertThat(summary.fieldScore()).isEqualTo(Score.goalless());
        assertThat(summary.goals()).isEmpty();
        assertThat(summary.home().shots()).isZero();
        assertThat(summary.away().possessionSeconds()).isZero();
        assertThat(summary.possessionShareOf(MatchSide.HOME)).isZero();
    }

    @Test
    void theResultAndTheScorersWithTheirMinuteComeFromTheGoalEvents() {
        events.add(goal(MatchSide.HOME, homeForward, firstHalf(754), 0.30));
        events.add(goal(MatchSide.AWAY, awayForward, secondHalf(120), 0.10));
        events.add(goal(MatchSide.HOME, homeMidfielder, secondHalf(3000), 0.05));

        MatchSummary summary = calculator.summarize(events);

        assertThat(summary.fieldScore()).isEqualTo(new Score(2, 1));
        assertThat(summary.goals()).extracting(ScoredGoal::side)
                .containsExactly(MatchSide.HOME, MatchSide.AWAY, MatchSide.HOME);
        assertThat(summary.goals()).extracting(ScoredGoal::scorer)
                .containsExactly(homeForward, awayForward, homeMidfielder);
        assertThat(summary.goals()).extracting(goal -> goal.time().display())
                .containsExactly("12'", "47'", "90+5'");
    }

    @Test
    void goalsAndSavesAreShotsOnTargetWhileOffTargetAndBlockedShotsAreNot() {
        events.add(goal(MatchSide.HOME, homeForward, firstHalf(100), 0.30));
        events.add(save(MatchSide.HOME, homeForward, awayGoalkeeper, 0.20));
        events.add(shotEvent(EventType.SHOT_OFF_TARGET, MatchSide.HOME, homeForward, 0.10));
        events.add(shotEvent(EventType.SHOT_BLOCKED, MatchSide.HOME, homeMidfielder, 0.05));
        events.add(shotEvent(EventType.SHOT_OFF_TARGET, MatchSide.AWAY, awayForward, 0.08));

        MatchSummary summary = calculator.summarize(events);

        assertThat(summary.home().shots()).isEqualTo(4);
        assertThat(summary.home().shotsOnTarget()).isEqualTo(2);
        assertThat(summary.away().shots()).isEqualTo(1);
        assertThat(summary.away().shotsOnTarget()).isZero();
    }

    @Test
    void nonShotEventsAreNotCountedAsShots() {
        events.add(plainEvent(EventType.PASS_COMPLETED, MatchSide.HOME, homeMidfielder, 8));
        events.add(plainEvent(EventType.GOAL_KICK, MatchSide.AWAY, awayGoalkeeper, 5));

        MatchSummary summary = calculator.summarize(events);

        assertThat(summary.home().shots()).isZero();
        assertThat(summary.away().shots()).isZero();
        assertThat(summary.home().expectedGoals()).isZero();
    }

    @Test
    void expectedGoalsAreTheSumOfTheShotProfilesOfEachSideIncludingGoalsSavesAndPenalties() {
        events.add(goal(MatchSide.HOME, homeForward, firstHalf(100), 0.30));
        events.add(save(MatchSide.HOME, homeForward, awayGoalkeeper, 0.20));
        events.add(shotEvent(EventType.SHOT_BLOCKED, MatchSide.HOME, homeMidfielder, 0.05));
        events.add(penaltyGoal(MatchSide.HOME, homeForward, 0.79));
        events.add(shotEvent(EventType.SHOT_OFF_TARGET, MatchSide.AWAY, awayForward, 0.08));

        MatchSummary summary = calculator.summarize(events);

        assertThat(summary.home().expectedGoals()).isCloseTo(1.34, within(TOLERANCE));
        assertThat(summary.away().expectedGoals()).isCloseTo(0.08, within(TOLERANCE));
        assertThat(summary.home().shots()).isEqualTo(4);
    }

    @Test
    void possessionIsTheSumOfTheDurationsOfTheSideThatHadTheBall() {
        events.add(plainEvent(EventType.PASS_COMPLETED, MatchSide.HOME, homeMidfielder, 30));
        events.add(plainEvent(EventType.DRIBBLE_COMPLETED, MatchSide.HOME, homeForward, 10));
        events.add(plainEvent(EventType.PASS_COMPLETED, MatchSide.AWAY, awayDefender, 20));
        events.add(goal(MatchSide.HOME, homeForward, firstHalf(60), 0.30, 40));

        MatchSummary summary = calculator.summarize(events);

        assertThat(summary.home().possessionSeconds()).isEqualTo(80);
        assertThat(summary.away().possessionSeconds()).isEqualTo(20);
        assertThat(summary.possessionShareOf(MatchSide.HOME)).isCloseTo(0.8, within(TOLERANCE));
        assertThat(summary.possessionShareOf(MatchSide.AWAY)).isCloseTo(0.2, within(TOLERANCE));
    }

    @Test
    void aTurnoverCountsForTheSideThatHeldTheBallNotForTheSideOfItsPrimaryPlayer() {
        events.add(plainEvent(EventType.INTERCEPTION, MatchSide.HOME, awayDefender, 6));
        events.add(plainEvent(EventType.TACKLE, MatchSide.AWAY, homeMidfielder, 4));

        MatchSummary summary = calculator.summarize(events);

        assertThat(summary.home().possessionSeconds()).isEqualTo(6);
        assertThat(summary.away().possessionSeconds()).isEqualTo(4);
    }

    @Test
    void periodEventsWithZeroDurationLeavePossessionUnchanged() {
        events.add(plainEvent(EventType.KICK_OFF, MatchSide.HOME, null, 0));
        events.add(plainEvent(EventType.FIRST_HALF_END, MatchSide.AWAY, null, 0));

        MatchSummary summary = calculator.summarize(events);

        assertThat(summary.home().possessionSeconds()).isZero();
        assertThat(summary.away().possessionSeconds()).isZero();
    }

    @Test
    void contourCountersStayAtZeroWhenTheirEventsAreAbsent() {
        events.add(goal(MatchSide.HOME, homeForward, firstHalf(100), 0.30));

        MatchSummary summary = calculator.summarize(events);

        assertThat(summary.home().contour()).isEqualTo(ContourCounters.none());
        assertThat(summary.away().contour()).isEqualTo(ContourCounters.none());
    }

@Test
void cornersAreCountedForTheSideThatTookThem() {
    events.add(plainEvent(EventType.CORNER_KICK, MatchSide.HOME, homeMidfielder, 30));
    events.add(plainEvent(EventType.CORNER_KICK, MatchSide.HOME, homeMidfielder, 30));
    events.add(plainEvent(EventType.CORNER_KICK, MatchSide.AWAY, awayDefender, 30));

    MatchSummary summary = calculator.summarize(events);

    assertThat(summary.home().contour()).isEqualTo(ContourCounters.none().withCorners(2));
    assertThat(summary.away().contour()).isEqualTo(ContourCounters.none().withCorners(1));
}

@Test
void crossesCountBothTheCompletedAndTheFailedOnesForTheSideThatCrossed() {
    events.add(plainEvent(EventType.CROSS_COMPLETED, MatchSide.HOME, homeMidfielder, 4));
    events.add(plainEvent(EventType.CROSS_FAILED, MatchSide.HOME, homeMidfielder, 4));
    events.add(plainEvent(EventType.CROSS_FAILED, MatchSide.AWAY, awayDefender, 4));

    MatchSummary summary = calculator.summarize(events);

    assertThat(summary.home().contour()).isEqualTo(ContourCounters.none().withCrosses(2));
    assertThat(summary.away().contour()).isEqualTo(ContourCounters.none().withCrosses(1));
}

@Test
void aClearanceIsNeitherACornerNorACrossAndTheDeliveryOfACornerIsNotACross() {
    events.add(plainEvent(EventType.CLEARANCE, MatchSide.HOME, awayDefender, 2));
    events.add(plainEvent(EventType.CORNER_KICK, MatchSide.HOME, homeMidfielder, 30));

    MatchSummary summary = calculator.summarize(events);

    assertThat(summary.home().contour()).isEqualTo(ContourCounters.none().withCorners(1));
}

@Test
void aGoalWithoutAScorerIsRejected() {
        events.add(event(EventType.GOAL, MatchSide.HOME, firstHalf(10), Participants.nobody(), 0, goalDetail(0.3)));

        assertThatThrownBy(() -> calculator.summarize(events))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must name its scorer");
    }

    @Test
    void aShotWithoutAShotProfileIsRejected() {
        events.add(event(EventType.SHOT_OFF_TARGET, MatchSide.HOME, firstHalf(10), Participants.of(homeForward), 0,
                EventDetail.none()));

        assertThatThrownBy(() -> calculator.summarize(events))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shot profile");
    }

    @Test
    void foulsAreCountedForTheSideOfTheFoulerWhileTheirTimeBelongsToTheFouledSideThatHadTheBall() {
        events.add(foul(MatchSide.HOME, awayDefender, homeForward, 5));
        events.add(foul(MatchSide.HOME, awayDefender, homeMidfielder, 5));
        events.add(foul(MatchSide.AWAY, homeMidfielder, awayForward, 7));

        MatchSummary summary = calculator.summarize(events);

        assertThat(summary.home().contour().fouls()).isEqualTo(1);
        assertThat(summary.away().contour().fouls()).isEqualTo(2);
        assertThat(summary.home().possessionSeconds()).isEqualTo(10);
        assertThat(summary.away().possessionSeconds()).isEqualTo(7);
    }

    @Test
    void cardsAreCountedForTheSideOfTheCardedPlayerAndASecondYellowIsAYellowAndARed() {
        events.add(plainEvent(EventType.YELLOW_CARD, MatchSide.HOME, homeMidfielder, 0));
        events.add(plainEvent(EventType.YELLOW_CARD, MatchSide.AWAY, awayDefender, 0));
        events.add(plainEvent(EventType.SECOND_YELLOW_CARD, MatchSide.AWAY, awayDefender, 0));
        events.add(plainEvent(EventType.RED_CARD, MatchSide.HOME, homeForward, 0));

        MatchSummary summary = calculator.summarize(events);

        assertThat(summary.home().contour().yellowCards()).isEqualTo(1);
        assertThat(summary.home().contour().redCards()).isEqualTo(1);
        assertThat(summary.away().contour().yellowCards()).isEqualTo(2);
        assertThat(summary.away().contour().redCards()).isEqualTo(1);
    }

    @Test
    void substitutionsAreCountedForTheSideThatMadeThem() {
        events.add(event(EventType.SUBSTITUTION, MatchSide.AWAY, firstHalf(10),
                Participants.of(awayDefender, awayGoalkeeper), 0, EventDetail.none()));

        MatchSummary summary = calculator.summarize(events);

        assertThat(summary.away().contour().substitutions()).isEqualTo(1);
        assertThat(summary.home().contour().substitutions()).isZero();
    }

    @Test
    void aMatchWithoutSuspensionHasTheFieldScoreAsOfficialResult() {
        events.add(goal(MatchSide.HOME, homeForward, firstHalf(100), 0.30));

        MatchSummary summary = calculator.summarize(events);

        assertThat(summary.isSuspended()).isFalse();
        assertThat(summary.suspension()).isNull();
        assertThat(summary.officialScore()).isEqualTo(new Score(1, 0));
    }

    @Test
    void aSuspendedMatchShowsTheOfficialResultTheFieldScoreAndTheMinuteOfTheSuspension() {
        events.add(goal(MatchSide.HOME, homeForward, firstHalf(754), 0.30));
        events.add(goal(MatchSide.HOME, homeMidfielder, firstHalf(900), 0.20));
        events.add(goal(MatchSide.AWAY, awayForward, secondHalf(60), 0.10));
        Score field = new Score(2, 1);
        Score official = new Score(0, 3);
        EventDetail detail = EventDetail.suspension(MatchSide.HOME, field, official);
        events.add(event(EventType.MATCH_SUSPENDED, MatchSide.AWAY, secondHalf(1800), Participants.nobody(), 0,
                detail));

        MatchSummary summary = calculator.summarize(events);

        assertThat(summary.isSuspended()).isTrue();
        assertThat(summary.officialScore()).isEqualTo(official);
        assertThat(summary.fieldScore()).isEqualTo(field);
        assertThat(summary.suspension().responsibleSide()).isEqualTo(MatchSide.HOME);
        assertThat(summary.suspension().time().display()).isEqualTo("75'");
    }

    @Test
    void aSuspendedMatchKeepsTheStatisticsAndTheScorersOfTheEventsUpToTheSuspension() {
        events.add(goal(MatchSide.AWAY, awayForward, firstHalf(100), 0.30));
        events.add(shotEvent(EventType.SHOT_OFF_TARGET, MatchSide.HOME, homeForward, 0.10));
        EventDetail detail = EventDetail.suspension(MatchSide.HOME, new Score(0, 1), new Score(0, 3));
        events.add(event(EventType.MATCH_SUSPENDED, MatchSide.HOME, firstHalf(500), Participants.nobody(), 0,
                detail));

        MatchSummary summary = calculator.summarize(events);

        assertThat(summary.goals()).extracting(ScoredGoal::scorer).containsExactly(awayForward);
        assertThat(summary.home().shots()).isEqualTo(1);
        assertThat(summary.away().shots()).isEqualTo(1);
        assertThat(summary.officialScore()).isEqualTo(new Score(0, 3));
    }

    @Test
    void aSuspensionWithoutItsDetailIsRejected() {
        events.add(event(EventType.MATCH_SUSPENDED, MatchSide.HOME, firstHalf(500), Participants.nobody(), 0,
                EventDetail.none()));

        assertThatThrownBy(() -> calculator.summarize(events))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must carry the suspension detail");
    }

    @Test
    void penaltiesAreCountedForTheSideThatWonThemAndOtherFoulsAreNot() {
        events.add(foul(MatchSide.HOME, awayDefender, homeForward, 5, true));
        events.add(foul(MatchSide.HOME, awayDefender, homeForward, 5, false));
        events.add(foul(MatchSide.AWAY, homeMidfielder, awayForward, 5, true));
        events.add(foul(MatchSide.AWAY, homeMidfielder, awayForward, 5, true));

        MatchSummary summary = calculator.summarize(events);

        assertThat(summary.home().contour().penalties()).isEqualTo(1);
        assertThat(summary.away().contour().penalties()).isEqualTo(2);
    }

    @Test
    void offsidesAreCountedForTheSideThatWasCaughtOffsideAndNotForTheSideThatTakesTheFreeKick() {
        events.add(plainEvent(EventType.OFFSIDE, MatchSide.HOME, homeForward, 12));
        events.add(plainEvent(EventType.OFFSIDE_FREE_KICK, MatchSide.AWAY, awayDefender, 25));
        events.add(plainEvent(EventType.OFFSIDE, MatchSide.AWAY, awayForward, 9));
        events.add(plainEvent(EventType.OFFSIDE, MatchSide.AWAY, awayForward, 9));

        MatchSummary summary = calculator.summarize(events);

        assertThat(summary.home().contour().offsides()).isEqualTo(1);
        assertThat(summary.away().contour().offsides()).isEqualTo(2);
    }

    private MatchEvent foul(MatchSide fouledSide, Player fouler, Player fouled, int seconds) {
        return foul(fouledSide, fouler, fouled, seconds, false);
    }

    private MatchEvent foul(MatchSide fouledSide, Player fouler, Player fouled, int seconds, boolean penalty) {
        EventDetail detail = EventDetail.foul(FoulSeverity.HARD, penalty);
        Participants participants = Participants.of(fouler, fouled);
        return event(EventType.FOUL, fouledSide, firstHalf(10), participants, seconds, detail);
    }

    private MatchEvent goal(MatchSide side, Player scorer, MatchTime time, double expectedGoals) {
        return goal(side, scorer, time, expectedGoals, 0);
    }

    private MatchEvent goal(MatchSide side, Player scorer, MatchTime time, double expectedGoals, int seconds) {
        EventDetail detail = goalDetail(expectedGoals);
        return event(EventType.GOAL, side, time, Participants.of(scorer), seconds, detail);
    }

    private MatchEvent penaltyGoal(MatchSide side, Player scorer, double expectedGoals) {
        ShotProfile profile = ShotProfile.ofPenalty(expectedGoals, ShotQuality.BIG_CHANCE);
        EventDetail detail = EventDetail.goal(profile, GoalContext.TAKES_THE_LEAD);
        return event(EventType.GOAL, side, firstHalf(10), Participants.of(scorer), 0, detail);
    }

    private MatchEvent save(MatchSide side, Player shooter, Player goalkeeper, double expectedGoals) {
        ShotProfile profile = footShot(expectedGoals);
        EventDetail detail = EventDetail.save(profile, SaveQuality.ROUTINE);
        Participants participants = Participants.of(shooter, goalkeeper);
        return event(EventType.SHOT_SAVED, side, firstHalf(10), participants, 0, detail);
    }

    private MatchEvent shotEvent(EventType type, MatchSide side, Player shooter, double expectedGoals) {
        ShotProfile profile = footShot(expectedGoals);
        EventDetail detail = EventDetail.shot(profile);
        return event(type, side, firstHalf(10), Participants.of(shooter), 0, detail);
    }

    private MatchEvent plainEvent(EventType type, MatchSide side, Player primary, int seconds) {
        Participants participants = primary == null ? Participants.nobody() : Participants.of(primary);
        return event(type, side, firstHalf(10), participants, seconds, EventDetail.none());
    }

    private MatchEvent event(EventType type, MatchSide side, MatchTime time, Participants participants, int seconds,
            EventDetail detail) {
        int order = events.size() + 1;
        return new MatchEvent(order, time, type, side, ZoneRole.OPPONENT_BOX, PlayPhase.BUILD_UP, participants,
                seconds, detail);
    }

    private EventDetail goalDetail(double expectedGoals) {
        ShotProfile profile = footShot(expectedGoals);
        return EventDetail.goal(profile, GoalContext.TAKES_THE_LEAD);
    }

    private ShotProfile footShot(double expectedGoals) {
        return ShotProfile.ofShot(expectedGoals, ShotKind.FOOT, ShotQuality.HALF_CHANCE);
    }

    private MatchTime firstHalf(int seconds) {
        return new MatchTime(MatchPeriod.FIRST_HALF, seconds);
    }

    private MatchTime secondHalf(int seconds) {
        return new MatchTime(MatchPeriod.SECOND_HALF, seconds);
    }

    private static Player player(String name, Role role) {
        return new Player(name, role, new PlayerAttributes(50, 50, 50, 50, 50, 50, 50, 50));
    }
}
