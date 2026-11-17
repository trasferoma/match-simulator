package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.inTheFirstHalf;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.lastEventOf;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.onPitch;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.onTheBench;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.playToTheEnd;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.substituteSameRole;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.substitution;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.config.DurationRange;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.InjuryConfig;
import org.tasktelemetry.matchsimulator.engine.config.LineupConfig;
import org.tasktelemetry.matchsimulator.engine.config.StoppageConfig;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.engine.resolution.stoppage.StoppageTime;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class VoluntarySubstitutionTest {

    private static final long SEED = ReferenceMatch.SEED;
    private static final MatchSide HOME = MatchSide.HOME;
    private static final MatchSide AWAY = MatchSide.AWAY;
    private static final DurationRange SUBSTITUTION_DURATION = LineupConfig.defaults().substitutionDuration();
    private static final int MATCHES_WITH_PASSES = 300;
    private static final int SEEDS_TO_SCAN = 3000;

    @Test
    void anExecutedSubstitutionIsAnEventWithWhoLeavesWhoEntersTheSectorAndTheReason() {
        Match match = inTheFirstHalf(SEED);
        int eventsBefore = match.events().size();
        Player leaving = onPitch(match, HOME, Role.DEFENDER, 0);
        Player entering = onTheBench(match, HOME, Role.DEFENDER, 0);
        Deployment unchanged = Deployment.of(match.teamOnPitch(HOME));

        InterventionResult result = match.apply(substitution(HOME, leaving, entering));

        assertThat(result).isEqualTo(InterventionResult.executed());
        assertThat(match.events()).hasSize(eventsBefore + 1);
        MatchEvent event = lastEventOf(match).orElseThrow();
        assertThat(event.type()).isEqualTo(EventType.SUBSTITUTION);
        assertThat(event.side()).isEqualTo(HOME);
        assertThat(event.participants()).isEqualTo(Participants.of(leaving, HOME, entering, HOME));
        assertThat(event.detail()).isEqualTo(
                EventDetail.lineupChange(Role.DEFENDER, ChangeReason.PROTECT_LEAD, unchanged, unchanged));
        assertThat(event.order()).isEqualTo(eventsBefore + 1);
    }

    @Test
    void theSubstitutionConsumesTheDrawnDurationAndAdvancesTheClockByIt() {
        Match match = inTheFirstHalf(SEED);
        MatchEvent before = lastEventOf(match).orElseThrow();

        substituteSameRole(match, HOME, Role.MIDFIELDER, 0);

        MatchEvent event = lastEventOf(match).orElseThrow();
        assertThat(event.durationSeconds()).isBetween(SUBSTITUTION_DURATION.minSeconds(),
                SUBSTITUTION_DURATION.maxSeconds());
        assertThat(event.time().seconds()).isEqualTo(before.time().seconds() + event.durationSeconds());
    }

    @Test
    void theSubstitutionConsumesADrawSoTheMatchDivergesFromTheOneWithoutIt() {
        List<MatchEvent> without = ReferenceMatch.play(SEED);
        Match match = inTheFirstHalf(SEED);
        substituteSameRole(match, HOME, Role.MIDFIELDER, 0);
        playToTheEnd(match);

        List<MatchEvent> with = match.events();

        List<MatchEvent> afterWithout = without.subList(MatchDriving.STEPS_INTO_THE_FIRST_HALF, without.size());
        List<MatchEvent> afterWith = with.subList(MatchDriving.STEPS_INTO_THE_FIRST_HALF + 1, with.size());
        assertThat(afterWith).isNotEqualTo(afterWithout);
    }

    @Test
    void theSubstitutionCountsAmongTheSubstitutionsMade() {
        Match match = inTheFirstHalf(SEED);
        int homeMade = match.teamOnPitch(HOME).substitutionsMade();
        int awayMade = match.teamOnPitch(AWAY).substitutionsMade();

        substituteSameRole(match, HOME, Role.FORWARD, 0);

        assertThat(match.teamOnPitch(HOME).substitutionsMade()).isEqualTo(homeMade + 1);
        assertThat(match.teamOnPitch(AWAY).substitutionsMade()).isEqualTo(awayMade);
    }

    @Test
    void theSubstitutionsOfTheFirstHalfCountInTheAnnouncedStoppage() {
        StoppageTime stoppage = new StoppageTime(StoppageConfig.defaults());
        Match match = inTheFirstHalf(SEED);
        substituteSameRole(match, HOME, Role.DEFENDER, 0);
        substituteSameRole(match, HOME, Role.DEFENDER, 1);
        substituteSameRole(match, HOME, Role.MIDFIELDER, 0);
        substituteSameRole(match, HOME, Role.MIDFIELDER, 1);
        substituteSameRole(match, HOME, Role.FORWARD, 0);
        playToTheEnd(match);

        List<MatchEvent> events = match.events();

        MatchEvent announcement = events.stream()
                .filter(event -> event.type() == EventType.STOPPAGE_ANNOUNCED)
                .findFirst()
                .orElseThrow();
        EventDetail.StoppageAnnounced detail = (EventDetail.StoppageAnnounced) announcement.detail();
        int announcedWithTheSubstitutions = detail.minutes();
        int announcedIfTheyWereIgnored = stoppage.announcedMinutes(
                events.stream().filter(event -> event.type() != EventType.SUBSTITUTION).toList(),
                MatchPeriod.FIRST_HALF);
        assertThat(announcedWithTheSubstitutions).isGreaterThanOrEqualTo(3);
        assertThat(announcedWithTheSubstitutions).isGreaterThan(announcedIfTheyWereIgnored);
    }

    @Test
    void whoEntersPlaysInTheSectorOfHisRoleAndTheModuleChangesWhenTheRolesDiffer() {
        Match match = inTheFirstHalf(SEED);
        TeamOnPitch before = match.teamOnPitch(HOME);
        Player leaving = onPitch(match, HOME, Role.DEFENDER, 0);
        Player entering = onTheBench(match, HOME, Role.FORWARD, 0);

        InterventionResult result = match.apply(substitution(HOME, leaving, entering));

        TeamOnPitch after = match.teamOnPitch(HOME);
        assertThat(result).isEqualTo(InterventionResult.executed());
        assertThat(after.roleOf(entering)).isEqualTo(Role.FORWARD);
        assertThat(after.isOutOfPosition(entering)).isFalse();
        assertThat(after.playersIn(Role.DEFENDER)).hasSize(before.playersIn(Role.DEFENDER).size() - 1);
        assertThat(after.playersIn(Role.FORWARD)).hasSize(before.playersIn(Role.FORWARD).size() + 1);
        assertThat(lastEventOf(match).orElseThrow().detail()).isEqualTo(EventDetail.lineupChange(Role.FORWARD,
                ChangeReason.PROTECT_LEAD, Deployment.of(before), Deployment.of(after)));
    }

    @Test
    void whoLeavesDoesNotComeBack() {
        Match match = inTheFirstHalf(SEED);
        Player leaving = onPitch(match, HOME, Role.MIDFIELDER, 0);
        Player entering = onTheBench(match, HOME, Role.MIDFIELDER, 0);
        match.apply(substitution(HOME, leaving, entering));

        InterventionResult back = match.apply(substitution(HOME, entering, leaving));

        assertThat(back).isEqualTo(InterventionResult.refused(RefusalReason.SUBSTITUTE_NOT_AVAILABLE));
        assertThat(match.teamOnPitch(HOME).availableBench()).doesNotContain(leaving);
    }

    @Test
    void substitutingTheLastPasserMakesTheNextGoalLoseItsAssist() {
        int cancelledAssists = 0;
        for (long seed = 1; seed <= MATCHES_WITH_PASSES; seed++) {
            cancelledAssists += goalsRightAfterSubstitutingThePasser(seed, true);
        }

        assertThat(cancelledAssists).isPositive();
    }

    @Test
    void substitutingSomeoneElseThanTheLastPasserKeepsTheAssist() {
        int keptAssists = 0;
        for (long seed = 1; seed <= MATCHES_WITH_PASSES; seed++) {
            keptAssists += goalsRightAfterSubstitutingThePasser(seed, false);
        }

        assertThat(keptAssists).isPositive();
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 42})
    void aRequestBeforeTheKickOffOrAfterTheFinalWhistleIsRefusedBecauseTheMatchIsNotInPlay(long seed) {
        Match beforeTheKickOff = ReferenceMatch.start(seed);
        Match afterTheEnd = ReferenceMatch.start(seed);
        playToTheEnd(afterTheEnd);
        List<MatchEvent> eventsAtTheEnd = afterTheEnd.events();

        InterventionResult early = beforeTheKickOff.apply(sameRoleSubstitution(beforeTheKickOff));
        InterventionResult late = afterTheEnd.apply(sameRoleSubstitution(afterTheEnd));

        assertThat(early).isEqualTo(InterventionResult.refused(RefusalReason.MATCH_NOT_IN_PLAY));
        assertThat(late).isEqualTo(InterventionResult.refused(RefusalReason.MATCH_NOT_IN_PLAY));
        assertThat(beforeTheKickOff.events()).isEmpty();
        assertThat(afterTheEnd.events()).isEqualTo(eventsAtTheEnd);
    }

    @Test
    void whoIsNotOnThePitchCannotLeave() {
        assertRefusedAndTheMatchGoesOnUntouched(RefusalReason.PLAYER_NOT_ON_PITCH, match -> {
            Player onTheBench = onTheBench(match, HOME, Role.DEFENDER, 0);
            Player otherOnTheBench = onTheBench(match, HOME, Role.MIDFIELDER, 0);
            return substitution(HOME, onTheBench, otherOnTheBench);
        });
    }

    @Test
    void aPlayerOfTheOtherTeamCannotLeaveForThisOne() {
        assertRefusedAndTheMatchGoesOnUntouched(RefusalReason.PLAYER_NOT_ON_PITCH, match -> {
            Player opponent = onPitch(match, AWAY, Role.DEFENDER, 0);
            Player entering = onTheBench(match, HOME, Role.DEFENDER, 0);
            return substitution(HOME, opponent, entering);
        });
    }

    @Test
    void whoIsAlreadyOnThePitchCannotEnter() {
        assertRefusedAndTheMatchGoesOnUntouched(RefusalReason.SUBSTITUTE_NOT_AVAILABLE, match -> {
            Player leaving = onPitch(match, HOME, Role.DEFENDER, 0);
            Player alreadyIn = onPitch(match, HOME, Role.DEFENDER, 1);
            return substitution(HOME, leaving, alreadyIn);
        });
    }

    @Test
    void whoHasAlreadyLeftCannotEnterAgain() {
        List<Player> leftThePitch = new ArrayList<>();
        Consumer<Match> oneAlreadySubstituted = match -> {
            Player leaving = onPitch(match, HOME, Role.MIDFIELDER, 0);
            leftThePitch.add(leaving);
            match.apply(substitution(HOME, leaving, onTheBench(match, HOME, Role.MIDFIELDER, 0)));
        };

        assertRefusedAndTheMatchGoesOnUntouched(RefusalReason.SUBSTITUTE_NOT_AVAILABLE, oneAlreadySubstituted,
                match -> substitution(HOME, onPitch(match, HOME, Role.DEFENDER, 0), leftThePitch.get(0)));
    }

    @Test
    void aPlayerOfTheOtherTeamCannotEnter() {
        assertRefusedAndTheMatchGoesOnUntouched(RefusalReason.SUBSTITUTE_NOT_AVAILABLE, match -> {
            Player leaving = onPitch(match, HOME, Role.DEFENDER, 0);
            Player opponentOnTheBench = onTheBench(match, AWAY, Role.DEFENDER, 0);
            return substitution(HOME, leaving, opponentOnTheBench);
        });
    }

    @Test
    void theSixthSubstitutionIsRefusedBecauseTheyAreExhausted() {
        Consumer<Match> fiveSubstitutions = match -> {
            substituteSameRole(match, HOME, Role.DEFENDER, 0);
            substituteSameRole(match, HOME, Role.DEFENDER, 1);
            substituteSameRole(match, HOME, Role.MIDFIELDER, 0);
            substituteSameRole(match, HOME, Role.MIDFIELDER, 1);
            substituteSameRole(match, HOME, Role.FORWARD, 0);
        };

        assertRefusedAndTheMatchGoesOnUntouched(RefusalReason.NO_SUBSTITUTIONS_LEFT, fiveSubstitutions, match -> {
            Player leaving = onPitch(match, HOME, Role.DEFENDER, 2);
            Player entering = onTheBench(match, HOME, Role.FORWARD, 0);
            return substitution(HOME, leaving, entering);
        });
    }

    @Test
    void theGoalkeeperCannotLeaveForAnOutfielder() {
        assertRefusedAndTheMatchGoesOnUntouched(RefusalReason.GOALKEEPER_NOT_COVERED, match -> {
            Player goalkeeper = match.teamOnPitch(HOME).goalkeeper();
            Player outfielder = onTheBench(match, HOME, Role.DEFENDER, 0);
            return substitution(HOME, goalkeeper, outfielder);
        });
    }

    @Test
    void anOutfielderCannotLeaveForTheReserveGoalkeeperWhileTheGoalkeeperIsInGoal() {
        assertRefusedAndTheMatchGoesOnUntouched(RefusalReason.GOALKEEPER_NOT_COVERED, match -> {
            Player outfielder = onPitch(match, HOME, Role.DEFENDER, 0);
            Player reserveGoalkeeper = onTheBench(match, HOME, Role.GOALKEEPER, 0);
            return substitution(HOME, outfielder, reserveGoalkeeper);
        });
    }

    @Test
    void theLastPlayerOfASectorCannotLeaveForAnotherSector() {
        Consumer<Match> oneForwardLeft = match ->
                match.apply(substitution(HOME, onPitch(match, HOME, Role.FORWARD, 0),
                        onTheBench(match, HOME, Role.DEFENDER, 0)));

        assertRefusedAndTheMatchGoesOnUntouched(RefusalReason.SECTOR_WOULD_BE_EMPTIED, oneForwardLeft, match -> {
            Player lastForward = onPitch(match, HOME, Role.FORWARD, 0);
            Player entering = onTheBench(match, HOME, Role.DEFENDER, 0);
            return substitution(HOME, lastForward, entering);
        });
    }

    @Test
    void theLastPlayerOfASectorCanLeaveForAnotherOfTheSameRole() {
        Match match = inTheFirstHalf(SEED);
        match.apply(substitution(HOME, onPitch(match, HOME, Role.FORWARD, 0),
                onTheBench(match, HOME, Role.DEFENDER, 0)));

        InterventionResult result = substituteSameRole(match, HOME, Role.FORWARD, 0);

        assertThat(result).isEqualTo(InterventionResult.executed());
        assertThat(match.teamOnPitch(HOME).playersIn(Role.FORWARD)).hasSize(1);
    }

    @Test
    void afterFiveVoluntarySubstitutionsAnInjuryRemainsWithoutReplacement() {
        Match match = matchWithFrequentInjuries(SEED);
        match.step();
        substituteSameRole(match, HOME, Role.DEFENDER, 0);
        substituteSameRole(match, HOME, Role.DEFENDER, 1);
        substituteSameRole(match, HOME, Role.MIDFIELDER, 0);
        substituteSameRole(match, HOME, Role.MIDFIELDER, 1);
        substituteSameRole(match, HOME, Role.FORWARD, 0);
        playToTheEnd(match);

        List<MatchEvent> events = match.events();

        assertThat(eventsOf(events, EventType.INJURY, HOME)).isNotEmpty();
        List<MatchEvent> homeSubstitutions = eventsOf(events, EventType.SUBSTITUTION, HOME);
        assertThat(homeSubstitutions).hasSize(5);
        assertThat(homeSubstitutions).noneMatch(event -> isForced(event));
    }

    @Test
    void afterTwoForcedSubstitutionsTheFourthVoluntaryOneIsRefused() {
        Match match = matchWithFrequentInjuries(SEED);
        while (forcedSubstitutionsOf(match, HOME) < 2) {
            match.step();
        }

        int executed = 0;
        Optional<RefusalReason> refusal = Optional.empty();
        while (refusal.isEmpty()) {
            InterventionResult result = match.apply(anyValidSubstitution(match));
            if (result instanceof InterventionResult.Refused refused) {
                refusal = Optional.of(refused.reason());
            } else {
                executed++;
            }
        }

        assertThat(executed).isEqualTo(3);
        assertThat(refusal).contains(RefusalReason.NO_SUBSTITUTIONS_LEFT);
        assertThat(match.teamOnPitch(HOME).substitutionsMade()).isEqualTo(5);
    }

    @Test
    void aSubstitutionThatTakesTheClockPastTheStoppageEndsTheMatchRightAfterItWithoutAnotherAction() {
        Match match = findMatchWhere(this::substitutionWouldOutlastTheStoppage, MatchPeriod.SECOND_HALF);

        InterventionResult result = substituteSameRole(match, HOME, Role.MIDFIELDER, 0);

        assertThat(result).isEqualTo(InterventionResult.executed());
        assertThat(match.isFinished()).isTrue();
        assertThat(typesOfTheLastEvents(match, 2)).containsExactly(EventType.SUBSTITUTION, EventType.MATCH_END);
    }

    @Test
    void aSubstitutionThatTakesTheClockPastTheStoppageOfTheFirstHalfEndsItAndTheSecondHalfStartsAsUsual() {
        Match match = findMatchWhere(this::substitutionWouldOutlastTheStoppage, MatchPeriod.FIRST_HALF);

        InterventionResult result = substituteSameRole(match, HOME, Role.MIDFIELDER, 0);
        match.step();

        assertThat(result).isEqualTo(InterventionResult.executed());
        assertThat(match.isFinished()).isFalse();
        assertThat(typesOfTheLastEvents(match, 3)).containsExactly(EventType.SUBSTITUTION,
                EventType.FIRST_HALF_END, EventType.SECOND_HALF_START);
    }

    @Test
    void aSubstitutionThatTakesTheClockToTheRegulationTimeAnnouncesTheStoppageCountingItself() {
        StoppageTime stoppage = new StoppageTime(StoppageConfig.defaults());
        Match match = findMatchWhere(this::substitutionWouldReachTheRegulationTime, MatchPeriod.FIRST_HALF);

        InterventionResult result = substituteSameRole(match, HOME, Role.MIDFIELDER, 0);

        assertThat(result).isEqualTo(InterventionResult.executed());
        assertThat(typesOfTheLastEvents(match, 2)).containsExactly(EventType.SUBSTITUTION,
                EventType.STOPPAGE_ANNOUNCED);
        List<MatchEvent> events = match.events();
        MatchEvent announcement = events.get(events.size() - 1);
        List<MatchEvent> beforeTheAnnouncement = events.subList(0, events.size() - 1);
        int expectedMinutes = stoppage.announcedMinutes(beforeTheAnnouncement, MatchPeriod.FIRST_HALF);
        assertThat(announcement.detail()).isEqualTo(EventDetail.stoppageAnnounced(expectedMinutes));
    }

    private Match findMatchWhere(BiPredicate<Match, MatchPeriod> window, MatchPeriod period) {
        for (long seed = 1; seed <= SEEDS_TO_SCAN; seed++) {
            Match match = ReferenceMatch.start(seed);
            while (!match.isFinished()) {
                match.step();
                if (isPlayableWithTheBallStill(match) && window.test(match, period)) {
                    return match;
                }
            }
        }
        throw new IllegalStateException("no moment found in the first " + SEEDS_TO_SCAN + " seeds");
    }

    private boolean isPlayableWithTheBallStill(Match match) {
        EventType last = lastEventOf(match).orElseThrow().type();
        boolean isPlayed = !match.isFinished() && last != EventType.FIRST_HALF_END;
        return isPlayed && !match.state().hasCrossInFlight() && !match.state().isAwaitingPenalty();
    }

    private boolean substitutionWouldOutlastTheStoppage(Match match, MatchPeriod period) {
        OptionalInt expiry = expiryOf(match.events(), period);
        MatchTime now = lastEventOf(match).orElseThrow().time();
        if (now.period() != period || expiry.isEmpty()) {
            return false;
        }
        int endOfTheSubstitution = now.seconds() + SUBSTITUTION_DURATION.minSeconds();
        return now.seconds() <= expiry.getAsInt() && endOfTheSubstitution > expiry.getAsInt();
    }

    private boolean substitutionWouldReachTheRegulationTime(Match match, MatchPeriod period) {
        MatchTime now = lastEventOf(match).orElseThrow().time();
        boolean isBeforeTheAnnouncement = now.period() == period && expiryOf(match.events(), period).isEmpty();
        int regulation = MatchPeriod.REGULATION_SECONDS;
        return isBeforeTheAnnouncement && now.seconds() < regulation
                && now.seconds() + SUBSTITUTION_DURATION.minSeconds() >= regulation;
    }

    private OptionalInt expiryOf(List<MatchEvent> events, MatchPeriod period) {
        return events.stream()
                .filter(event -> event.type() == EventType.STOPPAGE_ANNOUNCED && event.time().period() == period)
                .mapToInt(event -> expiryAnnouncedBy(event))
                .findFirst();
    }

    private int expiryAnnouncedBy(MatchEvent announcement) {
        EventDetail.StoppageAnnounced detail = (EventDetail.StoppageAnnounced) announcement.detail();
        return MatchPeriod.REGULATION_SECONDS + detail.minutes() * 60;
    }

    private List<EventType> typesOfTheLastEvents(Match match, int count) {
        List<MatchEvent> events = match.events();
        return events.subList(events.size() - count, events.size()).stream()
                .map(MatchEvent::type)
                .toList();
    }

    private int goalsRightAfterSubstitutingThePasser(long seed, boolean substitutePasser) {
        Match match = ReferenceMatch.start(seed);
        int cases = 0;
        Optional<SubstitutedPass> pending = Optional.empty();
        while (!match.isFinished()) {
            match.step();
            if (pending.isPresent() && isTheGoalRightAfter(match, pending.get())) {
                cases += assistIsAsExpected(match, pending.get().passer(), substitutePasser) ? 1 : 0;
            }
            pending = substituteAfterAPass(match, substitutePasser);
        }
        return cases;
    }

    private Optional<SubstitutedPass> substituteAfterAPass(Match match, boolean substitutePasser) {
        MatchEvent last = lastEventOf(match).orElseThrow();
        Optional<Player> passer = last.type() == EventType.PASS_COMPLETED ? last.participants().primary()
                : Optional.empty();
        if (passer.isEmpty() || !trySubstitution(match, last.side(), passer.get(), substitutePasser)) {
            return Optional.empty();
        }
        return Optional.of(new SubstitutedPass(passer.get(), last.side(), match.events().size()));
    }

    private boolean isTheGoalRightAfter(Match match, SubstitutedPass pass) {
        MatchEvent last = lastEventOf(match).orElseThrow();
        boolean isTheNextEvent = match.events().size() == pass.eventsAfterTheSubstitution() + 1;
        boolean isFootGoal = last.detail() instanceof EventDetail.Goal goal && !goal.profile().penalty()
                && goal.profile().kind() == ShotKind.FOOT;
        return isTheNextEvent && isFootGoal && last.type() == EventType.GOAL && last.side() == pass.side();
    }

    private boolean assistIsAsExpected(Match match, Player passer, boolean substitutedPasser) {
        MatchEvent goal = lastEventOf(match).orElseThrow();
        Optional<Player> assistant = goal.participants().secondary();
        if (substitutedPasser) {
            assertThat(assistant).as("goal %s", goal).isEmpty();
            return true;
        }
        boolean passerIsTheScorer = goal.participants().primary().equals(Optional.of(passer));
        if (passerIsTheScorer) {
            return false;
        }
        assertThat(assistant).as("goal %s", goal).contains(passer);
        return true;
    }

    private boolean trySubstitution(Match match, MatchSide side, Player passer, boolean substitutePasser) {
        TeamOnPitch team = match.teamOnPitch(side);
        Optional<Player> leaving = substitutePasser ? Optional.of(passer) : anotherOutfielderThan(team, passer);
        if (leaving.isEmpty() || !team.players().contains(passer)) {
            return false;
        }
        Optional<Player> entering = benchPlayerWithTheRoleOf(team, leaving.get());
        if (entering.isEmpty()) {
            return false;
        }
        InterventionResult result = match.apply(substitution(side, leaving.get(), entering.get()));
        return !result.isRefused();
    }

    private Optional<Player> anotherOutfielderThan(TeamOnPitch team, Player passer) {
        return team.players().stream()
                .filter(player -> !player.equals(passer))
                .filter(player -> team.roleOf(player) != Role.GOALKEEPER)
                .findFirst();
    }

    private Optional<Player> benchPlayerWithTheRoleOf(TeamOnPitch team, Player leaving) {
        return team.availableBench().stream()
                .filter(player -> player.role() == team.roleOf(leaving))
                .findFirst();
    }

    private Intervention anyValidSubstitution(Match match) {
        TeamOnPitch team = match.teamOnPitch(HOME);
        for (Player entering : team.availableBench()) {
            Optional<Player> leaving = team.players().stream()
                    .filter(player -> team.roleOf(player) == entering.role())
                    .filter(player -> team.playersIn(entering.role()).size() >= 2)
                    .findFirst();
            if (leaving.isPresent()) {
                return substitution(HOME, leaving.get(), entering);
            }
        }
        Player leaving = onPitch(match, HOME, Role.MIDFIELDER, 0);
        return substitution(HOME, leaving, team.availableBench().get(0));
    }

    private long forcedSubstitutionsOf(Match match, MatchSide side) {
        return eventsOf(match.events(), EventType.SUBSTITUTION, side).stream()
                .filter(event -> isForced(event))
                .count();
    }

    private boolean isForced(MatchEvent event) {
        return event.detail() instanceof EventDetail.LineupChange change && change.reason().isForced();
    }

    private List<MatchEvent> eventsOf(List<MatchEvent> events, EventType type, MatchSide side) {
        return events.stream()
                .filter(event -> event.type() == type && event.side() == side)
                .toList();
    }

    private record SubstitutedPass(Player passer, MatchSide side, int eventsAfterTheSubstitution) {
    }

    private Match matchWithFrequentInjuries(long seed) {
        EngineConfig defaults = EngineConfig.defaults();
        InjuryConfig frequent = new InjuryConfig(0.05, defaults.injury().duration());
        EngineConfig config = TestConfigs.withInjury(defaults, frequent);
        Team home = TestTeams.team("Home", PlayStyle.ATTACKING, 65);
        Team away = TestTeams.team("Away", PlayStyle.DEFENSIVE, 55);
        return new MatchEngine(config).start(home, away, seed);
    }

    private Intervention sameRoleSubstitution(Match match) {
        Player leaving = onPitch(match, HOME, Role.DEFENDER, 0);
        Player entering = onTheBench(match, HOME, Role.DEFENDER, 0);
        return substitution(HOME, leaving, entering);
    }

    private void assertRefusedAndTheMatchGoesOnUntouched(RefusalReason expected,
            Function<Match, Intervention> request) {
        assertRefusedAndTheMatchGoesOnUntouched(expected, match -> { }, request);
    }

    private void assertRefusedAndTheMatchGoesOnUntouched(RefusalReason expected, Consumer<Match> preamble,
            Function<Match, Intervention> request) {
        Match twin = inTheFirstHalf(SEED);
        preamble.accept(twin);
        playToTheEnd(twin);
        Match match = inTheFirstHalf(SEED);
        preamble.accept(match);
        List<MatchEvent> eventsBefore = match.events();

        InterventionResult result = match.apply(request.apply(match));

        assertThat(result).isEqualTo(InterventionResult.refused(expected));
        assertThat(match.events()).isEqualTo(eventsBefore);
        playToTheEnd(match);
        assertThat(match.events()).isEqualTo(twin.events());
    }
}
