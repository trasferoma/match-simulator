package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class MatchSidesAndBallTest {

    private static final int MATCHES = 40;

    private final Team home = TestTeams.team("Home", PlayStyle.BALANCED, 60);
    private final Team away = TestTeams.team("Away", PlayStyle.BALANCED, 60);
    private final List<MatchEvent> events = IntStream.rangeClosed(1, MATCHES)
            .mapToObj(seed -> play(seed))
            .flatMap(List::stream)
            .toList();

    @Test
    void everyParticipantIsRecordedWithTheSideOfItsOwnTeam() {
        assertThat(events).anyMatch(event -> event.participants().primary().isPresent());
        assertThat(events).allSatisfy(event -> {
            Participants participants = event.participants();
            participants.primary().ifPresent(player -> assertThat(sideOfTeam(player))
                    .isEqualTo(participants.primarySide().orElseThrow()));
            participants.secondary().ifPresent(player -> assertThat(sideOfTeam(player))
                    .isEqualTo(participants.secondarySide().orElseThrow()));
        });
    }

    @Test
    void theBallRecovererOfAnInterceptionOrATackleBelongsToTheSideWithoutPossession() {
        List<MatchEvent> recoveries = eventsMatching(event -> event.type().isBallRecovery());

        assertThat(recoveries).isNotEmpty().allSatisfy(recovery -> {
            assertThat(recovery.participants().primarySide()).contains(recovery.side().opposite());
            assertThat(recovery.ball().possession()).isEqualTo(recovery.side().opposite());
        });
    }

    @Test
    void aFoulNamesTheFoulerOfTheDefendingSideAndTheFouledPlayerOfTheSideInPossession() {
        List<MatchEvent> fouls = eventsOfType(EventType.FOUL);

        assertThat(fouls).isNotEmpty().allSatisfy(foul -> {
            assertThat(foul.participants().primarySide()).contains(foul.side().opposite());
            assertThat(foul.participants().secondarySide()).contains(foul.side());
        });
    }

    @Test
    void aSavedShotNamesTheShooterOfTheSideInPossessionAndTheKeeperOfTheOpponents() {
        List<MatchEvent> saves = eventsOfType(EventType.SHOT_SAVED);

        assertThat(saves).isNotEmpty().allSatisfy(save -> {
            assertThat(save.participants().primarySide()).contains(save.side());
            assertThat(save.participants().secondarySide()).contains(save.side().opposite());
        });
    }

    @Test
    void anOffsideNamesTheReceiverAndThePasserOfTheSideInPossession() {
        List<MatchEvent> offsides = eventsOfType(EventType.OFFSIDE);

        assertThat(offsides).isNotEmpty().allSatisfy(offside -> {
            assertThat(offside.participants().primarySide()).contains(offside.side());
            assertThat(offside.participants().secondarySide()).contains(offside.side());
        });
    }

    @Test
    void aCardNamesAPlayerOfTheSideItIsRecordedFor() {
        List<MatchEvent> cards = eventsMatching(event -> event.type().isCard());

        assertThat(cards).isNotEmpty().allSatisfy(card ->
                assertThat(card.participants().primarySide()).contains(card.side()));
    }

    @Test
    void aClearanceNamesTheDefenderOfTheOpponentsAndTheCrosserOfTheSideInPossession() {
        List<MatchEvent> clearances = eventsOfType(EventType.CLEARANCE);

        assertThat(clearances).isNotEmpty().allSatisfy(clearance -> {
            assertThat(clearance.participants().primarySide()).contains(clearance.side().opposite());
            assertThat(clearance.participants().secondarySide()).contains(clearance.side());
        });
    }

    @Test
    void aGoalHasTheBallInTheOpponentBoxOfTheScoringSideNotAtTheFollowingKickOff() {
        List<MatchEvent> goals = eventsOfType(EventType.GOAL);

        assertThat(goals).isNotEmpty().allSatisfy(goal -> {
            assertThat(goal.ball().possession()).isEqualTo(goal.side());
            assertThat(goal.ball().zoneRole()).isEqualTo(ZoneRole.OPPONENT_BOX);
        });
    }

    @Test
    void aKickOffHasTheBallAtTheCentreForTheSideThatKicksOff() {
        List<MatchEvent> kickOffs = eventsMatching(event -> event.type() == EventType.KICK_OFF
                || event.type() == EventType.SECOND_HALF_START || event.type() == EventType.KICK_OFF_AFTER_GOAL);

        assertThat(kickOffs).isNotEmpty().allSatisfy(kickOff -> {
            assertThat(kickOff.ball().possession()).isEqualTo(kickOff.side());
            assertThat(kickOff.ball().zoneRole()).isEqualTo(ZoneRole.MIDFIELD);
        });
    }

    @Test
    void aCompletedPassHasTheBallWithTheSameSideOneZoneAhead() {
        List<MatchEvent> passes = eventsOfType(EventType.PASS_COMPLETED);

        assertThat(passes).isNotEmpty().allSatisfy(pass -> {
            assertThat(pass.ball().possession()).isEqualTo(pass.side());
            assertThat(pass.ball().zoneRole().ordinal()).isEqualTo(pass.zoneRole().ordinal() + 1);
        });
    }

    @Test
    void aFoulHasTheBallInTheZoneOfTheFoulForTheFouledSide() {
        List<MatchEvent> fouls = eventsOfType(EventType.FOUL);

        assertThat(fouls).isNotEmpty().allSatisfy(foul -> {
            assertThat(foul.ball().possession()).isEqualTo(foul.side());
            assertThat(foul.ball().zoneRole()).isEqualTo(foul.zoneRole());
        });
    }

    private List<MatchEvent> play(long seed) {
        Match match = new MatchEngine(EngineConfig.defaults()).start(home, away, seed);
        while (!match.isFinished()) {
            match.step();
        }
        return match.events();
    }

    private List<MatchEvent> eventsOfType(EventType type) {
        return eventsMatching(event -> event.type() == type);
    }

    private List<MatchEvent> eventsMatching(Predicate<MatchEvent> condition) {
        return events.stream()
                .filter(condition)
                .toList();
    }

    private MatchSide sideOfTeam(Player player) {
        return isInSquad(home, player) ? MatchSide.HOME : MatchSide.AWAY;
    }

    private boolean isInSquad(Team team, Player player) {
        return team.starters().contains(player) || team.bench().contains(player);
    }
}
