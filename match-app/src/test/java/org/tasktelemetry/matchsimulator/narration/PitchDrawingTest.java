package org.tasktelemetry.matchsimulator.narration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.TEAM_NAMES;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.BallPosition;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.state.Score;

class PitchDrawingTest {

    private static final Score GOALLESS = Score.goalless();

    private final PitchDrawing drawing = new PitchDrawing();

    @ParameterizedTest
    @CsvSource({
            "OWN_BOX, |>____|",
            "DEFENCE, |_>___|",
            "MIDFIELD, |__>__|",
            "ATTACKING_THIRD, |___>_|",
            "OPPONENT_BOX, |____>|"})
    void theHomeSideInPossessionIsDrawnWithAnArrowTowardsTheRightFromItsOwnBoxToTheOpponentBox(
            ZoneRole zoneRole, String field) {
        MatchEvent pass = eventWithBall(EventType.PASS_COMPLETED, MatchSide.HOME, MatchSide.HOME, zoneRole);

        String line = drawing.draw(pass, TEAM_NAMES, GOALLESS);

        assertThat(line).isEqualTo("    Aquile " + field + " Lupi (0 - 0)");
    }

    @ParameterizedTest
    @CsvSource({
            "OWN_BOX, |____<|",
            "DEFENCE, |___<_|",
            "MIDFIELD, |__<__|",
            "ATTACKING_THIRD, |_<___|",
            "OPPONENT_BOX, |<____|"})
    void theAwaySideInPossessionIsDrawnWithAnArrowTowardsTheLeftFromItsOwnBoxToTheOpponentBox(
            ZoneRole zoneRole, String field) {
        MatchEvent pass = eventWithBall(EventType.PASS_COMPLETED, MatchSide.AWAY, MatchSide.AWAY, zoneRole);

        String line = drawing.draw(pass, TEAM_NAMES, GOALLESS);

        assertThat(line).isEqualTo("    Aquile " + field + " Lupi (0 - 0)");
    }

    @Test
    void theArrowFollowsTheSideWithTheBallAfterTheEventNotTheSideThatActed() {
        MatchEvent interception = eventWithBall(EventType.INTERCEPTION, MatchSide.HOME, MatchSide.AWAY,
                ZoneRole.DEFENCE);

        String line = drawing.draw(interception, TEAM_NAMES, GOALLESS);

        assertThat(line).isEqualTo("    Aquile |___<_| Lupi (0 - 0)");
    }

    @ParameterizedTest
    @CsvSource({
            "INJURY, HOME, OPPONENT_BOX, |____O|",
            "INJURY, AWAY, ATTACKING_THIRD, |_O___|"})
    void aStoppedPlayEventDrawsABallLetterInTheZoneOfTheEvent(EventType type, MatchSide possession,
            ZoneRole zoneRole, String field) {
        MatchEvent stopped = eventWithBall(type, possession, possession, zoneRole);

        String line = drawing.draw(stopped, TEAM_NAMES, GOALLESS);

        assertThat(line).isEqualTo("    Aquile " + field + " Lupi (0 - 0)");
    }

    @ParameterizedTest
    @CsvSource({
            "FOUL, HOME, OWN_BOX, |>____|",
            "YELLOW_CARD, HOME, ATTACKING_THIRD, |___>_|",
            "SECOND_YELLOW_CARD, AWAY, OWN_BOX, |____<|",
            "RED_CARD, AWAY, DEFENCE, |___<_|"})
    void aFoulAndItsCardsDrawTheArrowOfTheTeamThatSufferedTheFoulInTheZoneOfTheFoul(EventType type,
            MatchSide fouledSide, ZoneRole zoneRole, String field) {
        MatchSide foulingSide = fouledSide.opposite();
        MatchEvent foul = eventWithBall(type, foulingSide, fouledSide, zoneRole);

        String line = drawing.draw(foul, TEAM_NAMES, GOALLESS);

        assertThat(line).isEqualTo("    Aquile " + field + " Lupi (0 - 0)");
    }

    @ParameterizedTest
    @CsvSource({
            "HOME, |___<_|",
            "AWAY, |_>___|"})
    void anOffsideDrawsTheArrowOfTheDefendingTeamThatTakesTheFreeKick(MatchSide offsideSide, String field) {
        MatchSide defendingSide = offsideSide.opposite();
        MatchEvent offside = eventWithBall(EventType.OFFSIDE, offsideSide, defendingSide, ZoneRole.DEFENCE);

        String line = drawing.draw(offside, TEAM_NAMES, GOALLESS);

        assertThat(line).isEqualTo("    Aquile " + field + " Lupi (0 - 0)");
    }

    @ParameterizedTest
    @CsvSource({
            "STOPPAGE_ANNOUNCED, HOME, |____O|",
            "FIRST_HALF_END, AWAY, |O____|",
            "MATCH_END, HOME, |____O|",
            "MATCH_SUSPENDED, AWAY, |O____|",
            "SUBSTITUTION, HOME, |____O|"})
    void anEventNotAboutTheActionDrawsABallLetterWhereTheBallIs(EventType type, MatchSide possession,
            String field) {
        MatchEvent event = eventWithBall(type, possession, possession, ZoneRole.OPPONENT_BOX);

        String line = drawing.draw(event, TEAM_NAMES, GOALLESS);

        assertThat(line).isEqualTo("    Aquile " + field + " Lupi (0 - 0)");
    }

    @Test
    void aKickOffIsDrawnWithTheArrowOfTheSideThatKicksOff() {
        MatchEvent homeKickOff = eventWithBall(EventType.KICK_OFF, MatchSide.HOME, MatchSide.HOME, ZoneRole.MIDFIELD);
        MatchEvent awayKickOff = eventWithBall(EventType.SECOND_HALF_START, MatchSide.AWAY, MatchSide.AWAY,
                ZoneRole.MIDFIELD);

        assertThat(drawing.draw(homeKickOff, TEAM_NAMES, GOALLESS)).isEqualTo("    Aquile |__>__| Lupi (0 - 0)");
        assertThat(drawing.draw(awayKickOff, TEAM_NAMES, GOALLESS)).isEqualTo("    Aquile |__<__| Lupi (0 - 0)");
    }

    @Test
    void aGoalByTheHomeSideHasTheBallInTheAwayBoxAndAGoalByTheAwaySideInTheHomeBox() {
        MatchEvent homeGoal = eventWithBall(EventType.GOAL, MatchSide.HOME, MatchSide.HOME, ZoneRole.OPPONENT_BOX);
        MatchEvent awayGoal = eventWithBall(EventType.GOAL, MatchSide.AWAY, MatchSide.AWAY, ZoneRole.OPPONENT_BOX);

        assertThat(drawing.draw(homeGoal, TEAM_NAMES, GOALLESS)).isEqualTo("    Aquile |____>| Lupi (0 - 0)");
        assertThat(drawing.draw(awayGoal, TEAM_NAMES, GOALLESS)).isEqualTo("    Aquile |<____| Lupi (0 - 0)");
    }

    @Test
    void theFieldIsDrawnWithTheNamesOfTheTeamsOnTheirSides() {
        TeamNames names = new TeamNames("Sporting Collina", "Real Rivoli");
        MatchEvent pass = eventWithBall(EventType.PASS_COMPLETED, MatchSide.HOME, MatchSide.HOME,
                ZoneRole.ATTACKING_THIRD);

        assertThat(drawing.draw(pass, names, GOALLESS)).isEqualTo("    Sporting Collina |___>_| Real Rivoli (0 - 0)");
    }

    @Test
    void theScoreIsDrawnAfterTheAwayTeamNameWithTheHomeGoalsFirst() {
        MatchEvent pass = eventWithBall(EventType.PASS_COMPLETED, MatchSide.HOME, MatchSide.HOME, ZoneRole.MIDFIELD);

        String line = drawing.draw(pass, TEAM_NAMES, new Score(3, 1));

        assertThat(line).isEqualTo("    Aquile |__>__| Lupi (3 - 1)");
    }

    private MatchEvent eventWithBall(EventType type, MatchSide side, MatchSide possession, ZoneRole zoneRole) {
        BallPosition ball = new BallPosition(possession, zoneRole);
        MatchTime time = new MatchTime(MatchPeriod.FIRST_HALF, 600);
        return new MatchEvent(1, time, type, side, zoneRole, ball, PlayPhase.BUILD_UP, Participants.nobody(), 0,
                EventDetail.none());
    }
}
