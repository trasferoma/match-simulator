package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.onPitch;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.onTheBench;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.playToTheEnd;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.stepUntilHalfTime;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.substituteSameRole;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.substituteWhoeverIsPossible;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.substitution;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.config.StoppageConfig;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class HalfTimeInterventionTest {

    private static final long SEED = ReferenceMatch.SEED;
    private static final MatchTime START_OF_THE_SECOND_HALF = new MatchTime(MatchPeriod.SECOND_HALF, 0);

    @Test
    void aSubstitutionAtHalfTimeIsRecordedAtTheStartOfTheSecondHalfBeforeItsStartEvent() {
        Match match = ReferenceMatch.start(SEED);
        stepUntilHalfTime(match);
        int eventsBefore = match.events().size();
        Player leaving = onPitch(match, MatchSide.HOME, Role.DEFENDER, 0);
        Player entering = onTheBench(match, MatchSide.HOME, Role.FORWARD, 0);
        Deployment before = Deployment.of(match.teamOnPitch(MatchSide.HOME));

        InterventionResult result = match.apply(substitution(MatchSide.HOME, leaving, entering));
        match.step();

        assertThat(result).isEqualTo(InterventionResult.executed());
        List<MatchEvent> events = match.events();
        assertThat(events).hasSize(eventsBefore + 2);
        MatchEvent substitution = events.get(eventsBefore);
        MatchEvent secondHalfStart = events.get(eventsBefore + 1);
        assertThat(substitution.type()).isEqualTo(EventType.SUBSTITUTION);
        assertThat(substitution.time()).isEqualTo(START_OF_THE_SECOND_HALF);
        assertThat(substitution.durationSeconds()).isZero();
        Deployment after = new Deployment(before.defenders() - 1, before.midfielders(), before.forwards() + 1);
        assertThat(substitution.detail())
                .isEqualTo(EventDetail.lineupChange(Role.FORWARD, ChangeReason.PROTECT_LEAD, before, after));
        assertThat(secondHalfStart.type()).isEqualTo(EventType.SECOND_HALF_START);
        assertThat(secondHalfStart.time()).isEqualTo(START_OF_THE_SECOND_HALF);
        assertThat(secondHalfStart.order()).isEqualTo(substitution.order() + 1);
    }

    @Test
    void theRequestsAtHalfTimeAreRecordedInTheOrderTheyArrive() {
        Match match = ReferenceMatch.start(SEED);
        stepUntilHalfTime(match);
        int eventsBefore = match.events().size();

        substituteSameRole(match, MatchSide.AWAY, Role.MIDFIELDER, 0);
        substituteSameRole(match, MatchSide.HOME, Role.DEFENDER, 0);
        substituteSameRole(match, MatchSide.AWAY, Role.FORWARD, 0);
        match.step();

        List<MatchEvent> added = match.events().subList(eventsBefore, match.events().size());
        assertThat(added).extracting(MatchEvent::type).containsExactly(EventType.SUBSTITUTION,
                EventType.SUBSTITUTION, EventType.SUBSTITUTION, EventType.SECOND_HALF_START);
        assertThat(added).extracting(MatchEvent::side).containsExactly(MatchSide.AWAY, MatchSide.HOME,
                MatchSide.AWAY, MatchSide.AWAY);
        assertThat(added).extracting(MatchEvent::time).containsOnly(START_OF_THE_SECOND_HALF);
        assertThat(added).extracting(MatchEvent::durationSeconds).containsOnly(0);
    }

    @Test
    void aSubstitutionAtHalfTimeCountsAmongTheSubstitutionsMadeAndTheLimit() {
        Match match = ReferenceMatch.start(SEED);
        stepUntilHalfTime(match);
        substituteSameRole(match, MatchSide.HOME, Role.DEFENDER, 0);
        substituteSameRole(match, MatchSide.HOME, Role.DEFENDER, 1);
        substituteSameRole(match, MatchSide.HOME, Role.MIDFIELDER, 0);
        substituteSameRole(match, MatchSide.HOME, Role.MIDFIELDER, 1);
        substituteSameRole(match, MatchSide.HOME, Role.FORWARD, 0);
        Player leaving = onPitch(match, MatchSide.HOME, Role.FORWARD, 1);
        Player entering = onTheBench(match, MatchSide.HOME, Role.FORWARD, 0);

        InterventionResult sixth = match.apply(substitution(MatchSide.HOME, leaving, entering));

        assertThat(match.teamOnPitch(MatchSide.HOME).substitutionsMade()).isEqualTo(5);
        assertThat(sixth).isEqualTo(InterventionResult.refused(RefusalReason.NO_SUBSTITUTIONS_LEFT));
    }

    @Test
    void aRefusedRequestAtHalfTimeLeavesTheMatchLikeTheOneWithoutRequests() {
        Match match = ReferenceMatch.start(SEED);
        stepUntilHalfTime(match);
        Player notOnThePitch = onTheBench(match, MatchSide.HOME, Role.DEFENDER, 0);
        Player entering = onTheBench(match, MatchSide.HOME, Role.MIDFIELDER, 0);

        InterventionResult result = match.apply(substitution(MatchSide.HOME, notOnThePitch, entering));
        playToTheEnd(match);

        assertThat(result).isEqualTo(InterventionResult.refused(RefusalReason.PLAYER_NOT_ON_PITCH));
        assertThat(match.events()).isEqualTo(ReferenceMatch.play(SEED));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 42})
    void aSubstitutionBetweenTwinsAtHalfTimeLeavesTheRestOfTheMatchEqualExceptTheNames(long seed) {
        List<MatchEvent> without = withoutFatigueEvents(ReferenceMatch.play(seed));
        Match match = ReferenceMatch.start(seed);
        stepUntilHalfTime(match);
        substituteSameRole(match, MatchSide.HOME, Role.DEFENDER, 0);
        substituteSameRole(match, MatchSide.AWAY, Role.MIDFIELDER, 0);
        playToTheEnd(match);

        List<MatchEvent> with = match.events();
        List<MatchEvent> withWithoutFatigue = withoutFatigueEvents(with);

        assertThat(withWithoutFatigue).hasSize(without.size() + 2);
        assertThat(with).extracting(MatchEvent::order).containsExactlyElementsOf(consecutiveOrdersUpTo(with.size()));
        assertThat(shapesWithoutTheHalfTimeSubstitutions(withWithoutFatigue)).isEqualTo(shapesOf(without));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 42})
    void theHalfTimeSubstitutionsDoNotCountInTheStoppageOfTheSecondHalf(long seed) {
        Match match = ReferenceMatch.start(seed);
        stepUntilHalfTime(match);
        int made = 0;
        for (MatchSide side : MatchSide.values()) {
            made += substituteWhoeverIsPossible(match, side, 5);
        }
        assertThat(made).isGreaterThanOrEqualTo(6);
        playToTheEnd(match);

        List<MatchEvent> events = match.events();

        int startIndex = indexOf(events, EventType.SECOND_HALF_START, 0);
        int announcementIndex = indexOf(events, EventType.STOPPAGE_ANNOUNCED, startIndex);
        MatchEvent announcement = events.get(announcementIndex);
        EventDetail.StoppageAnnounced announced = (EventDetail.StoppageAnnounced) announcement.detail();
        List<MatchEvent> countedEvents = events.subList(startIndex + 1, announcementIndex);
        assertThat(announced.minutes()).isEqualTo(expectedSecondHalfStoppage(countedEvents));
    }

    private int expectedSecondHalfStoppage(List<MatchEvent> events) {
        StoppageConfig config = StoppageConfig.defaults();
        long fixedCostEvents = events.stream()
                .filter(event -> event.type() == EventType.GOAL || event.type() == EventType.SUBSTITUTION
                        || event.type().isCard())
                .count();
        int injurySeconds = events.stream()
                .filter(event -> event.type() == EventType.INJURY)
                .mapToInt(MatchEvent::durationSeconds)
                .sum();
        int lostSeconds = (int) fixedCostEvents * config.secondsPerEvent() + injurySeconds;
        int minutes = Math.ceilDiv(lostSeconds, MatchPeriod.SECONDS_PER_MINUTE);
        return Math.max(config.minimumMinutes(), Math.min(minutes, config.maximumSecondHalfMinutes()));
    }

    private int indexOf(List<MatchEvent> events, EventType type, int from) {
        return IntStream.range(from, events.size())
                .filter(index -> events.get(index).type() == type)
                .findFirst()
                .orElseThrow();
    }

    private List<Integer> consecutiveOrdersUpTo(int size) {
        return IntStream.rangeClosed(1, size).boxed().toList();
    }

    private List<MatchEvent> withoutFatigueEvents(List<MatchEvent> events) {
        return MatchDriving.withoutEventsOf(events, EventType.PLAYER_TIRED, EventType.PLAYER_RECOVERED);
    }

    private List<List<Object>> shapesWithoutTheHalfTimeSubstitutions(List<MatchEvent> events) {
        int startIndex = indexOf(events, EventType.SECOND_HALF_START, 0);
        int endOfTheFirstHalf = indexOf(events, EventType.FIRST_HALF_END, 0);
        List<MatchEvent> kept = IntStream.range(0, events.size())
                .filter(index -> index <= endOfTheFirstHalf || index >= startIndex)
                .mapToObj(events::get)
                .toList();
        return shapesOf(kept);
    }

    private List<List<Object>> shapesOf(List<MatchEvent> events) {
        return events.stream().map(this::shapeOf).toList();
    }

    private List<Object> shapeOf(MatchEvent event) {
        String primary = describe(event.participants().primary().orElse(null));
        String secondary = describe(event.participants().secondary().orElse(null));
        return List.of(event.time(), event.type(), event.side(), event.zoneRole(), event.ball(), event.phase(),
                event.durationSeconds(), event.detail(), primary, secondary);
    }

    private String describe(Player player) {
        if (player == null) {
            return "-";
        }
        return player.role() + "/" + player.attributes();
    }
}
