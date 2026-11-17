package org.tasktelemetry.matchsimulator.coach.lineup;

import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.coach.config.AssessmentConfig;
import org.tasktelemetry.matchsimulator.coach.config.BandResponse;
import org.tasktelemetry.matchsimulator.coach.config.BandResponses;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.config.Formation;
import org.tasktelemetry.matchsimulator.coach.squad.Squad;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public final class LineupChoice {

    private final AssessmentConfig assessment;
    private final BandResponses bandResponses;
    private final FeasibleFormations feasibleFormations;
    private final TeamStrength teamStrength;
    private final FormationPicker formationPicker;
    private final StarterPicker starterPicker;
    private final BenchPicker benchPicker;

    public LineupChoice(CoachConfig config) {
        Objects.requireNonNull(config, "coach configuration must not be null");
        RosterRanking ranking = new RosterRanking();
        this.assessment = config.assessment();
        this.bandResponses = config.bandResponses();
        this.feasibleFormations = new FeasibleFormations(config.formations());
        this.teamStrength = new TeamStrength(ranking);
        this.formationPicker = new FormationPicker();
        this.starterPicker = new StarterPicker(ranking);
        this.benchPicker = new BenchPicker(config.maximumBenchSize(), ranking);
    }

    public PreMatchDecision decide(Squad own, Squad opponent, MatchSide side) {
        Objects.requireNonNull(own, "own squad must not be null");
        Objects.requireNonNull(opponent, "opponent squad must not be null");
        Objects.requireNonNull(side, "match side must not be null");
        List<Formation> feasible = listFeasibleFormations(own);
        double evaluation = evaluate(own, opponent, side);
        AssessmentBand band = classify(evaluation);
        BandResponse response = respondTo(band);
        Formation formation = pickFormation(feasible, response);
        PlayStyle style = chooseStyle(response, formation);
        Team team = buildTeam(own, formation, style);
        return new PreMatchDecision(team, evaluation, band, formation, own.players());
    }

    private List<Formation> listFeasibleFormations(Squad own) {
        return feasibleFormations.requireAtLeastOne(own.players());
    }

    private double evaluate(Squad own, Squad opponent, MatchSide side) {
        double ownStrength = teamStrength.of(own);
        double opponentStrength = teamStrength.of(opponent);
        double fieldBonus = fieldBonusOf(side);
        return ownStrength - opponentStrength + fieldBonus;
    }

    private double fieldBonusOf(MatchSide side) {
        double weight = assessment.homeFieldWeight();
        return switch (side) {
            case HOME -> weight;
            case AWAY -> -weight;
        };
    }

    private AssessmentBand classify(double evaluation) {
        return assessment.bandOf(evaluation);
    }

    private BandResponse respondTo(AssessmentBand band) {
        return bandResponses.responseOf(band);
    }

    private Formation pickFormation(List<Formation> feasible, BandResponse response) {
        return formationPicker.pickClosestToGrade(feasible, response.formationGrade());
    }

    private PlayStyle chooseStyle(BandResponse response, Formation formation) {
        boolean isMoreOffensiveThanBand = formation.grade() > response.formationGrade();
        return isMoreOffensiveThanBand ? PlayStyle.DEFENSIVE : response.style();
    }

    private Team buildTeam(Squad own, Formation formation, PlayStyle style) {
        List<Player> starters = starterPicker.pick(own.players(), formation);
        List<Player> bench = benchPicker.pick(own.players(), starters);
        return new Team(own.name(), style, starters, bench);
    }
}
