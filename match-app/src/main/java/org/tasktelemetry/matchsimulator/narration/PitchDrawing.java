package org.tasktelemetry.matchsimulator.narration;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.BallPosition;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.message.UserMessages;

public final class PitchDrawing {

    static final String INDENT = "    ";
    private static final String WORD_SEPARATOR = " ";
    private static final String SCORE_FORMAT = " (%d - %d)";
    private static final char FIELD_END = '|';
    private static final char ZONE = '_';
    private static final char BALL_MOVING_RIGHT = '>';
    private static final char BALL_MOVING_LEFT = '<';
    private static final char BALL_STOPPED = 'O';
    private static final int ZONE_COUNT = ZoneRole.values().length;
    private static final int LAST_COLUMN = ZONE_COUNT - 1;

    private final UserMessages messages;

    public PitchDrawing(UserMessages messages) {
        this.messages = messages;
    }

    public String draw(MatchEvent event, TeamNames teamNames, Score score, MatchSetups setups) {
        String field = drawField(event);
        String scoreText = SCORE_FORMAT.formatted(score.home(), score.away());
        String homeSetup = describeSetup(setups.home());
        String awaySetup = describeSetup(setups.away());
        String home = teamNames.home() + WORD_SEPARATOR + homeSetup;
        String away = awaySetup + WORD_SEPARATOR + teamNames.away();
        return INDENT + home + WORD_SEPARATOR + field + WORD_SEPARATOR + away + scoreText;
    }

    private String describeSetup(TeamSetup setup) {
        String formation = setup.deployment().label();
        StyleAbbreviation abbreviation = StyleAbbreviation.of(setup.style());
        String style = messages.text(abbreviation);
        return formation + WORD_SEPARATOR + style;
    }

    private String drawField(MatchEvent event) {
        BallDepiction depiction = BallDepiction.of(event.type());
        int ballColumn = columnOf(event.ball());
        char ballCharacter = ballCharacterOf(event, depiction);
        return renderField(ballColumn, ballCharacter);
    }

    private String renderField(int ballColumn, char ballCharacter) {
        StringBuilder field = new StringBuilder();
        field.append(FIELD_END);
        for (int column = 0; column < ZONE_COUNT; column++) {
            char character = column == ballColumn ? ballCharacter : ZONE;
            field.append(character);
        }
        field.append(FIELD_END);
        return field.toString();
    }

    private char ballCharacterOf(MatchEvent event, BallDepiction depiction) {
        return switch (depiction) {
            case IN_PLAY -> movingBallOf(event.ball().possession());
            case STOPPED_IN_EVENT_ZONE -> BALL_STOPPED;
        };
    }

    private int columnOf(BallPosition ball) {
        int ordinal = ball.zoneRole().ordinal();
        return switch (ball.possession()) {
            case HOME -> ordinal;
            case AWAY -> LAST_COLUMN - ordinal;
        };
    }

    private char movingBallOf(MatchSide possession) {
        return switch (possession) {
            case HOME -> BALL_MOVING_RIGHT;
            case AWAY -> BALL_MOVING_LEFT;
        };
    }
}
