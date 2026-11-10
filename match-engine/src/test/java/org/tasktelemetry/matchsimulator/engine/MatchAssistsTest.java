package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.InjuryConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class MatchAssistsTest {

    private static final int MATCHES = 40;
    private static final int INJURY_PRONE_MATCHES = 300;
    private static final int HOMONYM_MATCHES = 3000;
    private static final double FREQUENT_INJURIES = 0.3;
    private static final double HOMONYM_INJURIES = 0.15;
    private static final Set<EventType> LEAVING_THE_PASS_ALIVE = EnumSet.of(EventType.DRIBBLE_COMPLETED,
            EventType.BALL_CIRCULATED, EventType.INJURY, EventType.SUBSTITUTION, EventType.STOPPAGE_ANNOUNCED);

    private final Team home = TestTeams.team("Home", PlayStyle.BALANCED, 60);
    private final Team away = TestTeams.team("Away", PlayStyle.BALANCED, 60);
    private final List<List<MatchEvent>> matches = IntStream.rangeClosed(1, MATCHES)
            .mapToObj(seed -> play(seed))
            .toList();

    @Test
    void aGoalOnOpenPlayWithoutACrossIsAssistedByTheLastPasserOfTheSameSideWhenThereIsOne() {
        int assisted = 0;
        for (List<MatchEvent> events : matches) {
            for (int index = 0; index < events.size(); index++) {
                MatchEvent goal = events.get(index);
                if (!isFootGoal(goal)) {
                    continue;
                }
                Optional<Player> expected = lastPasserBefore(events, index);
                assertThat(goal.participants().secondary()).as("goal %s", goal).isEqualTo(expected);
                assisted += expected.isPresent() ? 1 : 0;
            }
        }
        assertThat(assisted).isPositive();
    }

    @Test
    void aGoalFromACrossIsAssistedByTheCrosser() {
        int checked = 0;
        for (List<MatchEvent> events : matches) {
            for (int index = 1; index < events.size(); index++) {
                MatchEvent goal = events.get(index);
                if (!isGoalOfKind(goal, ShotKind.HEADER)) {
                    continue;
                }
                assertThat(goal.participants().secondary()).as("goal %s", goal).isNotEmpty();
                checked++;
            }
        }
        assertThat(checked).isPositive();
    }

    @Test
    void aGoalFromAPenaltyOrADirectFreeKickHasNoAssist() {
        List<MatchEvent> goals = matches.stream()
                .flatMap(List::stream)
                .filter(event -> isGoalOfKind(event, ShotKind.DIRECT_FREE_KICK) || isPenaltyGoal(event))
                .toList();

        assertThat(goals).allSatisfy(goal -> assertThat(goal.participants().secondary()).isEmpty());
    }

    @Test
    void theInjuryOfTheLastPasserCancelsTheAssistWhileTheInjuryOfAnyoneElseDoesNot() {
        List<List<MatchEvent>> injuryProne = IntStream.rangeClosed(1, INJURY_PRONE_MATCHES)
                .mapToObj(seed -> play(withInjuriesOf(FREQUENT_INJURIES), seed))
                .toList();
        int cancelled = 0;
        for (List<MatchEvent> events : injuryProne) {
            for (int index = 0; index < events.size(); index++) {
                MatchEvent goal = events.get(index);
                if (!isFootGoal(goal)) {
                    continue;
                }
                Optional<Player> remembered = passerRememberedBeforeInjuries(events, index);
                Optional<Player> expected = lastPasserBefore(events, index);
                assertThat(goal.participants().secondary()).as("goal %s", goal).isEqualTo(expected);
                cancelled += remembered.isPresent() && expected.isEmpty() ? 1 : 0;
            }
        }
        assertThat(cancelled).isPositive();
    }

    @Test
    void theInjuryOfASamenamedPlayerOfTheOpponentDoesNotCancelTheAssist() {
        List<List<MatchEvent>> injuryProne = IntStream.rangeClosed(1, HOMONYM_MATCHES)
                .mapToObj(seed -> play(withInjuriesOf(HOMONYM_INJURIES), home, home, seed))
                .toList();
        int cancelled = 0;
        int kept = 0;
        int homonym = 0;
        for (List<MatchEvent> events : injuryProne) {
            for (int index = 0; index < events.size(); index++) {
                MatchEvent goal = events.get(index);
                if (!isFootGoal(goal)) {
                    continue;
                }
                Optional<Player> remembered = passerRememberedBeforeInjuries(events, index);
                Optional<Player> expected = lastPasserBefore(events, index);
                assertThat(goal.participants().secondary()).as("goal %s", goal).isEqualTo(expected);
                cancelled += remembered.isPresent() && expected.isEmpty() ? 1 : 0;
                kept += expected.isPresent() ? 1 : 0;
                homonym += opponentHomonymInjured(events, index, remembered) ? 1 : 0;
            }
        }
        assertThat(cancelled).isPositive();
        assertThat(kept).isPositive();
        assertThat(homonym).as("homonym").isPositive();
    }

    private boolean opponentHomonymInjured(List<MatchEvent> events, int goalIndex, Optional<Player> passer) {
        MatchEvent goal = events.get(goalIndex);
        int index = indexOfTheEventThatEndsThePass(events, goalIndex);
        return passer.isPresent() && events.subList(index + 1, goalIndex).stream()
                .anyMatch(event -> event.type() == EventType.INJURY && event.side() != goal.side()
                        && event.participants().primary().equals(passer));
    }

    private Optional<Player> lastPasserBefore(List<MatchEvent> events, int goalIndex) {
        Optional<Player> passer = passerRememberedBeforeInjuries(events, goalIndex);
        Set<Player> injured = injuredSinceThePass(events, goalIndex);
        return passer.filter(player -> !injured.contains(player));
    }

    private Optional<Player> passerRememberedBeforeInjuries(List<MatchEvent> events, int goalIndex) {
        MatchEvent goal = events.get(goalIndex);
        int index = indexOfTheEventThatEndsThePass(events, goalIndex);
        if (index < 0) {
            return Optional.empty();
        }
        MatchEvent previous = events.get(index);
        boolean isPassOfTheSameSide = previous.type() == EventType.PASS_COMPLETED && previous.side() == goal.side();
        Optional<Player> passer = previous.participants().primary();
        if (isPassOfTheSameSide && !passer.equals(goal.participants().primary())) {
            return passer;
        }
        return Optional.empty();
    }

    private Set<Player> injuredSinceThePass(List<MatchEvent> events, int goalIndex) {
        MatchEvent goal = events.get(goalIndex);
        int index = indexOfTheEventThatEndsThePass(events, goalIndex);
        return events.subList(index + 1, goalIndex).stream()
                .filter(event -> event.type() == EventType.INJURY && event.side() == goal.side())
                .flatMap(event -> event.participants().primary().stream())
                .collect(Collectors.toSet());
    }

    private int indexOfTheEventThatEndsThePass(List<MatchEvent> events, int goalIndex) {
        int index = goalIndex - 1;
        while (index >= 0 && LEAVING_THE_PASS_ALIVE.contains(events.get(index).type())) {
            index--;
        }
        return index;
    }

    private boolean isFootGoal(MatchEvent event) {
        return isGoalOfKind(event, ShotKind.FOOT);
    }

    private boolean isGoalOfKind(MatchEvent event, ShotKind kind) {
        return event.detail() instanceof EventDetail.Goal goal && !goal.profile().penalty()
                && goal.profile().kind() == kind;
    }

    private boolean isPenaltyGoal(MatchEvent event) {
        return event.detail() instanceof EventDetail.Goal goal && goal.profile().penalty();
    }

    private EngineConfig withInjuriesOf(double probability) {
        EngineConfig defaults = EngineConfig.defaults();
        InjuryConfig injury = new InjuryConfig(probability, defaults.injury().duration());
        return TestConfigs.withInjury(defaults, injury);
    }

    private List<MatchEvent> play(long seed) {
        return play(EngineConfig.defaults(), seed);
    }

    private List<MatchEvent> play(EngineConfig config, long seed) {
        return play(config, home, away, seed);
    }

    private List<MatchEvent> play(EngineConfig config, Team homeTeam, Team awayTeam, long seed) {
        Match match = new MatchEngine(config).start(homeTeam, awayTeam, seed);
        while (!match.isFinished()) {
            match.step();
        }
        return match.events();
    }
}
