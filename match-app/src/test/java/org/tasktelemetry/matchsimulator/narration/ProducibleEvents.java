package org.tasktelemetry.matchsimulator.narration;

import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_DEFENDER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_FORWARD;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_GOALKEEPER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_BENCH;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_FORWARD;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_MIDFIELDER;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.FoulSeverity;
import org.tasktelemetry.matchsimulator.engine.event.GoalContext;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.event.SaveQuality;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.event.ShotProfile;
import org.tasktelemetry.matchsimulator.engine.event.ShotQuality;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.Score;

final class ProducibleEvents {

    private static final Set<EventType> WITHOUT_PLAYERS = EnumSet.of(EventType.KICK_OFF, EventType.SECOND_HALF_START,
            EventType.FIRST_HALF_END, EventType.MATCH_END, EventType.KICK_OFF_AFTER_GOAL);
    private static final Set<EventType> WITH_BOTH_PLAYERS = EnumSet.of(EventType.CLEARANCE, EventType.OFFSIDE,
            EventType.SUBSTITUTION);
    private static final Set<EventType> WITH_SPECIFIC_DETAIL = EnumSet.of(EventType.STOPPAGE_ANNOUNCED,
            EventType.MATCH_SUSPENDED, EventType.FOUL, EventType.GOAL, EventType.SHOT_SAVED,
            EventType.SHOT_OFF_TARGET, EventType.SHOT_BLOCKED);

    private final NarrationEvents fixture = new NarrationEvents();

    static List<MatchEvent> all() {
        return new ProducibleEvents().build();
    }

    private List<MatchEvent> build() {
        Stream<MatchEvent> flow = Stream.concat(withoutPlayers(), withPrimaryOnly());
        Stream<MatchEvent> pairs = Stream.concat(withBothPlayers(), specificDetails());
        return Stream.concat(flow, pairs).toList();
    }

    private Stream<MatchEvent> withoutPlayers() {
        Stream<MatchEvent> flow = WITHOUT_PLAYERS.stream().map(this::eventWithoutPlayers);
        MatchEvent stoppage = eventWithDetail(EventType.STOPPAGE_ANNOUNCED, Participants.nobody(),
                EventDetail.stoppageAnnounced(3));
        Score fieldScore = new Score(1, 4);
        Score officialScore = new Score(1, 4);
        EventDetail suspension = EventDetail.suspension(MatchSide.HOME, fieldScore, officialScore);
        MatchEvent suspended = eventWithDetail(EventType.MATCH_SUSPENDED, Participants.nobody(), suspension);
        return Stream.concat(flow, Stream.of(stoppage, suspended));
    }

    private Stream<MatchEvent> withPrimaryOnly() {
        return Stream.of(EventType.values())
                .filter(type -> !WITHOUT_PLAYERS.contains(type))
                .filter(type -> !WITH_BOTH_PLAYERS.contains(type))
                .filter(type -> !WITH_SPECIFIC_DETAIL.contains(type))
                .map(this::eventWithPrimary);
    }

    private Stream<MatchEvent> withBothPlayers() {
        return WITH_BOTH_PLAYERS.stream().map(this::eventWithBothPlayers);
    }

    private Stream<MatchEvent> specificDetails() {
        Stream<MatchEvent> fouls = fouls();
        Stream<MatchEvent> goals = goals();
        Stream<MatchEvent> saves = saves();
        Stream<MatchEvent> misses = misses(EventType.SHOT_OFF_TARGET);
        Stream<MatchEvent> blocks = misses(EventType.SHOT_BLOCKED);
        Stream<MatchEvent> penaltyMiss = Stream.of(penaltyMiss());
        Stream<MatchEvent> firstGroup = Stream.concat(fouls, goals);
        Stream<MatchEvent> secondGroup = Stream.concat(saves, Stream.concat(misses, blocks));
        return Stream.concat(Stream.concat(firstGroup, secondGroup), penaltyMiss);
    }

    private Stream<MatchEvent> fouls() {
        Participants participants = Participants.of(AWAY_DEFENDER, HOME_MIDFIELDER);
        return Stream.of(FoulSeverity.values())
                .flatMap(severity -> Stream.of(false, true).map(penalty -> {
                    EventDetail detail = EventDetail.foul(severity, penalty);
                    return eventWithDetail(EventType.FOUL, participants, detail);
                }));
    }

    private Stream<MatchEvent> goals() {
        Participants alone = Participants.of(HOME_FORWARD);
        Participants withCrosser = Participants.of(HOME_FORWARD, HOME_MIDFIELDER);
        MatchEvent foot = goalOf(ShotProfile.ofShot(0.2, ShotKind.FOOT, ShotQuality.BIG_CHANCE), alone);
        MatchEvent freeKick = goalOf(ShotProfile.ofShot(0.1, ShotKind.DIRECT_FREE_KICK, ShotQuality.HALF_CHANCE),
                alone);
        MatchEvent header = goalOf(ShotProfile.ofShot(0.3, ShotKind.HEADER, ShotQuality.BIG_CHANCE), withCrosser);
        MatchEvent penalty = goalOf(ShotProfile.ofPenalty(0.79, ShotQuality.BIG_CHANCE), alone);
        return Stream.of(foot, freeKick, header, penalty);
    }

    private MatchEvent goalOf(ShotProfile profile, Participants participants) {
        EventDetail detail = EventDetail.goal(profile, GoalContext.TAKES_THE_LEAD);
        return eventWithDetail(EventType.GOAL, participants, detail);
    }

    private Stream<MatchEvent> saves() {
        Participants participants = Participants.of(HOME_FORWARD, AWAY_GOALKEEPER);
        Stream<MatchEvent> byQuality = Stream.of(SaveQuality.values()).map(quality -> {
            ShotProfile profile = ShotProfile.ofShot(0.2, ShotKind.FOOT, ShotQuality.HALF_CHANCE);
            return eventWithDetail(EventType.SHOT_SAVED, participants, EventDetail.save(profile, quality));
        });
        ShotProfile penaltyProfile = ShotProfile.ofPenalty(0.79, ShotQuality.BIG_CHANCE);
        EventDetail penaltySave = EventDetail.save(penaltyProfile, SaveQuality.ROUTINE);
        MatchEvent penalty = eventWithDetail(EventType.SHOT_SAVED, participants, penaltySave);
        return Stream.concat(byQuality, Stream.of(penalty));
    }

    private Stream<MatchEvent> misses(EventType type) {
        Participants alone = Participants.of(HOME_FORWARD);
        Participants withCrosser = Participants.of(HOME_FORWARD, HOME_MIDFIELDER);
        return Stream.of(ShotQuality.values())
                .flatMap(quality -> Stream.of(alone, withCrosser).map(participants -> {
                    ShotProfile profile = ShotProfile.ofShot(0.1, ShotKind.FOOT, quality);
                    return eventWithDetail(type, participants, EventDetail.shot(profile));
                }));
    }

    private MatchEvent penaltyMiss() {
        ShotProfile profile = ShotProfile.ofPenalty(0.79, ShotQuality.BIG_CHANCE);
        return eventWithDetail(EventType.SHOT_OFF_TARGET, Participants.of(AWAY_FORWARD), EventDetail.shot(profile));
    }

    private MatchEvent eventWithoutPlayers(EventType type) {
        return eventWithDetail(type, Participants.nobody(), EventDetail.none());
    }

    private MatchEvent eventWithPrimary(EventType type) {
        return eventWithDetail(type, Participants.of(HOME_BENCH), EventDetail.none());
    }

    private MatchEvent eventWithBothPlayers(EventType type) {
        return eventWithDetail(type, Participants.of(AWAY_DEFENDER, HOME_FORWARD), EventDetail.none());
    }

    private MatchEvent eventWithDetail(EventType type, Participants participants, EventDetail detail) {
        return fixture.add(type, MatchSide.HOME, ZoneRole.MIDFIELD, participants, detail);
    }
}
