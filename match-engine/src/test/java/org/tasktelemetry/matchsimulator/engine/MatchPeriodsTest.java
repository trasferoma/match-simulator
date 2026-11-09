package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.config.DurationRange;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.StoppageConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class MatchPeriodsTest {

    private static final int REGULATION_SECONDS = 45 * 60;
    private static final Set<EventType> PERIOD_EVENTS = EnumSet.of(EventType.KICK_OFF, EventType.STOPPAGE_ANNOUNCED,
            EventType.FIRST_HALF_END, EventType.SECOND_HALF_START, EventType.MATCH_END);
    private static final Set<EventType> OPEN_PLAY_ACTIONS = EnumSet.of(EventType.PASS_COMPLETED,
            EventType.DRIBBLE_COMPLETED, EventType.INTERCEPTION, EventType.TACKLE);

    private final Team home = TestTeams.team("Home", PlayStyle.BALANCED, 60);
    private final Team away = TestTeams.team("Away", PlayStyle.BALANCED, 60);

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 42, 2026})
    void aMatchHasKickOffTwoHalvesAnAnnouncementPerHalfAndAnEnd(long seed) {
        List<MatchEvent> events = playToEnd(EngineConfig.defaults(), seed);

        assertThat(periodEventTypes(events)).containsExactly(
                EventType.KICK_OFF, EventType.STOPPAGE_ANNOUNCED, EventType.FIRST_HALF_END,
                EventType.SECOND_HALF_START, EventType.STOPPAGE_ANNOUNCED, EventType.MATCH_END);
    }

    @Test
    void theMatchStartsWithTheHomeKickOffAndTheSecondHalfStartsWithTheAwayKickOff() {
        List<MatchEvent> events = playToEnd(EngineConfig.defaults(), 5L);

        MatchEvent kickOff = firstOfType(events, EventType.KICK_OFF);
        MatchEvent secondHalf = firstOfType(events, EventType.SECOND_HALF_START);
        assertThat(events.get(0)).isSameAs(kickOff);
        assertThat(kickOff.side()).isEqualTo(MatchSide.HOME);
        assertThat(secondHalf.side()).isEqualTo(MatchSide.AWAY);
        assertThat(kickOff.zoneRole()).isEqualTo(ZoneRole.MIDFIELD);
        assertThat(secondHalf.zoneRole()).isEqualTo(ZoneRole.MIDFIELD);
        assertThat(kickOff.phase()).isEqualTo(PlayPhase.SET_PIECE);
        assertThat(secondHalf.phase()).isEqualTo(PlayPhase.SET_PIECE);
        assertThat(kickOff.time().seconds()).isZero();
        assertThat(secondHalf.time().period()).isEqualTo(MatchPeriod.SECOND_HALF);
        assertThat(secondHalf.time().seconds()).isZero();
    }

    @Test
    void theMatchEndsWithTheEndEventAndNoStepCanBePlayedAfterwards() {
        Match match = new MatchEngine(EngineConfig.defaults()).start(home, away, 5L);
        playToEnd(match);
        int eventsAtTheEnd = match.events().size();

        assertThat(match.isFinished()).isTrue();
        assertThat(match.events().get(eventsAtTheEnd - 1).type()).isEqualTo(EventType.MATCH_END);
        assertThatThrownBy(match::step)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("the match is over");
        assertThat(match.events()).hasSize(eventsAtTheEnd);
    }

    @Test
    void theFirstStepIsTheKickOffAndNothingHasHappenedBefore() {
        Match match = new MatchEngine(EngineConfig.defaults()).start(home, away, 5L);

        assertThat(match.isFinished()).isFalse();
        assertThat(match.events()).isEmpty();

        match.step();

        assertThat(match.events()).extracting(MatchEvent::type).containsExactly(EventType.KICK_OFF);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void theAnnouncementIsNeverBelowTheConfiguredMinimumAndTheHalfLastsUntilItIsExceeded(int minimumMinutes) {
        EngineConfig config = configWithMinimumStoppage(minimumMinutes);
        int maximumStepSeconds = longestStepSeconds(config);
        List<MatchEvent> events = playToEnd(config, 9L);

        List<MatchEvent> announcements = eventsOfType(events, EventType.STOPPAGE_ANNOUNCED);
        List<MatchEvent> ends = List.of(firstOfType(events, EventType.FIRST_HALF_END),
                firstOfType(events, EventType.MATCH_END));

        assertThat(announcements).hasSize(2);
        for (int half = 0; half < announcements.size(); half++) {
            int announced = ((EventDetail.StoppageAnnounced) announcements.get(half).detail()).minutes();
            int expiry = REGULATION_SECONDS + announced * 60;
            MatchEvent end = ends.get(half);

            assertThat(announced).isGreaterThanOrEqualTo(minimumMinutes);
            assertThat(end.time().seconds()).isGreaterThan(expiry);
            assertThat(end.time().seconds()).isLessThanOrEqualTo(expiry + maximumStepSeconds);
        }
    }

    @Test
    void theAnnouncementComesRightAfterTheFirstActionThatReachesFortyFiveMinutes() {
        List<MatchEvent> events = playToEnd(EngineConfig.defaults(), 9L);

        for (MatchPeriod half : List.of(MatchPeriod.FIRST_HALF, MatchPeriod.SECOND_HALF)) {
            List<MatchEvent> inHalf = eventsOfPeriod(events, half);
            int announcementIndex = indexOfType(inHalf, EventType.STOPPAGE_ANNOUNCED);
            MatchEvent triggeringAction = inHalf.get(announcementIndex - 1);

            assertThat(triggeringAction.time().seconds()).isGreaterThanOrEqualTo(REGULATION_SECONDS);
            assertThat(triggeringAction.time().seconds() - triggeringAction.durationSeconds())
                    .isLessThan(REGULATION_SECONDS);
            assertThat(inHalf.get(announcementIndex).time()).isEqualTo(triggeringAction.time());
        }
    }

    @Test
    void eventsAreNumberedFromOneAndTheClockAdvancesByTheDurationOfEachAction() {
        List<MatchEvent> events = playToEnd(EngineConfig.defaults(), 13L);

        assertThat(events).extracting(MatchEvent::order)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, events.size()).boxed().toList());
        for (int index = 1; index < events.size(); index++) {
            MatchEvent previous = events.get(index - 1);
            MatchEvent current = events.get(index);
            if (previous.time().period() == current.time().period()) {
                assertThat(current.time().seconds() - previous.time().seconds())
                        .as("event %d", current.order())
                        .isEqualTo(current.durationSeconds());
            }
        }
    }

    @Test
    void periodEventsTakeNoTimeAndActionsStayInsideTheConfiguredDurations() {
        EngineConfig config = EngineConfig.defaults();
        List<MatchEvent> events = playToEnd(config, 17L);

        List<MatchEvent> periodEvents = events.stream().filter(event -> PERIOD_EVENTS.contains(event.type())).toList();
        List<MatchEvent> actions = events.stream().filter(event -> OPEN_PLAY_ACTIONS.contains(event.type())).toList();

        assertThat(periodEvents).allSatisfy(event -> assertThat(event.durationSeconds()).isZero());
        assertThat(actions).isNotEmpty().allSatisfy(event -> {
            assertThat(event.phase()).isIn(PlayPhase.BUILD_UP, PlayPhase.COUNTER_ATTACK);
            if (event.phase() == PlayPhase.BUILD_UP) {
                assertThat(event.durationSeconds()).isBetween(config.attackingThirdDuration().minSeconds(),
                        longestOpenPlayActionSeconds(config));
            } else {
                assertThat(config.counterAttack().advanceDuration().contains(event.durationSeconds())).isTrue();
            }
        });
    }

    @Test
    void theEventsListIsASnapshot() {
        Match match = new MatchEngine(EngineConfig.defaults()).start(home, away, 5L);
        match.step();
        List<MatchEvent> snapshot = match.events();

        match.step();

        assertThat(snapshot).hasSize(1);
        assertThatThrownBy(() -> snapshot.add(snapshot.get(0))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void startingAMatchRequiresBothTeamsAndAConfiguration() {
        MatchEngine engine = new MatchEngine(EngineConfig.defaults());

        assertThatThrownBy(() -> engine.start(null, away, 1L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> engine.start(home, null, 1L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MatchEngine(null)).isInstanceOf(NullPointerException.class);
    }

    private EngineConfig configWithMinimumStoppage(int minutes) {
        EngineConfig defaults = EngineConfig.defaults();
        DurationRange buildUp = defaults.buildUpAndMidfieldDuration();
        DurationRange attacking = defaults.attackingThirdDuration();
        StoppageConfig stoppage = new StoppageConfig(minutes, defaults.stoppage().secondsPerEvent(),
                defaults.stoppage().maximumFirstHalfMinutes(), defaults.stoppage().maximumSecondHalfMinutes());
        return new EngineConfig(buildUp, attacking, defaults.dribbleProbability(), stoppage, defaults.shot(),
                defaults.setPiece(), defaults.cross(), defaults.counterAttack(), defaults.playStyles(), defaults.foul(),
                defaults.offside(), defaults.injury(), defaults.lineup(), defaults.homeAdvantage());
    }

    private int longestOpenPlayActionSeconds(EngineConfig config) {
        return Math.max(config.buildUpAndMidfieldDuration().maxSeconds(), config.attackingThirdDuration().maxSeconds());
    }

    private int longestConfiguredActionSeconds(EngineConfig config) {
        return IntStream.of(
                config.buildUpAndMidfieldDuration().maxSeconds(),
                config.attackingThirdDuration().maxSeconds(),
                config.shot().shotDuration().maxSeconds(),
                config.shot().goalWithRestartDuration().maxSeconds(),
                config.shot().penaltyDuration().maxSeconds(),
                config.setPiece().goalKickDuration().maxSeconds(),
                config.setPiece().cornerDuration().maxSeconds(),
                config.cross().crossDuration().maxSeconds(),
                config.cross().clearanceDuration().maxSeconds())
                .max()
                .orElseThrow();
    }

    private int longestStepSeconds(EngineConfig config) {
        int penaltyConcededByTheLastAction = config.shot().penaltyDuration().maxSeconds();
        int substitutionCausedByTheLastAction = config.lineup().substitutionDuration().maxSeconds();
        return longestConfiguredActionSeconds(config) + penaltyConcededByTheLastAction
                + substitutionCausedByTheLastAction;
    }

    private List<MatchEvent> playToEnd(EngineConfig config, long seed) {
        Match match = new MatchEngine(config).start(home, away, seed);
        playToEnd(match);
        return match.events();
    }

    private void playToEnd(Match match) {
        while (!match.isFinished()) {
            match.step();
        }
    }

    private List<EventType> periodEventTypes(List<MatchEvent> events) {
        return events.stream()
                .map(MatchEvent::type)
                .filter(PERIOD_EVENTS::contains)
                .toList();
    }

    private List<MatchEvent> eventsOfType(List<MatchEvent> events, EventType type) {
        return events.stream().filter(event -> event.type() == type).toList();
    }

    private MatchEvent firstOfType(List<MatchEvent> events, EventType type) {
        return eventsOfType(events, type).get(0);
    }

    private List<MatchEvent> eventsOfPeriod(List<MatchEvent> events, MatchPeriod period) {
        return events.stream().filter(event -> event.time().period() == period).toList();
    }

    private int indexOfType(List<MatchEvent> events, EventType type) {
        return events.indexOf(firstOfType(events, type));
    }
}
