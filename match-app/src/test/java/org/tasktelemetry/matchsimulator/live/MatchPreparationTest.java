package org.tasktelemetry.matchsimulator.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.decision.AutomaticCoach;
import org.tasktelemetry.matchsimulator.coach.human.DugoutSession;
import org.tasktelemetry.matchsimulator.coach.human.HumanCoach;
import org.tasktelemetry.matchsimulator.coach.human.PlannedMoves;
import org.tasktelemetry.matchsimulator.coach.human.TeamSheet;
import org.tasktelemetry.matchsimulator.coach.lineup.LineupChoice;
import org.tasktelemetry.matchsimulator.coach.squad.CoachAttributes;
import org.tasktelemetry.matchsimulator.coach.squad.CoachProfile;
import org.tasktelemetry.matchsimulator.coach.squad.Squad;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;
import org.tasktelemetry.matchsimulator.narration.MatchSetups;

class MatchPreparationTest {

    private static final int RATING = 60;

    private final CoachConfig coachConfig = CoachConfig.defaults();
    private final LineupChoice lineupChoice = new LineupChoice(coachConfig);
    private final MatchPreparation preparation = new MatchPreparation(lineupChoice, coachConfig);

    @Test
    void theHomeSquadIsChosenAsTheHomeSideAndTheAwaySquadAsTheAwaySide() {
        Squad home = squad("Casa", "Coach Casa", 2, 6, 6, 4);
        Squad away = squad("Ospite", "Coach Ospite", 2, 6, 6, 4);

        PreparedMatch prepared = preparation.prepare(home, away);

        Team expectedHome = lineupChoice.decide(home, away, MatchSide.HOME).team();
        Team expectedAway = lineupChoice.decide(away, home, MatchSide.AWAY).team();
        assertThat(prepared.home()).isEqualTo(expectedHome);
        assertThat(prepared.away()).isEqualTo(expectedAway);
        assertThat(prepared.home().name()).isEqualTo("Casa");
        assertThat(prepared.away().name()).isEqualTo("Ospite");
    }

    @Test
    void theSideChangesTheChoiceOfTwoEquallyStrongSquads() {
        Squad home = squad("Casa", "Coach Casa", 2, 6, 6, 4);
        Squad away = squad("Ospite", "Coach Ospite", 2, 6, 6, 4);

        PreparedMatch prepared = preparation.prepare(home, away);

        assertThat(prepared.home().style()).isNotEqualTo(prepared.away().style());
    }

    @Test
    void theOpeningSetupsAreTheStartingDeploymentAndStyleOfEachTeam() {
        Squad home = squad("Casa", "Coach Casa", 2, 6, 6, 4);
        Squad away = squad("Ospite", "Coach Ospite", 2, 6, 6, 4);

        PreparedMatch prepared = preparation.prepare(home, away);

        MatchSetups setups = prepared.openingSetups();
        assertThat(setups.home().deployment()).isEqualTo(Deployment.of(TeamOnPitch.startingWith(prepared.home())));
        assertThat(setups.away().deployment()).isEqualTo(Deployment.of(TeamOnPitch.startingWith(prepared.away())));
        assertThat(setups.home().style()).isEqualTo(prepared.home().style());
        assertThat(setups.away().style()).isEqualTo(prepared.away().style());
    }

    @Test
    void theNamesOfTheCoachesComeFromTheSquads() {
        Squad home = squad("Casa", "Giorgio Parodi", 2, 6, 6, 4);
        Squad away = squad("Ospite", "Roberto Basile", 2, 6, 6, 4);

        PreparedMatch prepared = preparation.prepare(home, away);

        assertThat(prepared.names().home()).isEqualTo("Casa");
        assertThat(prepared.names().away()).isEqualTo("Ospite");
        assertThat(prepared.names().coachOf(MatchSide.HOME)).isEqualTo("Giorgio Parodi");
        assertThat(prepared.names().coachOf(MatchSide.AWAY)).isEqualTo("Roberto Basile");
    }

    @Test
    void theTwoSquadsChooseTheirFormationIndependently() {
        Squad home = squad("Casa", "Coach Casa", 2, 6, 6, 4);
        Squad onlyFourFourTwo = squad("Ospite", "Coach Ospite", 1, 4, 4, 2);

        PreparedMatch prepared = preparation.prepare(home, onlyFourFourTwo);

        assertThat(countOf(prepared.away(), Role.DEFENDER)).isEqualTo(4);
        assertThat(countOf(prepared.away(), Role.MIDFIELDER)).isEqualTo(4);
        assertThat(countOf(prepared.away(), Role.FORWARD)).isEqualTo(2);
        Team expectedHome = lineupChoice.decide(home, onlyFourFourTwo, MatchSide.HOME).team();
        assertThat(prepared.home()).isEqualTo(expectedHome);
    }

    @Test
    void thePreparedMatchCarriesTheDecisionsOfBothSides() {
        Squad home = squad("Casa", "Coach Casa", 2, 6, 6, 4);
        Squad away = squad("Ospite", "Coach Ospite", 2, 6, 6, 4);

        PreparedMatch prepared = preparation.prepare(home, away);

        assertThat(prepared.homeDecision()).isEqualTo(lineupChoice.decide(home, away, MatchSide.HOME));
        assertThat(prepared.awayDecision()).isEqualTo(lineupChoice.decide(away, home, MatchSide.AWAY));
    }

    @Test
    void handingControlToASessionReplacesTheAutomaticCoachOfThatSideOnly() {
        Squad home = squad("Casa", "Coach Casa", 2, 6, 6, 4);
        Squad away = squad("Ospite", "Coach Ospite", 2, 6, 6, 4);
        PreparedMatch prepared = preparation.prepare(home, away);

        PreparedMatch awayControlled = preparation.handControlTo(prepared, MatchSide.AWAY, new IdleSession());
        PreparedMatch homeControlled = preparation.handControlTo(prepared, MatchSide.HOME, new IdleSession());

        assertThat(awayControlled.coaches().home()).isSameAs(prepared.coaches().home())
                .isInstanceOf(AutomaticCoach.class);
        assertThat(awayControlled.coaches().away()).isInstanceOf(HumanCoach.class);
        assertThat(homeControlled.coaches().home()).isInstanceOf(HumanCoach.class);
        assertThat(homeControlled.coaches().away()).isSameAs(prepared.coaches().away());
        assertThat(awayControlled.homeDecision()).isEqualTo(prepared.homeDecision());
        assertThat(awayControlled.awayDecision()).isEqualTo(prepared.awayDecision());
        assertThat(awayControlled.names()).isEqualTo(prepared.names());
    }

    @Test
    void handingControlNeedsTheMatchTheSideAndTheSession() {
        Squad home = squad("Casa", "Coach Casa", 2, 6, 6, 4);
        Squad away = squad("Ospite", "Coach Ospite", 2, 6, 6, 4);
        PreparedMatch prepared = preparation.prepare(home, away);
        DugoutSession session = new IdleSession();

        assertThatThrownBy(() -> preparation.handControlTo(null, MatchSide.HOME, session))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> preparation.handControlTo(prepared, null, session))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> preparation.handControlTo(prepared, MatchSide.HOME, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aMissingSquadIsRefused() {
        Squad home = squad("Casa", "Coach Casa", 2, 6, 6, 4);

        assertThatThrownBy(() -> preparation.prepare(null, home)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> preparation.prepare(home, null)).isInstanceOf(NullPointerException.class);
    }

    private static final class IdleSession implements DugoutSession {

        @Override
        public boolean isPauseRequested() {
            return false;
        }

        @Override
        public void prepareForPause() {
        }

        @Override
        public List<Intervention> open(TeamSheet sheet, PlannedMoves plannedMoves) {
            return List.of();
        }

        @Override
        public void showOutcomes(List<Intervention> moves, List<InterventionResult> results) {
        }
    }

    private long countOf(Team team, Role role) {
        return team.starters().stream().filter(player -> player.role() == role).count();
    }

    private Squad squad(String name, String coachName, int goalkeepers, int defenders, int midfielders,
            int forwards) {
        List<Player> players = new ArrayList<>();
        players.addAll(group(name, Role.GOALKEEPER, goalkeepers));
        players.addAll(group(name, Role.DEFENDER, defenders));
        players.addAll(group(name, Role.MIDFIELDER, midfielders));
        players.addAll(group(name, Role.FORWARD, forwards));
        CoachProfile coach = new CoachProfile(coachName, new CoachAttributes(50));
        return new Squad(name, coach, players);
    }

    private List<Player> group(String squadName, Role role, int count) {
        List<Player> players = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            PlayerAttributes attributes = new PlayerAttributes(RATING, RATING, RATING, RATING, RATING, RATING,
                    RATING, RATING, 50);
            players.add(new Player(squadName + " " + role + " " + index, role, attributes));
        }
        return players;
    }
}
