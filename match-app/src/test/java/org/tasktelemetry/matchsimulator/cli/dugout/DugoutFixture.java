package org.tasktelemetry.matchsimulator.cli.dugout;

import java.util.List;
import java.util.function.Predicate;

import org.tasktelemetry.matchsimulator.coach.MatchMoment.InterventionPreview;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.config.FormationCatalog;
import org.tasktelemetry.matchsimulator.coach.human.AvailableFormations;
import org.tasktelemetry.matchsimulator.coach.human.FormationShiftPlanner;
import org.tasktelemetry.matchsimulator.coach.human.PlannedMoves;
import org.tasktelemetry.matchsimulator.coach.human.TeamSheet;
import org.tasktelemetry.matchsimulator.coach.human.TeamSheetReader;
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
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.event.ShotProfile;
import org.tasktelemetry.matchsimulator.engine.event.ShotQuality;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.PreviewVerdict;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;
import org.tasktelemetry.matchsimulator.live.PreparedMatch;
import org.tasktelemetry.matchsimulator.narration.ReferenceChronicle;

final class DugoutFixture {

    static final String HOME_COACH = "Giorgio Parodi";

    private static final double GOAL_EXPECTED_GOALS = 0.2;
    private static final Deployment ANY_DEPLOYMENT = new Deployment(4, 4, 2);

    private final CoachConfig config = CoachConfig.defaults();
    private final PreparedMatch prepared = ReferenceChronicle.prepare(ReferenceChronicle.homeTeamFile(),
            ReferenceChronicle.awayTeamFile());
    private final TeamSheetReader reader = new TeamSheetReader(config);
    private int nextOrder = 1;

    Team home() {
        return prepared.home();
    }

    TeamSheet sheetAtKickOff() {
        return reader.start(MatchSide.HOME, prepared.homeDecision(), prepared.away());
    }

    TeamSheet sheetAfter(MatchEvent... events) {
        TeamSheet atKickOff = sheetAtKickOff();
        return reader.advance(atKickOff, List.of(events));
    }

    PlannedMoves plannedMoves(TeamSheet sheet, InterventionPreview preview) {
        FormationCatalog catalog = config.formations();
        return plannedMoves(sheet, catalog, preview);
    }

    PlannedMoves plannedMoves(TeamSheet sheet, FormationCatalog catalog, InterventionPreview preview) {
        AvailableFormations formations = new AvailableFormations(catalog);
        FormationShiftPlanner planner = new FormationShiftPlanner();
        return new PlannedMoves(sheet, preview, formations, planner);
    }

    static InterventionPreview acceptEverything() {
        return requests -> requests.stream()
                .map(request -> PreviewVerdict.accepted())
                .toList();
    }

    static InterventionPreview refusing(Predicate<Intervention> rule, RefusalReason reason) {
        return requests -> requests.stream()
                .map(request -> rule.test(request) ? PreviewVerdict.refused(reason) : PreviewVerdict.accepted())
                .toList();
    }

    Player starter(Role role, int index) {
        return home().starters().stream()
                .filter(player -> player.role() == role)
                .skip(index)
                .findFirst()
                .orElseThrow();
    }

    Player benchPlayer(Role role, int index) {
        return home().bench().stream()
                .filter(player -> player.role() == role)
                .skip(index)
                .findFirst()
                .orElseThrow();
    }

    int numberOf(TeamSheet sheet, Player player) {
        return sheet.players().stream()
                .filter(candidate -> candidate.player().equals(player))
                .findFirst()
                .orElseThrow()
                .number();
    }

    MatchEvent kickOff() {
        return plain(EventType.KICK_OFF, MatchSide.HOME, Participants.nobody(), timeAt(0), EventDetail.none());
    }

    MatchEvent tired(Player player, FatigueBand band, int minute) {
        EventDetail detail = EventDetail.fatigue(band);
        return plain(EventType.PLAYER_TIRED, MatchSide.HOME, Participants.of(player, MatchSide.HOME),
                timeAt(minute), detail);
    }

    MatchEvent yellowCard(Player player, int minute) {
        return plain(EventType.YELLOW_CARD, MatchSide.HOME, Participants.of(player, MatchSide.HOME),
                timeAt(minute), EventDetail.none());
    }

    MatchEvent substitution(Player leaving, Player entering, int minute) {
        EventDetail detail = EventDetail.lineupChange(entering.role(), ChangeReason.CHASE_GAME, ANY_DEPLOYMENT,
                ANY_DEPLOYMENT);
        Participants participants = Participants.of(leaving, MatchSide.HOME, entering, MatchSide.HOME);
        return plain(EventType.SUBSTITUTION, MatchSide.HOME, participants, timeAt(minute), detail);
    }

    MatchEvent moved(Player player, Role sector, int minute) {
        EventDetail detail = EventDetail.lineupChange(sector, ChangeReason.CHASE_GAME, ANY_DEPLOYMENT,
                ANY_DEPLOYMENT);
        return plain(EventType.PLAYER_MOVED, MatchSide.HOME, Participants.of(player, MatchSide.HOME),
                timeAt(minute), detail);
    }

    MatchEvent awayGoal(int minute) {
        ShotProfile profile = ShotProfile.ofShot(GOAL_EXPECTED_GOALS, ShotKind.FOOT, ShotQuality.HALF_CHANCE);
        EventDetail detail = EventDetail.goal(profile, GoalContext.TAKES_THE_LEAD);
        return plain(EventType.GOAL, MatchSide.AWAY, Participants.nobody(), timeAt(minute), detail);
    }

    private MatchTime timeAt(int minute) {
        if (minute < MatchPeriod.MINUTES_PER_HALF) {
            return new MatchTime(MatchPeriod.FIRST_HALF, minute * MatchPeriod.SECONDS_PER_MINUTE);
        }
        int minutesIntoSecondHalf = minute - MatchPeriod.MINUTES_PER_HALF;
        return new MatchTime(MatchPeriod.SECOND_HALF, minutesIntoSecondHalf * MatchPeriod.SECONDS_PER_MINUTE);
    }

    private MatchEvent plain(EventType type, MatchSide side, Participants participants, MatchTime time,
            EventDetail detail) {
        BallPosition ball = new BallPosition(side, ZoneRole.MIDFIELD);
        MatchEvent event = new MatchEvent(nextOrder, time, type, side, ZoneRole.MIDFIELD, ball, PlayPhase.BUILD_UP,
                participants, 0, detail);
        nextOrder++;
        return event;
    }
}
