package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.config.DurationRange;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.FoulConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.FoulSeverity;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.ShotProfile;
import org.tasktelemetry.matchsimulator.engine.resolution.stoppage.StoppageTime;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class MatchStoppageTest {

    private static final int MATCHES = 60;
    private static final int FOUL_MATCHES = 300;
    private static final int REGULATION_SECONDS = 45 * 60;
    private static final Set<EventType> SHOT_TYPES = EnumSet.of(EventType.GOAL, EventType.SHOT_SAVED,
            EventType.SHOT_OFF_TARGET, EventType.SHOT_BLOCKED);
    private static final Set<EventType> CONSEQUENCES = EnumSet.of(EventType.YELLOW_CARD, EventType.SECOND_YELLOW_CARD,
            EventType.RED_CARD, EventType.SUBSTITUTION, EventType.STOPPAGE_ANNOUNCED);
    private static final Set<EventType> PERIOD_ENDS = EnumSet.of(EventType.FIRST_HALF_END, EventType.MATCH_END);

    private final Team home = TestTeams.team("Home", PlayStyle.BALANCED, 60);
    private final Team away = TestTeams.team("Away", PlayStyle.BALANCED, 60);

    @Test
    void theAnnouncedMinutesAreTheOnesComputedFromTheEventsOfTheHalfUntilTheAnnouncement() {
        EngineConfig config = EngineConfig.defaults();
        StoppageTime stoppage = new StoppageTime(config.stoppage());
        int checked = 0;

        for (List<MatchEvent> events : playMany(config, MATCHES)) {
            for (MatchPeriod half : List.of(MatchPeriod.FIRST_HALF, MatchPeriod.SECOND_HALF)) {
                MatchEvent announcement = announcementOf(events, half);
                List<MatchEvent> untilTheAnnouncement = events.subList(0, events.indexOf(announcement));
                int expected = stoppage.announcedMinutes(untilTheAnnouncement, half);

                assertThat(announcement.detail()).isEqualTo(EventDetail.stoppageAnnounced(expected));
                checked++;
            }
        }
        assertThat(checked).isEqualTo(MATCHES * 2);
    }

    @Test
    void eventsAfterTheAnnouncementDoNotChangeTheMinutesAlreadyAnnounced() {
        EngineConfig config = EngineConfig.defaults();
        StoppageTime stoppage = new StoppageTime(config.stoppage());
        int halvesWhereTheLaterEventsWouldHaveChangedIt = 0;

        for (List<MatchEvent> events : playMany(config, MATCHES)) {
            for (MatchPeriod half : List.of(MatchPeriod.FIRST_HALF, MatchPeriod.SECOND_HALF)) {
                MatchEvent announcement = announcementOf(events, half);
                int announced = ((EventDetail.StoppageAnnounced) announcement.detail()).minutes();
                int withAllTheEvents = stoppage.announcedMinutes(events, half);
                if (withAllTheEvents != announced) {
                    halvesWhereTheLaterEventsWouldHaveChangedIt++;
                }
                assertThat(eventsOfType(events, half, EventType.STOPPAGE_ANNOUNCED)).hasSize(1);
            }
        }
        assertThat(halvesWhereTheLaterEventsWouldHaveChangedIt).isPositive();
    }

    @Test
    void theAnnouncementComesAtTheFirstStepThatBringsTheClockToFortyFiveMinutesOrBeyond() {
        for (List<MatchEvent> events : playMany(EngineConfig.defaults(), MATCHES)) {
            for (MatchPeriod half : List.of(MatchPeriod.FIRST_HALF, MatchPeriod.SECOND_HALF)) {
                MatchEvent announcement = announcementOf(events, half);
                List<MatchEvent> before = events.subList(0, events.indexOf(announcement)).stream()
                        .filter(event -> event.time().period() == half)
                        .toList();

                assertThat(announcement.time().seconds()).isGreaterThanOrEqualTo(REGULATION_SECONDS);
                assertThat(before.stream().filter(event -> event.type() != EventType.KICK_OFF)
                        .filter(event -> event.time().seconds() >= REGULATION_SECONDS)
                        .filter(event -> event.type() != EventType.INJURY && event.type() != EventType.SUBSTITUTION
                                && !event.type().isCard())
                        .count()).isLessThanOrEqualTo(1);
            }
        }
    }

    @Test
    void aPenaltyConcededByTheActionThatExceedsTheStoppageIsTakenAndTheHalfEndsRightAfterIt() {
        int penaltiesAtTheWhistle = 0;

        for (List<MatchEvent> events : playMany(withFoulsEverywhere(), FOUL_MATCHES)) {
            for (MatchPeriod half : List.of(MatchPeriod.FIRST_HALF, MatchPeriod.SECOND_HALF)) {
                List<MatchEvent> afterTheExpiry = playedEventsAfterTheExpiryStarted(events, half);
                if (afterTheExpiry.isEmpty() || !isPenaltyFoul(afterTheExpiry.get(0))) {
                    continue;
                }
                assertThat(afterTheExpiry).hasSize(3);
                assertThat(isPenaltyShot(afterTheExpiry.get(1))).isTrue();
                assertThat(PERIOD_ENDS).contains(afterTheExpiry.get(2).type());
                penaltiesAtTheWhistle++;
            }
        }
        assertThat(penaltiesAtTheWhistle).isPositive();
    }

    @Test
    void anythingOtherThanAPenaltyConcededAtTheWhistleIsNotPlayed() {
        int endedByAnAction = 0;

        for (List<MatchEvent> events : playMany(withFoulsEverywhere(), FOUL_MATCHES)) {
            for (MatchPeriod half : List.of(MatchPeriod.FIRST_HALF, MatchPeriod.SECOND_HALF)) {
                List<MatchEvent> afterTheExpiry = playedEventsAfterTheExpiryStarted(events, half);
                if (afterTheExpiry.isEmpty() || isPenaltyFoul(afterTheExpiry.get(0))) {
                    continue;
                }
                assertThat(afterTheExpiry).hasSize(2);
                assertThat(PERIOD_ENDS).contains(afterTheExpiry.get(1).type());
                endedByAnAction++;
            }
        }
        assertThat(endedByAnAction).isPositive();
    }

    @Test
    void everyHalfLastsMoreThanTheRegulationPlusTheAnnouncementAndNoMoreThanTheLastStepAllows() {
        EngineConfig config = EngineConfig.defaults();
        int longestStep = longestStepSeconds(config);

        for (List<MatchEvent> events : playMany(withFoulsEverywhere(), 100)) {
            for (MatchPeriod half : List.of(MatchPeriod.FIRST_HALF, MatchPeriod.SECOND_HALF)) {
                int announced = ((EventDetail.StoppageAnnounced) announcementOf(events, half).detail()).minutes();
                int expiry = REGULATION_SECONDS + announced * MatchPeriod.SECONDS_PER_MINUTE;
                MatchEvent end = eventsOfType(events, half, half == MatchPeriod.FIRST_HALF
                        ? EventType.FIRST_HALF_END : EventType.MATCH_END).get(0);

                assertThat(end.time().seconds()).isGreaterThan(expiry);
                assertThat(end.time().seconds()).isLessThanOrEqualTo(expiry + longestStep);
            }
        }
    }

    private int longestStepSeconds(EngineConfig config) {
        int longestAction = config.shot().penaltyDuration().maxSeconds()
                + config.shot().goalWithRestartDuration().maxSeconds() + config.foul().foulDuration().maxSeconds();
        return longestAction + config.lineup().substitutionDuration().maxSeconds();
    }

    private List<MatchEvent> playedEventsAfterTheExpiryStarted(List<MatchEvent> events, MatchPeriod half) {
        int announced = ((EventDetail.StoppageAnnounced) announcementOf(events, half).detail()).minutes();
        int expiry = REGULATION_SECONDS + announced * MatchPeriod.SECONDS_PER_MINUTE;
        List<MatchEvent> inTheHalf = events.stream().filter(event -> event.time().period() == half).toList();
        List<MatchEvent> fromTheCrossing = new ArrayList<>();
        boolean crossed = false;
        for (MatchEvent event : inTheHalf) {
            crossed = crossed || event.time().seconds() > expiry;
            if (crossed && !CONSEQUENCES.contains(event.type())) {
                fromTheCrossing.add(event);
            }
        }
        return fromTheCrossing;
    }

    private boolean isPenaltyFoul(MatchEvent event) {
        return event.type() == EventType.FOUL && ((EventDetail.Foul) event.detail()).penalty();
    }

    private boolean isPenaltyShot(MatchEvent event) {
        return SHOT_TYPES.contains(event.type()) && profileOf(event).penalty();
    }

    private ShotProfile profileOf(MatchEvent shot) {
        return switch (shot.detail()) {
            case EventDetail.Goal goal -> goal.profile();
            case EventDetail.Save save -> save.profile();
            case EventDetail.Shot detail -> detail.profile();
            default -> throw new IllegalStateException("not a shot detail: " + shot.detail());
        };
    }

    private MatchEvent announcementOf(List<MatchEvent> events, MatchPeriod half) {
        return eventsOfType(events, half, EventType.STOPPAGE_ANNOUNCED).get(0);
    }

    private List<MatchEvent> eventsOfType(List<MatchEvent> events, MatchPeriod half, EventType type) {
        return events.stream()
                .filter(event -> event.type() == type && event.time().period() == half)
                .toList();
    }

    private EngineConfig withFoulsEverywhere() {
        FoulConfig defaults = FoulConfig.defaults();
        Map<FoulSeverity, Double> noCards = new EnumMap<>(FoulSeverity.class);
        for (FoulSeverity severity : FoulSeverity.values()) {
            noCards.put(severity, 0.0);
        }
        FoulConfig fouls = new FoulConfig(0.99, defaults.neutralTackling(), defaults.tacklingSlope(), 1.0,
                defaults.lightShare(), defaults.hardShare(), noCards, noCards, new DurationRange(3, 8));
        return TestConfigs.withFoul(EngineConfig.defaults(), fouls);
    }

    private List<List<MatchEvent>> playMany(EngineConfig config, int count) {
        List<List<MatchEvent>> matches = new ArrayList<>();
        for (long seed = 1; seed <= count; seed++) {
            Match match = new MatchEngine(config).start(home, away, seed);
            while (!match.isFinished()) {
                match.step();
            }
            matches.add(match.events());
        }
        return matches;
    }
}
