package org.tasktelemetry.matchsimulator.coach.human;

import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.coach.Coach;
import org.tasktelemetry.matchsimulator.coach.MatchMoment;
import org.tasktelemetry.matchsimulator.coach.TieBreakDraw;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public final class HumanCoach implements Coach {

    private final DugoutSession session;
    private final TeamSheetReader sheetReader;
    private final AvailableFormations formations;
    private final FormationShiftPlanner planner;

    private TeamSheet sheet;

    public HumanCoach(CoachConfig config, DugoutSession session) {
        Objects.requireNonNull(config, "coach configuration must not be null");
        this.session = Objects.requireNonNull(session, "dugout session must not be null");
        this.sheetReader = new TeamSheetReader(config);
        this.formations = new AvailableFormations(config.formations());
        this.planner = new FormationShiftPlanner();
    }

    @Override
    public void open(MatchSide side, PreMatchDecision own, Team opponent, TieBreakDraw draw) {
        Objects.requireNonNull(side, "match side must not be null");
        Objects.requireNonNull(own, "pre-match decision must not be null");
        Objects.requireNonNull(opponent, "opponent team must not be null");
        Objects.requireNonNull(draw, "tie-break draw must not be null");
        if (sheet != null) {
            throw new IllegalStateException("the coach already has a match open");
        }
        sheet = sheetReader.start(side, own, opponent);
    }

    @Override
    public List<Intervention> decide(List<MatchEvent> newEvents, MatchMoment moment) {
        Objects.requireNonNull(newEvents, "new events must not be null");
        Objects.requireNonNull(moment, "match moment must not be null");
        TeamSheet before = requireOpenMatch();
        sheet = sheetReader.advance(before, newEvents);
        if (!isTimeForThePause(moment)) {
            return List.of();
        }
        return holdThePause(moment);
    }

    @Override
    public void interventionsSettled(List<Intervention> requests, List<InterventionResult> results) {
        session.showOutcomes(requests, results);
    }

    @Override
    public void close() {
        sheet = null;
    }

    private TeamSheet requireOpenMatch() {
        if (sheet == null) {
            throw new IllegalStateException("the coach has no match open");
        }
        return sheet;
    }

    private boolean isTimeForThePause(MatchMoment moment) {
        return session.isPauseRequested() && !moment.ballLive();
    }

    private List<Intervention> holdThePause(MatchMoment moment) {
        session.prepareForPause();
        PlannedMoves plannedMoves = new PlannedMoves(sheet, moment.preview(), formations, planner);
        List<Intervention> acceptedMoves = session.open(sheet, plannedMoves);
        return List.copyOf(acceptedMoves);
    }
}
