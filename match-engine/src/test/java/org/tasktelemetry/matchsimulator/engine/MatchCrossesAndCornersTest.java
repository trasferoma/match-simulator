package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class MatchCrossesAndCornersTest {

    private static final int MATCHES = 40;
    private static final Set<EventType> SHOT_TYPES = EnumSet.of(EventType.GOAL, EventType.SHOT_SAVED,
            EventType.SHOT_OFF_TARGET, EventType.SHOT_BLOCKED);
    private static final Set<EventType> AERIAL_OUTCOMES = EnumSet.of(EventType.GOAL, EventType.SHOT_SAVED,
            EventType.SHOT_OFF_TARGET, EventType.SHOT_BLOCKED, EventType.CLEARANCE);
    private static final Set<EventType> CORNER_CAUSES = EnumSet.of(EventType.SHOT_SAVED, EventType.SHOT_BLOCKED,
            EventType.CLEARANCE, EventType.CROSS_DEFLECTED_TO_CORNER);
    private static final Set<EventType> CROSS_FAILURES = EnumSet.of(EventType.CROSS_FAILED,
            EventType.CROSS_DEFLECTED_TO_CORNER);
    private static final Set<EventType> PERIOD_END_TYPES = EnumSet.of(EventType.FIRST_HALF_END,
            EventType.MATCH_END);

    private final EngineConfig config = EngineConfig.defaults();
    private final Team home = TestTeams.team("Home", PlayStyle.BALANCED, 60);
    private final Team away = TestTeams.team("Away", PlayStyle.BALANCED, 60);
    private final List<List<MatchEvent>> matches = IntStream.rangeClosed(1, MATCHES)
            .mapToObj(this::play)
            .toList();

    @Test
    void crossesCornersAndHeadersAppearInAMatch() {
        assertThat(eventsOf(EventType.CROSS_COMPLETED)).isNotEmpty();
        assertThat(eventsOf(EventType.CROSS_FAILED)).isNotEmpty();
        assertThat(eventsOf(EventType.CROSS_DEFLECTED_TO_CORNER)).isNotEmpty();
        assertThat(eventsOf(EventType.CORNER_KICK)).isNotEmpty();
        assertThat(eventsOf(EventType.CLEARANCE)).isNotEmpty();
        assertThat(headerShots()).isNotEmpty();
    }

    @Test
    void aCrossIsAttemptedOnlyOnTheAttackingThirdInOpenPlayOrFromACornerOrAFreeKickCross() {
        List<MatchEvent> crosses = eventsOf(EventType.CROSS_COMPLETED);
        crosses.addAll(eventsOf(EventType.CROSS_FAILED));
        crosses.addAll(eventsOf(EventType.CROSS_DEFLECTED_TO_CORNER));

        assertThat(crosses).isNotEmpty().allSatisfy(cross -> {
            boolean inOpenPlay = cross.phase() == PlayPhase.BUILD_UP && cross.zoneRole() == ZoneRole.ATTACKING_THIRD;
            boolean fromASetPiece = cross.phase() == PlayPhase.SET_PIECE && cross.zoneRole() == ZoneRole.OPPONENT_BOX;
            assertThat(inOpenPlay || fromASetPiece).isTrue();
        });
        assertThat(eventsOf(EventType.CROSS_COMPLETED)).allSatisfy(cross -> {
            assertThat(cross.zoneRole()).isEqualTo(ZoneRole.ATTACKING_THIRD);
            assertThat(cross.phase()).isEqualTo(PlayPhase.BUILD_UP);
        });
    }

    @Test
    void crossersComeFromEveryLineAndNeverFromTheGoalkeeper() {
        List<MatchEvent> crosses = eventsOf(EventType.CROSS_COMPLETED);
        crosses.addAll(eventsOf(EventType.CORNER_KICK));

        assertThat(crosses).extracting(cross -> cross.participants().primary().orElseThrow().role())
                .contains(Role.DEFENDER, Role.MIDFIELDER, Role.FORWARD)
                .doesNotContain(Role.GOALKEEPER);
    }

    @Test
    void aCompletedCrossIsFollowedByAHeaderOrAClearanceOfTheSameAttackersThatNamesTheCrosser() {
        int checked = 0;
        for (List<MatchEvent> events : matches) {
            for (int index = 0; index < events.size(); index++) {
                MatchEvent cross = events.get(index);
                Optional<MatchEvent> next = playedEventAfter(events, index);
                if (cross.type() != EventType.CROSS_COMPLETED || next.isEmpty()) {
                    continue;
                }
                assertAerialOutcome(cross, next.get(), PlayPhase.BUILD_UP);
                checked++;
            }
        }
        assertThat(checked).isPositive();
    }

    @Test
    void aFailedCrossGivesTheBallToTheDefendersInTheirOwnBox() {
        int checked = 0;
        for (List<MatchEvent> events : matches) {
            for (int index = 0; index < events.size(); index++) {
                MatchEvent cross = events.get(index);
                Optional<MatchEvent> next = playedEventAfter(events, index);
                if (cross.type() != EventType.CROSS_FAILED || next.isEmpty()) {
                    continue;
                }
                assertThat(next.get().side()).isEqualTo(cross.side().opposite());
                assertThat(next.get().zoneRole()).isEqualTo(ZoneRole.OWN_BOX);
                assertThat(next.get().phase()).isEqualTo(PlayPhase.BUILD_UP);
                checked++;
            }
        }
        assertThat(checked).isPositive();
    }

    @Test
    void aCornerKickIsTakenInTheSetPiecePhaseAndFollowedByAHeaderAClearanceOrAFailedCrossOfTheSameAttackers() {
        int checked = 0;
        for (List<MatchEvent> events : matches) {
            for (int index = 0; index < events.size(); index++) {
                MatchEvent corner = events.get(index);
                Optional<MatchEvent> next = playedEventAfter(events, index);
                if (corner.type() != EventType.CORNER_KICK || next.isEmpty()) {
                    continue;
                }
                assertThat(corner.phase()).isEqualTo(PlayPhase.SET_PIECE);
                assertThat(corner.zoneRole()).isEqualTo(ZoneRole.OPPONENT_BOX);
                if (CROSS_FAILURES.contains(next.get().type())) {
                    assertThat(next.get().side()).isEqualTo(corner.side());
                    assertThat(next.get().phase()).isEqualTo(PlayPhase.SET_PIECE);
                } else {
                    assertAerialOutcome(corner, next.get(), PlayPhase.SET_PIECE);
                }
                checked++;
            }
        }
        assertThat(checked).isPositive();
    }

    @Test
    void aCornerIsAlwaysAwardedByASaveABlockAClearanceOrADeflectedCrossOfTheSameAttackers() {
        int checked = 0;
        for (List<MatchEvent> events : matches) {
            for (int index = 0; index < events.size(); index++) {
                MatchEvent corner = events.get(index);
                if (corner.type() != EventType.CORNER_KICK) {
                    continue;
                }
                MatchEvent cause = playedEventBefore(events, index);
                assertThat(cause.type()).isIn(CORNER_CAUSES);
                assertThat(cause.side()).isEqualTo(corner.side());
                checked++;
            }
        }
        assertThat(checked).isPositive();
    }

    private void assertAerialOutcome(MatchEvent delivery, MatchEvent outcome, PlayPhase phase) {
        Player crosser = delivery.participants().primary().orElseThrow();
        assertThat(outcome.type()).isIn(AERIAL_OUTCOMES);
        assertThat(outcome.side()).isEqualTo(delivery.side());
        assertThat(outcome.zoneRole()).isEqualTo(ZoneRole.OPPONENT_BOX);
        assertThat(outcome.phase()).isEqualTo(phase);
        if (outcome.type() == EventType.SHOT_SAVED) {
            assertThat(shotKindOf(outcome.detail())).isEqualTo(ShotKind.HEADER);
            return;
        }
        assertThat(outcome.participants().secondary()).contains(crosser);
        if (outcome.type() != EventType.CLEARANCE) {
            assertThat(shotKindOf(outcome.detail())).isEqualTo(ShotKind.HEADER);
        }
    }

    private ShotKind shotKindOf(EventDetail detail) {
        return switch (detail) {
            case EventDetail.Shot shot -> shot.profile().kind();
            case EventDetail.Save save -> save.profile().kind();
            case EventDetail.Goal goal -> goal.profile().kind();
            default -> throw new AssertionError("not a shot detail: " + detail);
        };
    }

    private List<MatchEvent> headerShots() {
        List<MatchEvent> headers = new ArrayList<>();
        for (List<MatchEvent> events : matches) {
            for (MatchEvent event : events) {
                if (SHOT_TYPES.contains(event.type()) && shotKindOf(event.detail()) == ShotKind.HEADER) {
                    headers.add(event);
                }
            }
        }
        return headers;
    }

    private Optional<MatchEvent> playedEventAfter(List<MatchEvent> events, int index) {
        int next = index + 1;
        while (next < events.size() && isInterruption(events.get(next))) {
            next++;
        }
        boolean periodEndsFirst = next >= events.size() || PERIOD_END_TYPES.contains(events.get(next).type());
        if (periodEndsFirst) {
            return Optional.empty();
        }
        return Optional.of(events.get(next));
    }

    private MatchEvent playedEventBefore(List<MatchEvent> events, int index) {
        int previous = index - 1;
        while (isInterruption(events.get(previous))) {
            previous--;
        }
        return events.get(previous);
    }

    private List<MatchEvent> eventsOf(EventType type) {
        List<MatchEvent> selected = new ArrayList<>();
        for (List<MatchEvent> events : matches) {
            for (MatchEvent event : events) {
                if (event.type() == type) {
                    selected.add(event);
                }
            }
        }
        return selected;
    }

    private List<MatchEvent> play(long seed) {
        Match match = new MatchEngine(config).start(home, away, seed);
        while (!match.isFinished()) {
            match.step();
        }
        return match.events();
    }

    private boolean isInterruption(MatchEvent event) {
        return event.type() == EventType.STOPPAGE_ANNOUNCED || event.type() == EventType.INJURY
                || event.type() == EventType.SUBSTITUTION;
    }
}
