package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class MatchCounterAttackTest {

    private static final int MATCHES = 40;
    private static final Set<EventType> SHOT_TYPES = EnumSet.of(EventType.GOAL, EventType.SHOT_SAVED,
            EventType.SHOT_OFF_TARGET, EventType.SHOT_BLOCKED);
    private static final Set<EventType> NOT_ACTION_TYPES = EnumSet.of(EventType.KICK_OFF, EventType.STOPPAGE_ANNOUNCED,
            EventType.FIRST_HALF_END, EventType.SECOND_HALF_START, EventType.MATCH_END, EventType.INJURY,
            EventType.SUBSTITUTION);

    private final EngineConfig config = EngineConfig.defaults();
    private final Team home = TestTeams.team("Home", PlayStyle.COUNTER_ATTACK, 60);
    private final Team away = TestTeams.team("Away", PlayStyle.BALANCED, 60);
    private final List<List<MatchEvent>> matches = IntStream.rangeClosed(1, MATCHES)
            .mapToObj(this::play)
            .toList();

    @Test
    void counterAttacksAppearInAMatchAndEndInShots() {
        List<MatchEvent> inCounterAttack = counterAttackEvents();

        assertThat(inCounterAttack).isNotEmpty();
        assertThat(inCounterAttack).extracting(MatchEvent::type).anyMatch(SHOT_TYPES::contains);
    }

    @Test
    void aCounterAttackStartsRightAfterTheOtherSideLosesTheBallInTheirDefenceOrMidfield() {
        int checked = 0;
        for (List<MatchEvent> events : matches) {
            MatchEvent previous = null;
            for (MatchEvent event : events) {
                if (NOT_ACTION_TYPES.contains(event.type())) {
                    continue;
                }
                if (event.phase() == PlayPhase.COUNTER_ATTACK && !continuesARun(previous, event)) {
                    assertThat(previous).isNotNull();
                    assertThat(previous.type().isBallRecovery()).isTrue();
                    assertThat(previous.side()).isEqualTo(event.side().opposite());
                    assertThat(event.zoneRole()).isIn(ZoneRole.DEFENCE, ZoneRole.MIDFIELD);
                    checked++;
                }
                previous = event;
            }
        }
        assertThat(checked).isPositive();
    }

    @Test
    void inACounterAttackEveryActionIsAShortRunALostRunOrAShot() {
        List<MatchEvent> inCounterAttack = counterAttackEvents();

        assertThat(inCounterAttack).allSatisfy(event -> {
            boolean isRun = event.type() == EventType.DRIBBLE_COMPLETED || event.type() == EventType.TACKLE;
            if (isRun) {
                assertThat(config.counterAttack().advanceDuration().contains(event.durationSeconds())).isTrue();
            } else {
                assertThat(SHOT_TYPES).contains(event.type());
            }
        });
    }

    @Test
    void theStyleOfTheSideThatRecoversTheBallDecidesHowOftenItCounterAttacks() {
        long homeStarts = countStartsBy(MatchSide.HOME);
        long awayStarts = countStartsBy(MatchSide.AWAY);

        assertThat(homeStarts).isGreaterThan(awayStarts);
    }

    private long countStartsBy(MatchSide side) {
        long starts = 0;
        for (List<MatchEvent> events : matches) {
            MatchEvent previous = null;
            for (MatchEvent event : events) {
                if (NOT_ACTION_TYPES.contains(event.type())) {
                    continue;
                }
                boolean isStart = event.phase() == PlayPhase.COUNTER_ATTACK && !continuesARun(previous, event);
                if (isStart && event.side() == side) {
                    starts++;
                }
                previous = event;
            }
        }
        return starts;
    }

    private boolean continuesARun(MatchEvent previous, MatchEvent event) {
        return previous != null && previous.phase() == PlayPhase.COUNTER_ATTACK && previous.side() == event.side();
    }

    private List<MatchEvent> counterAttackEvents() {
        return matches.stream()
                .flatMap(List::stream)
                .filter(event -> event.phase() == PlayPhase.COUNTER_ATTACK)
                .filter(event -> !NOT_ACTION_TYPES.contains(event.type()))
                .toList();
    }

    private List<MatchEvent> play(long seed) {
        Match match = new MatchEngine(config).start(home, away, seed);
        while (!match.isFinished()) {
            match.step();
        }
        return match.events();
    }
}
