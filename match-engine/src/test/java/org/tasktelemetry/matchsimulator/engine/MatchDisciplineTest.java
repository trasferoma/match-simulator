package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.config.DurationRange;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.FoulConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.FoulSeverity;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.event.ShotProfile;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class MatchDisciplineTest {

    private static final int MATCHES = 40;
    private static final Set<EventType> SHOT_TYPES = EnumSet.of(EventType.GOAL, EventType.SHOT_SAVED,
            EventType.SHOT_OFF_TARGET, EventType.SHOT_BLOCKED);
    private static final Set<EventType> NOT_PLAYED_TYPES = EnumSet.of(EventType.STOPPAGE_ANNOUNCED,
            EventType.FIRST_HALF_END, EventType.MATCH_END, EventType.SUBSTITUTION, EventType.YELLOW_CARD,
            EventType.SECOND_YELLOW_CARD, EventType.RED_CARD);

    private final Team home = TestTeams.team("Home", PlayStyle.BALANCED, 60);
    private final Team away = TestTeams.team("Away", PlayStyle.BALANCED, 60);
    private final List<List<MatchEvent>> defaultMatches = IntStream.rangeClosed(1, MATCHES)
            .mapToObj(seed -> play(EngineConfig.defaults(), seed))
            .toList();

    @Test
    void afterAFoulTheFouledSideRestartsFromTheZoneOfTheFoulWithAFreeKickOrAPenaltyInTheSetPiecePhase() {
        int checked = 0;
        for (List<MatchEvent> events : defaultMatches) {
            for (int index = 0; index < events.size(); index++) {
                MatchEvent foul = events.get(index);
                Optional<MatchEvent> restart = playedEventAfter(events, index);
                if (foul.type() != EventType.FOUL || restart.isEmpty()) {
                    continue;
                }
                assertThat(restart.get().side()).isEqualTo(foul.side());
                assertThat(restart.get().zoneRole()).isEqualTo(foul.zoneRole());
                assertThat(restart.get().phase()).isEqualTo(PlayPhase.SET_PIECE);
                checked++;
            }
        }
        assertThat(checked).isPositive();
    }

    @Test
    void aFoulInTheAreaOfTheFoulerIsFollowedByAPenaltyAndAnyOtherFoulByAFreeKickOrADirectShot() {
        int penalties = 0;
        int freeKicks = 0;
        for (List<MatchEvent> events : defaultMatches) {
            for (int index = 0; index < events.size(); index++) {
                MatchEvent foul = events.get(index);
                Optional<MatchEvent> restart = playedEventAfter(events, index);
                if (foul.type() != EventType.FOUL || restart.isEmpty()) {
                    continue;
                }
                boolean penalty = ((EventDetail.Foul) foul.detail()).penalty();
                assertThat(penalty).isEqualTo(foul.zoneRole() == ZoneRole.OPPONENT_BOX);
                assertThat(isPenaltyShot(restart.get())).isEqualTo(penalty);
                if (penalty) {
                    penalties++;
                } else {
                    assertThat(isFreeKickRestart(restart.get())).isTrue();
                    freeKicks++;
                }
            }
        }
        assertThat(penalties).isPositive();
        assertThat(freeKicks).isPositive();
    }

    @Test
    void theActionAfterAFreeKickRestartsInOpenPlayFromTheSameZoneForTheSameSide() {
        int checked = 0;
        for (List<MatchEvent> events : defaultMatches) {
            for (int index = 0; index < events.size(); index++) {
                MatchEvent freeKick = events.get(index);
                Optional<MatchEvent> next = playedEventAfter(events, index);
                if (freeKick.type() != EventType.FREE_KICK || next.isEmpty()) {
                    continue;
                }
                assertThat(next.get().phase()).isEqualTo(PlayPhase.BUILD_UP);
                assertThat(next.get().side()).isEqualTo(freeKick.side());
                assertThat(next.get().zoneRole()).isEqualTo(freeKick.zoneRole());
                checked++;
            }
        }
        assertThat(checked).isPositive();
    }

    @Test
    void everyFoulNamesTheFoulerAndTheFouledPlayerFromOppositeSidesAndCarriesItsSeverity() {
        List<MatchEvent> fouls = defaultMatches.stream()
                .flatMap(List::stream)
                .filter(event -> event.type() == EventType.FOUL)
                .toList();

        assertThat(fouls).isNotEmpty().allSatisfy(foul -> {
            Player fouler = foul.participants().primary().orElseThrow();
            Player fouled = foul.participants().secondary().orElseThrow();
            String fouledTeam = foul.side() == MatchSide.HOME ? "Home" : "Away";
            assertThat(fouled.name()).startsWith(fouledTeam);
            assertThat(fouler.name()).doesNotStartWith(fouledTeam);
            assertThat(foul.detail()).isInstanceOf(EventDetail.Foul.class);
        });
        assertThat(fouls).extracting(foul -> ((EventDetail.Foul) foul.detail()).severity())
                .contains(FoulSeverity.values());
    }

    @Test
    void aTeamReducedToSixByExpulsionsSuspendsTheMatchImmediatelyAndNothingIsPlayedAfterwards() {
        List<List<MatchEvent>> matches = matchesWith(everyFoulARedCard());

        assertThat(matches).allSatisfy(events -> {
            MatchEvent last = events.get(events.size() - 1);
            assertThat(last.type()).isEqualTo(EventType.MATCH_SUSPENDED);
            assertThat(events).extracting(MatchEvent::type)
                    .doesNotContain(EventType.MATCH_END)
                    .containsOnlyOnce(EventType.MATCH_SUSPENDED);
            EventDetail.Suspension suspension = (EventDetail.Suspension) last.detail();
            MatchSide responsible = suspension.responsibleSide();
            assertThat(last.side()).isEqualTo(responsible);
            assertThat(redCardsOf(events, responsible)).isEqualTo(5);
            assertThat(redCardsOf(events, responsible.opposite())).isLessThan(5);
        });
    }

    @Test
    void aSuspendedMatchIsFinishedAndCannotBeSteppedFurther() {
        Match match = start(everyFoulARedCard(), 3L);
        while (!match.isFinished()) {
            match.step();
        }

        assertThat(match.events().get(match.events().size() - 1).type()).isEqualTo(EventType.MATCH_SUSPENDED);
        assertThatThrownBy(match::step).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theSuspensionCarriesTheScoreOnTheFieldAndTheOfficialResult() {
        List<List<MatchEvent>> matches = matchesWith(everyFoulARedCard());

        assertThat(matches).allSatisfy(events -> {
            EventDetail.Suspension suspension = (EventDetail.Suspension) events.get(events.size() - 1).detail();
            Score fieldFromGoals = scoreOfGoals(events);
            MatchSide winner = suspension.responsibleSide().opposite();
            Score official = suspension.officialScore();
            assertThat(suspension.fieldScore()).isEqualTo(fieldFromGoals);
            if (fieldFromGoals.leadOf(winner) >= 3) {
                assertThat(official).isEqualTo(fieldFromGoals);
            } else {
                assertThat(official.goalsOf(winner)).isEqualTo(3);
                assertThat(official.goalsOf(suspension.responsibleSide())).isZero();
            }
        });
    }

    @Test
    void theSuspensionIsSetInTheHalfWhereItHappenedAndNeverAsTheEndOfTheMatch() {
        List<List<MatchEvent>> matches = matchesWith(everyFoulARedCard());

        assertThat(matches).allSatisfy(events -> {
            MatchEvent last = events.get(events.size() - 1);
            assertThat(last.time().period()).isIn(MatchPeriod.FIRST_HALF, MatchPeriod.SECOND_HALF);
            assertThat(last.participants().primary()).isEmpty();
        });
    }

    @Test
    void aPenaltyConcededByTheFoulThatCostsTheFifthExpulsionIsNeverTaken() {
        int found = 0;
        for (List<MatchEvent> events : matchesWith(everyFoulARedCard(), 300)) {
            int size = events.size();
            MatchEvent foul = events.get(size - 3);
            boolean penaltyFoul = foul.type() == EventType.FOUL && ((EventDetail.Foul) foul.detail()).penalty();
            if (!penaltyFoul) {
                continue;
            }
            assertThat(events.get(size - 2).type()).isEqualTo(EventType.RED_CARD);
            assertThat(events.get(size - 1).type()).isEqualTo(EventType.MATCH_SUSPENDED);
            assertThat(events.stream().filter(event -> SHOT_TYPES.contains(event.type()))
                    .filter(this::isPenaltyShot)
                    .filter(event -> event.order() > foul.order())).isEmpty();
            found++;
        }
        assertThat(found).isPositive();
    }

    @Test
    void aSentOffPlayerNeverAppearsInAnyLaterEvent() {
        List<List<MatchEvent>> matches = matchesWith(everyFoulARedCard());

        assertThat(matches).allSatisfy(events -> {
            for (MatchEvent card : events) {
                if (card.type() != EventType.RED_CARD) {
                    continue;
                }
                Player sentOff = card.participants().primary().orElseThrow();
                assertThat(events.stream().filter(event -> event.order() > card.order()))
                        .noneMatch(event -> event.participants().primary().filter(sentOff::equals).isPresent()
                                || event.participants().secondary().filter(sentOff::equals).isPresent());
            }
        });
    }

    @Test
    void theSecondYellowOfAPlayerIsADistinctEventThatFollowsExactlyOneYellowOfTheSamePlayer() {
        List<List<MatchEvent>> matches = matchesWith(everyFoulAYellowCard());

        assertThat(matches).anySatisfy(events -> assertThat(events).extracting(MatchEvent::type)
                .contains(EventType.SECOND_YELLOW_CARD));
        assertThat(matches).allSatisfy(events -> {
            assertThat(events).extracting(MatchEvent::type).doesNotContain(EventType.RED_CARD);
            for (MatchEvent second : events) {
                if (second.type() != EventType.SECOND_YELLOW_CARD) {
                    continue;
                }
                Player booked = second.participants().primary().orElseThrow();
                long earlierYellows = events.stream()
                        .filter(event -> event.order() < second.order())
                        .filter(event -> event.type() == EventType.YELLOW_CARD)
                        .filter(event -> event.participants().primary().filter(booked::equals).isPresent())
                        .count();
                assertThat(earlierYellows).isEqualTo(1);
            }
        });
    }

    @Test
    void aPlayerWithAYellowGetsASecondYellowNotAnotherYellowAndIsThenSentOff() {
        List<List<MatchEvent>> matches = matchesWith(everyFoulAYellowCard());

        assertThat(matches).allSatisfy(events -> {
            for (MatchEvent yellow : events) {
                if (yellow.type() != EventType.YELLOW_CARD) {
                    continue;
                }
                Player booked = yellow.participants().primary().orElseThrow();
                assertThat(events.stream()
                        .filter(event -> event.order() > yellow.order())
                        .filter(event -> event.type() == EventType.YELLOW_CARD)).noneMatch(
                                event -> event.participants().primary().filter(booked::equals).isPresent());
            }
        });
    }

    @Test
    void theCardsAreRecordedForTheSideOfTheCardedPlayerRightAfterTheFoul() {
        List<List<MatchEvent>> matches = matchesWith(everyFoulAYellowCard());

        assertThat(matches).allSatisfy(events -> {
            for (int index = 1; index < events.size(); index++) {
                MatchEvent card = events.get(index);
                if (!card.type().isCard()) {
                    continue;
                }
                MatchEvent before = events.get(index - 1);
                assertThat(before.type()).isEqualTo(EventType.FOUL);
                assertThat(before.participants().primary()).isEqualTo(card.participants().primary());
                assertThat(card.side()).isEqualTo(before.side().opposite());
                assertThat(card.durationSeconds()).isZero();
            }
        });
    }

    @Test
    void theMatchesWithFoulsStayReproducibleForTheSameSeed() {
        List<MatchEvent> first = play(EngineConfig.defaults(), 9L);
        List<MatchEvent> second = play(EngineConfig.defaults(), 9L);

        assertThat(first).containsExactlyElementsOf(second);
    }

    private int redCardsOf(List<MatchEvent> events, MatchSide side) {
        return (int) events.stream()
                .filter(event -> event.type() == EventType.RED_CARD)
                .filter(event -> event.side() == side)
                .count();
    }

    private Score scoreOfGoals(List<MatchEvent> events) {
        Score score = Score.goalless();
        for (MatchEvent event : events) {
            if (event.type() == EventType.GOAL) {
                score = score.withGoalBy(event.side());
            }
        }
        return score;
    }

    private boolean isPenaltyShot(MatchEvent event) {
        return SHOT_TYPES.contains(event.type()) && profileOf(event).penalty();
    }

    private boolean isFreeKickRestart(MatchEvent event) {
        boolean freeKick = event.type() == EventType.FREE_KICK || event.type() == EventType.FREE_KICK_CROSS;
        boolean directShot = SHOT_TYPES.contains(event.type())
                && profileOf(event).kind() == ShotKind.DIRECT_FREE_KICK;
        return freeKick || directShot;
    }

    private ShotProfile profileOf(MatchEvent shot) {
        return switch (shot.detail()) {
            case EventDetail.Goal goal -> goal.profile();
            case EventDetail.Save save -> save.profile();
            case EventDetail.Shot detail -> detail.profile();
            default -> throw new IllegalStateException("not a shot detail: " + shot.detail());
        };
    }

    private Optional<MatchEvent> playedEventAfter(List<MatchEvent> events, int index) {
        int next = index + 1;
        while (next < events.size() && NOT_PLAYED_TYPES.contains(events.get(next).type())) {
            if (events.get(next).type() == EventType.FIRST_HALF_END
                    || events.get(next).type() == EventType.MATCH_END) {
                return Optional.empty();
            }
            next++;
        }
        if (next >= events.size() || events.get(next).type() == EventType.MATCH_SUSPENDED) {
            return Optional.empty();
        }
        return Optional.of(events.get(next));
    }

    private EngineConfig everyFoulARedCard() {
        return withEveryFoulCarded(0.0, 1.0);
    }

    private EngineConfig everyFoulAYellowCard() {
        return withEveryFoulCarded(1.0, 0.0);
    }

    private EngineConfig withEveryFoulCarded(double yellow, double red) {
        FoulConfig defaults = FoulConfig.defaults();
        Map<FoulSeverity, Double> yellows = new EnumMap<>(FoulSeverity.class);
        Map<FoulSeverity, Double> reds = new EnumMap<>(FoulSeverity.class);
        for (FoulSeverity severity : FoulSeverity.values()) {
            yellows.put(severity, yellow);
            reds.put(severity, red);
        }
        FoulConfig fouls = new FoulConfig(0.99, defaults.neutralTackling(), defaults.tacklingSlope(), 1.0,
                defaults.lightShare(), defaults.hardShare(), yellows, reds, new DurationRange(3, 8));
        return TestConfigs.withFoul(EngineConfig.defaults(), fouls);
    }

    private List<List<MatchEvent>> matchesWith(EngineConfig config) {
        return matchesWith(config, MATCHES);
    }

    private List<List<MatchEvent>> matchesWith(EngineConfig config, int count) {
        List<List<MatchEvent>> matches = new ArrayList<>();
        for (long seed = 1; seed <= count; seed++) {
            matches.add(play(config, seed));
        }
        return matches;
    }

    private List<MatchEvent> play(EngineConfig config, long seed) {
        Match match = start(config, seed);
        while (!match.isFinished()) {
            match.step();
        }
        return match.events();
    }

    private Match start(EngineConfig config, long seed) {
        return new MatchEngine(config).start(home, away, seed);
    }
}
