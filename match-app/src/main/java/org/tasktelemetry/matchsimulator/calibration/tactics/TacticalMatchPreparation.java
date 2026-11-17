package org.tasktelemetry.matchsimulator.calibration.tactics;

import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.coach.Coach;
import org.tasktelemetry.matchsimulator.coach.MatchCoaches;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.config.Formation;
import org.tasktelemetry.matchsimulator.coach.config.FormationCatalog;
import org.tasktelemetry.matchsimulator.coach.lineup.LineupChoice;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.coach.squad.Squad;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.team.Team;
import org.tasktelemetry.matchsimulator.live.PreparedMatch;
import org.tasktelemetry.matchsimulator.narration.TeamNames;

final class TacticalMatchPreparation {

    private final CoachConfig coachConfig;

    TacticalMatchPreparation(CoachConfig coachConfig) {
        this.coachConfig = Objects.requireNonNull(coachConfig, "coach configuration must not be null");
    }

    PreparedMatch prepare(Squad home, TacticVariant homeTactic, Squad away, TacticVariant awayTactic) {
        PreMatchDecision homeDecision = imposeTactic(home, away, MatchSide.HOME, homeTactic);
        PreMatchDecision awayDecision = imposeTactic(away, home, MatchSide.AWAY, awayTactic);
        MatchCoaches coaches = hireIdleCoaches();
        TeamNames names = nameParticipants(home, away);
        return new PreparedMatch(homeDecision, awayDecision, names, coaches);
    }

    private PreMatchDecision imposeTactic(Squad own, Squad opponent, MatchSide side, TacticVariant tactic) {
        LineupChoice lineupChoice = chooseLineupFixedTo(tactic.formation());
        PreMatchDecision automatic = lineupChoice.decide(own, opponent, side);
        Team team = restyle(automatic.team(), tactic);
        return new PreMatchDecision(team, automatic.evaluation(), automatic.band(), automatic.formation(),
                automatic.rosterOrder());
    }

    private LineupChoice chooseLineupFixedTo(Formation formation) {
        List<Formation> formations = List.of(formation);
        FormationCatalog onlyThisFormation = new FormationCatalog(formations);
        CoachConfig fixedConfig = new CoachConfig(coachConfig.assessment(), coachConfig.bandResponses(),
                onlyThisFormation, coachConfig.styleScale(), coachConfig.sectorLimits(),
                coachConfig.maximumBenchSize(), coachConfig.timing(), coachConfig.resultTiming(),
                coachConfig.flow(), coachConfig.sectorStrain(), coachConfig.tacticalReading(),
                coachConfig.substitutions(), coachConfig.fatigueResponse());
        return new LineupChoice(fixedConfig);
    }

    private Team restyle(Team team, TacticVariant tactic) {
        return new Team(team.name(), tactic.style(), team.starters(), team.bench());
    }

    private MatchCoaches hireIdleCoaches() {
        Coach homeCoach = new NoInterventionCoach();
        Coach awayCoach = new NoInterventionCoach();
        return new MatchCoaches(homeCoach, awayCoach);
    }

    private TeamNames nameParticipants(Squad home, Squad away) {
        String homeCoach = home.coach().name();
        String awayCoach = away.coach().name();
        return new TeamNames(home.name(), away.name(), homeCoach, awayCoach);
    }
}
