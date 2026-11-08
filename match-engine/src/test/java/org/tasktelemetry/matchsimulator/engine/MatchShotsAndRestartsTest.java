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
import org.tasktelemetry.matchsimulator.engine.event.GoalContext;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class MatchShotsAndRestartsTest {

    private static final int MATCHES = 40;
    private static final Set<EventType> SHOT_TYPES = EnumSet.of(EventType.GOAL, EventType.SHOT_SAVED,
            EventType.SHOT_OFF_TARGET, EventType.SHOT_BLOCKED);
    private static final Set<EventType> PERIOD_END_TYPES = EnumSet.of(EventType.FIRST_HALF_END,
            EventType.MATCH_END);

    private final EngineConfig config = EngineConfig.defaults();
    private final Team home = TestTeams.team("Home", PlayStyle.BALANCED, 60);
    private final Team away = TestTeams.team("Away", PlayStyle.BALANCED, 60);
    private final List<List<MatchEvent>> matches = IntStream.rangeClosed(1, MATCHES)
            .mapToObj(seed -> play(seed))
            .toList();

    @Test
    void everyShotEventHasTheDetailAndTheParticipantsOfItsType() {
        List<MatchEvent> shots = eventsOf(SHOT_TYPES);

        assertThat(shots).isNotEmpty().allSatisfy(shot -> {
            assertThat(shot.participants().primary()).isPresent();
            assertThat(shot.phase()).isIn(PlayPhase.BUILD_UP, PlayPhase.SET_PIECE);
            assertThat(shot.zoneRole()).isEqualTo(ZoneRole.OPPONENT_BOX);
            assertThat(shooterOf(shot).role()).isEqualTo(Role.FORWARD);
            assertThat(shooterOf(shot).name()).startsWith(shot.side() == MatchSide.HOME ? "Home" : "Away");
            assertThat(detailMatchesType(shot)).isTrue();
        });
    }

    @Test
    void theGoalkeeperOfTheDefendersIsTheSecondaryPlayerOfEverySave() {
        List<MatchEvent> saves = eventsOf(EnumSet.of(EventType.SHOT_SAVED));

        assertThat(saves).isNotEmpty().allSatisfy(save -> {
            Player keeper = save.participants().secondary().orElseThrow();
            assertThat(keeper.role()).isEqualTo(Role.GOALKEEPER);
            assertThat(keeper.name()).startsWith(save.side() == MatchSide.HOME ? "Away" : "Home");
        });
    }

    @Test
    void aShotOffTargetIsFollowedByTheGoalKickOfTheDefendersInTheirBoxAndThenByOpenPlayFromThere() {
        int checked = 0;
        for (List<MatchEvent> events : matches) {
            for (int index = 0; index < events.size(); index++) {
                MatchEvent shot = events.get(index);
                if (shot.type() != EventType.SHOT_OFF_TARGET) {
                    continue;
                }
                Optional<Integer> goalKickIndex = playedEventAfter(events, index);
                if (goalKickIndex.isEmpty()) {
                    continue;
                }
                MatchEvent goalKick = events.get(goalKickIndex.get());
                assertThat(goalKick.type()).isEqualTo(EventType.GOAL_KICK);
                assertThat(goalKick.side()).isEqualTo(shot.side().opposite());
                assertThat(goalKick.zoneRole()).isEqualTo(ZoneRole.OWN_BOX);
                assertThat(goalKick.phase()).isEqualTo(PlayPhase.SET_PIECE);
                assertThat(goalKick.participants().primary().orElseThrow().role()).isEqualTo(Role.GOALKEEPER);
                checked++;
                Optional<Integer> restartIndex = playedEventAfter(events, goalKickIndex.get());
                if (restartIndex.isPresent()) {
                    MatchEvent restart = events.get(restartIndex.get());
                    assertThat(restart.phase()).isEqualTo(PlayPhase.BUILD_UP);
                    assertThat(restart.side()).isEqualTo(goalKick.side());
                    assertThat(restart.zoneRole()).isEqualTo(ZoneRole.OWN_BOX);
                }
            }
        }
        assertThat(checked).isPositive();
    }

    @Test
    void aGoalIsFollowedByTheKickOffOfTheConcedingSideInTheCentreAndThenByOpenPlayFromThere() {
        int checked = 0;
        for (List<MatchEvent> events : matches) {
            for (int index = 0; index < events.size(); index++) {
                MatchEvent goal = events.get(index);
                if (goal.type() != EventType.GOAL) {
                    continue;
                }
                Optional<Integer> kickOffIndex = playedEventAfter(events, index);
                if (kickOffIndex.isEmpty()) {
                    continue;
                }
                MatchEvent kickOff = events.get(kickOffIndex.get());
                assertThat(kickOff.type()).isEqualTo(EventType.KICK_OFF_AFTER_GOAL);
                assertThat(kickOff.side()).isEqualTo(goal.side().opposite());
                assertThat(kickOff.zoneRole()).isEqualTo(ZoneRole.MIDFIELD);
                assertThat(kickOff.phase()).isEqualTo(PlayPhase.SET_PIECE);
                assertThat(kickOff.durationSeconds()).isZero();
                assertThat(kickOff.participants()).isEqualTo(Participants.nobody());
                checked++;
                Optional<Integer> restartIndex = playedEventAfter(events, kickOffIndex.get());
                if (restartIndex.isPresent()) {
                    MatchEvent restart = events.get(restartIndex.get());
                    assertThat(restart.phase()).isEqualTo(PlayPhase.BUILD_UP);
                    assertThat(restart.side()).isEqualTo(kickOff.side());
                    assertThat(restart.zoneRole()).isEqualTo(ZoneRole.MIDFIELD);
                }
            }
        }
        assertThat(checked).isPositive();
    }

    @Test
    void aSavedShotIsFollowedByOpenPlayOfTheKeepingSideInItsOwnBoxOrByACornerForTheAttackers() {
        int checked = 0;
        for (List<MatchEvent> events : matches) {
            for (int index = 0; index < events.size(); index++) {
                MatchEvent save = events.get(index);
                if (save.type() != EventType.SHOT_SAVED) {
                    continue;
                }
                Optional<Integer> nextIndex = playedEventAfter(events, index);
                if (nextIndex.isEmpty()) {
                    continue;
                }
                MatchEvent next = events.get(nextIndex.get());
                if (next.type() == EventType.CORNER_KICK) {
                    assertThat(next.side()).isEqualTo(save.side());
                    assertThat(next.zoneRole()).isEqualTo(ZoneRole.OPPONENT_BOX);
                    continue;
                }
                assertThat(next.phase()).isEqualTo(PlayPhase.BUILD_UP);
                assertThat(next.side()).isEqualTo(save.side().opposite());
                assertThat(next.zoneRole()).isEqualTo(ZoneRole.OWN_BOX);
                checked++;
            }
        }
        assertThat(checked).isPositive();
    }

    @Test
    void theContextOfEveryGoalIsReadFromTheScoreBuiltByThePreviousGoals() {
        int goals = 0;
        for (List<MatchEvent> events : matches) {
            int homeGoals = 0;
            int awayGoals = 0;
            for (MatchEvent event : events) {
                if (event.type() != EventType.GOAL) {
                    continue;
                }
                boolean homeScores = event.side() == MatchSide.HOME;
                int lead = homeScores ? homeGoals - awayGoals : awayGoals - homeGoals;
                assertThat(goalContextOf(event)).isEqualTo(expectedContext(lead));
                homeGoals += homeScores ? 1 : 0;
                awayGoals += homeScores ? 0 : 1;
                goals++;
            }
        }
        assertThat(goals).isPositive();
    }

    @Test
    void shotsAndRestartsStayInsideTheirConfiguredDurations() {
        assertThat(eventsOf(EnumSet.of(EventType.GOAL))).isNotEmpty().allSatisfy(event ->
                assertThat(config.shot().goalWithRestartDuration().contains(event.durationSeconds())).isTrue());
        assertThat(eventsOf(EnumSet.of(EventType.SHOT_SAVED, EventType.SHOT_OFF_TARGET, EventType.SHOT_BLOCKED)))
                .isNotEmpty().allSatisfy(event ->
                        assertThat(config.shot().shotDuration().contains(event.durationSeconds())).isTrue());
        assertThat(eventsOf(EnumSet.of(EventType.GOAL_KICK))).isNotEmpty().allSatisfy(event -> {
            assertThat(config.setPiece().goalKickDuration().contains(event.durationSeconds())).isTrue();
            assertThat(event.phase()).isEqualTo(PlayPhase.SET_PIECE);
        });
    }

    private GoalContext expectedContext(int leadBeforeTheGoal) {
        if (leadBeforeTheGoal > 0) {
            return GoalContext.EXTENDS_THE_LEAD;
        }
        if (leadBeforeTheGoal == 0) {
            return GoalContext.TAKES_THE_LEAD;
        }
        if (leadBeforeTheGoal == -1) {
            return GoalContext.EQUALISER;
        }
        return GoalContext.CONSOLATION;
    }

    private GoalContext goalContextOf(MatchEvent goal) {
        return ((EventDetail.Goal) goal.detail()).context();
    }

    private boolean detailMatchesType(MatchEvent shot) {
        return switch (shot.type()) {
            case GOAL -> shot.detail() instanceof EventDetail.Goal;
            case SHOT_SAVED -> shot.detail() instanceof EventDetail.Save;
            default -> shot.detail() instanceof EventDetail.Shot;
        };
    }

    private Player shooterOf(MatchEvent shot) {
        return shot.participants().primary().orElseThrow();
    }

    private Optional<Integer> playedEventAfter(List<MatchEvent> events, int index) {
        int next = index + 1;
        while (next < events.size() && events.get(next).type() == EventType.STOPPAGE_ANNOUNCED) {
            next++;
        }
        boolean periodEndsFirst = next >= events.size() || PERIOD_END_TYPES.contains(events.get(next).type());
        if (periodEndsFirst) {
            return Optional.empty();
        }
        return Optional.of(next);
    }

    private List<MatchEvent> eventsOf(Set<EventType> types) {
        List<MatchEvent> selected = new ArrayList<>();
        for (List<MatchEvent> events : matches) {
            for (MatchEvent event : events) {
                if (types.contains(event.type())) {
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
}
