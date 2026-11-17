package org.tasktelemetry.matchsimulator.coach;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.BallPosition;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.event.GoalContext;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.event.SaveQuality;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.event.ShotProfile;
import org.tasktelemetry.matchsimulator.engine.event.ShotQuality;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class ScenarioEvents {

    private static final Deployment DEPLOYMENT = new Deployment(4, 4, 2);

    private static final int FIRST_HALF_MINUTES = MatchPeriod.MINUTES_PER_HALF;
    private static final double GOAL_EXPECTED_GOALS = 0.2;

    private int nextOrder = 1;

    public static MatchTime timeAt(int minute, int second) {
        int secondsPerMinute = MatchPeriod.SECONDS_PER_MINUTE;
        if (minute < FIRST_HALF_MINUTES) {
            return new MatchTime(MatchPeriod.FIRST_HALF, minute * secondsPerMinute + second);
        }
        int minutesIntoSecondHalf = minute - FIRST_HALF_MINUTES;
        return new MatchTime(MatchPeriod.SECOND_HALF, minutesIntoSecondHalf * secondsPerMinute + second);
    }

    public MatchEvent kickOff() {
        return plain(EventType.KICK_OFF, MatchSide.HOME, new MatchTime(MatchPeriod.FIRST_HALF, 0));
    }

    public MatchEvent pass(MatchSide side, int minute, int second) {
        return plain(EventType.PASS_COMPLETED, side, timeAt(minute, second));
    }

    public MatchEvent circulation(MatchSide side, int minute, int second) {
        return plain(EventType.BALL_CIRCULATED, side, timeAt(minute, second));
    }

    public MatchEvent goal(MatchSide side, int minute, int second) {
        return shot(side, EventType.GOAL, minute, second, GOAL_EXPECTED_GOALS);
    }

    public MatchEvent shot(MatchSide side, EventType type, int minute, int second, double expectedGoals) {
        ShotProfile profile = ShotProfile.ofShot(expectedGoals, ShotKind.FOOT, ShotQuality.HALF_CHANCE);
        EventDetail detail = shotDetail(type, profile);
        return custom(type, side, ZoneRole.OPPONENT_BOX, timeAt(minute, second), 0, detail);
    }

    public MatchEvent duel(EventType type, MatchSide side, ZoneRole zoneRole, int minute, int second) {
        return custom(type, side, zoneRole, timeAt(minute, second), 0, EventDetail.none());
    }

    public MatchEvent timed(EventType type, MatchSide side, ZoneRole zoneRole, int minute, int second,
            int durationSeconds) {
        return custom(type, side, zoneRole, timeAt(minute, second), durationSeconds, EventDetail.none());
    }

    public MatchEvent firstHalfEnd(int stoppageSeconds) {
        int seconds = MatchPeriod.REGULATION_SECONDS + stoppageSeconds;
        return plain(EventType.FIRST_HALF_END, MatchSide.HOME, new MatchTime(MatchPeriod.FIRST_HALF, seconds));
    }

    public MatchEvent secondHalfStart() {
        return plain(EventType.SECOND_HALF_START, MatchSide.AWAY, new MatchTime(MatchPeriod.SECOND_HALF, 0));
    }

    public MatchEvent matchEnd(int stoppageSeconds) {
        int seconds = MatchPeriod.REGULATION_SECONDS + stoppageSeconds;
        return plain(EventType.MATCH_END, MatchSide.HOME, new MatchTime(MatchPeriod.SECOND_HALF, seconds));
    }

    public MatchEvent redCard(MatchSide side, Player player, int minute, int second) {
        return withPlayer(EventType.RED_CARD, side, player, timeAt(minute, second), EventDetail.none());
    }

    public MatchEvent yellowCard(MatchSide side, Player player, int minute, int second) {
        return withPlayer(EventType.YELLOW_CARD, side, player, timeAt(minute, second), EventDetail.none());
    }

    public MatchEvent injury(MatchSide side, Player player, int minute, int second) {
        return withPlayer(EventType.INJURY, side, player, timeAt(minute, second), EventDetail.none());
    }

    public MatchEvent styleChange(MatchSide side, PlayStyle from, PlayStyle to, ChangeReason reason, int minute,
            int second) {
        EventDetail detail = EventDetail.styleChange(from, to, reason);
        return build(EventType.STYLE_CHANGED, side, Participants.nobody(), timeAt(minute, second), detail);
    }

    public MatchEvent substitution(MatchSide side, Player leaving, Player entering, Role sector,
            ChangeReason reason, int minute, int second) {
        EventDetail detail = EventDetail.lineupChange(sector, reason, DEPLOYMENT, DEPLOYMENT);
        Participants participants = Participants.of(leaving, side, entering, side);
        return build(EventType.SUBSTITUTION, side, participants, timeAt(minute, second), detail);
    }

    public MatchEvent tired(MatchSide side, Player player, FatigueBand band, int minute, int second) {
        EventDetail detail = EventDetail.fatigue(band);
        return withPlayer(EventType.PLAYER_TIRED, side, player, timeAt(minute, second), detail);
    }

    public MatchEvent recovered(MatchSide side, Player player, FatigueBand band, int minute, int second) {
        EventDetail detail = EventDetail.fatigue(band);
        return withPlayer(EventType.PLAYER_RECOVERED, side, player, timeAt(minute, second), detail);
    }

    public MatchEvent move(MatchSide side, Player player, Role sector, ChangeReason reason, int minute,
            int second) {
        EventDetail detail = EventDetail.lineupChange(sector, reason, DEPLOYMENT, DEPLOYMENT);
        return withPlayer(EventType.PLAYER_MOVED, side, player, timeAt(minute, second), detail);
    }

    private EventDetail shotDetail(EventType type, ShotProfile profile) {
        return switch (type) {
            case GOAL -> EventDetail.goal(profile, GoalContext.TAKES_THE_LEAD);
            case SHOT_SAVED -> EventDetail.save(profile, SaveQuality.ROUTINE);
            default -> EventDetail.shot(profile);
        };
    }

    private MatchEvent custom(EventType type, MatchSide side, ZoneRole zoneRole, MatchTime time,
            int durationSeconds, EventDetail detail) {
        BallPosition ball = new BallPosition(side, zoneRole);
        MatchEvent event = new MatchEvent(nextOrder, time, type, side, zoneRole, ball, PlayPhase.BUILD_UP,
                Participants.nobody(), durationSeconds, detail);
        nextOrder++;
        return event;
    }

    private MatchEvent plain(EventType type, MatchSide side, MatchTime time) {
        return build(type, side, Participants.nobody(), time, EventDetail.none());
    }

    private MatchEvent withPlayer(EventType type, MatchSide side, Player player, MatchTime time,
            EventDetail detail) {
        Participants participants = Participants.of(player, side);
        return build(type, side, participants, time, detail);
    }

    private MatchEvent build(EventType type, MatchSide side, Participants participants, MatchTime time,
            EventDetail detail) {
        BallPosition ball = new BallPosition(side, ZoneRole.MIDFIELD);
        MatchEvent event = new MatchEvent(nextOrder, time, type, side, ZoneRole.MIDFIELD, ball, PlayPhase.BUILD_UP,
                participants, 0, detail);
        nextOrder++;
        return event;
    }
}
