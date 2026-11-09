package org.tasktelemetry.matchsimulator.narration;

import java.util.ArrayList;
import java.util.List;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.GoalContext;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.event.ShotProfile;
import org.tasktelemetry.matchsimulator.engine.event.ShotQuality;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;

final class NarrationEvents {

    static final Player HOME_FORWARD = player("Rossi", Role.FORWARD);
    static final Player HOME_MIDFIELDER = player("Bianchi", Role.MIDFIELDER);
    static final Player HOME_DEFENDER = player("Russo", Role.DEFENDER);
    static final Player HOME_BENCH = player("Esposito", Role.MIDFIELDER);
    static final Player AWAY_FORWARD = player("Verdi", Role.FORWARD);
    static final Player AWAY_DEFENDER = player("Neri", Role.DEFENDER);
    static final Player AWAY_GOALKEEPER = player("Gialli", Role.GOALKEEPER);

    private static final int SECONDS_BETWEEN_EVENTS = 20;

    private final List<MatchEvent> events = new ArrayList<>();

    static Player player(String name, Role role) {
        return new Player(name, role, new PlayerAttributes(50, 50, 50, 50, 50, 50, 50, 50));
    }

    static MatchTime firstHalf(int seconds) {
        return new MatchTime(MatchPeriod.FIRST_HALF, seconds);
    }

    static MatchTime secondHalf(int seconds) {
        return new MatchTime(MatchPeriod.SECOND_HALF, seconds);
    }

    static ShotProfile shotProfile(ShotKind kind, ShotQuality quality) {
        return ShotProfile.ofShot(0.2, kind, quality);
    }

    static EventDetail goalDetail(GoalContext context) {
        ShotProfile profile = shotProfile(ShotKind.FOOT, ShotQuality.HALF_CHANCE);
        return EventDetail.goal(profile, context);
    }

    List<MatchEvent> list() {
        return List.copyOf(events);
    }

    MatchEvent add(EventType type, MatchSide side, Player primary) {
        Participants participants = Participants.of(primary);
        return add(type, side, ZoneRole.MIDFIELD, participants, EventDetail.none());
    }

    MatchEvent add(EventType type, MatchSide side, ZoneRole zoneRole, Participants participants,
            EventDetail detail) {
        MatchTime time = firstHalf(SECONDS_BETWEEN_EVENTS * (events.size() + 1));
        return add(time, type, side, zoneRole, participants, detail);
    }

    MatchEvent add(MatchTime time, EventType type, MatchSide side, ZoneRole zoneRole, Participants participants,
            EventDetail detail) {
        int order = events.size() + 1;
        MatchEvent event = new MatchEvent(order, time, type, side, zoneRole, PlayPhase.BUILD_UP, participants,
                SECONDS_BETWEEN_EVENTS, detail);
        events.add(event);
        return event;
    }

    MatchEvent addGoal(MatchTime time, MatchSide side, Player scorer, GoalContext context) {
        Participants participants = Participants.of(scorer);
        EventDetail detail = goalDetail(context);
        return add(time, EventType.GOAL, side, ZoneRole.OPPONENT_BOX, participants, detail);
    }
}
