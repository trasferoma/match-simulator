package org.tasktelemetry.matchsimulator.live;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.coach.Coach;
import org.tasktelemetry.matchsimulator.coach.MatchCoaches;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.decision.AutomaticCoach;
import org.tasktelemetry.matchsimulator.coach.lineup.LineupChoice;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.coach.squad.Squad;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.narration.TeamNames;

public final class MatchPreparation {

    private final LineupChoice lineupChoice;
    private final CoachConfig coachConfig;

    public MatchPreparation(LineupChoice lineupChoice, CoachConfig coachConfig) {
        this.lineupChoice = Objects.requireNonNull(lineupChoice, "lineup choice must not be null");
        this.coachConfig = Objects.requireNonNull(coachConfig, "coach configuration must not be null");
    }

    public static MatchPreparation standard() {
        CoachConfig config = CoachConfig.defaults();
        LineupChoice lineupChoice = new LineupChoice(config);
        return new MatchPreparation(lineupChoice, config);
    }

    public PreparedMatch prepare(Squad home, Squad away) {
        Objects.requireNonNull(home, "home squad must not be null");
        Objects.requireNonNull(away, "away squad must not be null");
        PreMatchDecision homeDecision = lineupChoice.decide(home, away, MatchSide.HOME);
        PreMatchDecision awayDecision = lineupChoice.decide(away, home, MatchSide.AWAY);
        MatchCoaches coaches = hireCoaches(home, away);
        TeamNames names = nameParticipants(home, away);
        return new PreparedMatch(homeDecision, awayDecision, names, coaches);
    }

    private MatchCoaches hireCoaches(Squad home, Squad away) {
        Coach homeCoach = new AutomaticCoach(coachConfig, home.coach().attributes());
        Coach awayCoach = new AutomaticCoach(coachConfig, away.coach().attributes());
        return new MatchCoaches(homeCoach, awayCoach);
    }

    private TeamNames nameParticipants(Squad home, Squad away) {
        String homeCoach = home.coach().name();
        String awayCoach = away.coach().name();
        return new TeamNames(home.name(), away.name(), homeCoach, awayCoach);
    }
}
