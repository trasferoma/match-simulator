package org.tasktelemetry.matchsimulator.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.ScenarioEvents;
import org.tasktelemetry.matchsimulator.coach.config.SectorStrainConfig;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class SectorDuelReadingTest {

    private static final double TOLERANCE = 1e-9;
    private static final MatchSide HOME = MatchSide.HOME;
    private static final MatchSide AWAY = MatchSide.AWAY;

    private final DuelReader duelReader = new DuelReader();
    private final SectorStrainReader strainReader = new SectorStrainReader(SectorStrainConfig.defaults());
    private final ScenarioEvents events = new ScenarioEvents();

    @Test
    void theDefenceLosesWhenTheOpponentCompletesInItsAttackingAreaAndWinsWhenTheOpponentLosesTheBall() {
        List<MatchEvent> window = List.of(
                events.duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 0),
                events.duel(EventType.DRIBBLE_COMPLETED, AWAY, ZoneRole.OPPONENT_BOX, 50, 10),
                events.duel(EventType.INTERCEPTION, AWAY, ZoneRole.ATTACKING_THIRD, 50, 20),
                events.duel(EventType.TACKLE, AWAY, ZoneRole.OPPONENT_BOX, 50, 30));

        SectorDuels ofHome = duelReader.read(HOME, window);
        SectorDuels ofAway = duelReader.read(AWAY, window);

        assertThat(ofHome.defence()).isEqualTo(new DuelCount(2, 4));
        assertThat(ofHome.attack()).isEqualTo(new DuelCount(0, 0));
        assertThat(ofAway.attack()).isEqualTo(new DuelCount(2, 4));
        assertThat(ofAway.defence()).isEqualTo(new DuelCount(0, 0));
    }

    @Test
    void theMidfieldWinsWhenItHasTheBallAndCompletesOrDefendsAndRecoversWhateverTheSideOfTheEvent() {
        List<MatchEvent> window = List.of(
                events.duel(EventType.PASS_COMPLETED, HOME, ZoneRole.MIDFIELD, 50, 0),
                events.duel(EventType.DRIBBLE_COMPLETED, HOME, ZoneRole.MIDFIELD, 50, 5),
                events.duel(EventType.INTERCEPTION, AWAY, ZoneRole.MIDFIELD, 50, 10),
                events.duel(EventType.TACKLE, HOME, ZoneRole.MIDFIELD, 50, 15),
                events.duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.MIDFIELD, 50, 20));

        SectorDuels ofHome = duelReader.read(HOME, window);
        SectorDuels ofAway = duelReader.read(AWAY, window);

        assertThat(ofHome.midfield()).isEqualTo(new DuelCount(2, 5));
        assertThat(ofAway.midfield()).isEqualTo(new DuelCount(3, 5));
    }

    @Test
    void theAttackLosesWhenItsBallIsInterceptedOrTackledInTheAttackingAreaAndWinsWhenItCompletes() {
        List<MatchEvent> window = List.of(
                events.duel(EventType.TACKLE, HOME, ZoneRole.ATTACKING_THIRD, 50, 0),
                events.duel(EventType.DRIBBLE_COMPLETED, HOME, ZoneRole.ATTACKING_THIRD, 50, 10),
                events.duel(EventType.INTERCEPTION, HOME, ZoneRole.OPPONENT_BOX, 50, 20),
                events.duel(EventType.PASS_COMPLETED, HOME, ZoneRole.OPPONENT_BOX, 50, 30),
                events.duel(EventType.PASS_COMPLETED, HOME, ZoneRole.OPPONENT_BOX, 50, 40));

        SectorDuels ofHome = duelReader.read(HOME, window);
        SectorDuels ofAway = duelReader.read(AWAY, window);

        assertThat(ofHome.attack()).isEqualTo(new DuelCount(2, 5));
        assertThat(ofHome.attack().lostShare()).isCloseTo(0.4, within(TOLERANCE));
        assertThat(ofAway.defence()).isEqualTo(new DuelCount(3, 5));
        assertThat(ofHome.defence()).isEqualTo(new DuelCount(0, 0));
    }

    @Test
    void duelsInTheOwnHalfAndEventsThatAreNotDuelsAreNotCounted() {
        List<MatchEvent> window = List.of(
                events.duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.DEFENCE, 50, 0),
                events.duel(EventType.INTERCEPTION, HOME, ZoneRole.OWN_BOX, 50, 10),
                events.duel(EventType.CLEARANCE, AWAY, ZoneRole.MIDFIELD, 50, 20),
                events.duel(EventType.BALL_CIRCULATED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 30),
                events.duel(EventType.FOUL, HOME, ZoneRole.MIDFIELD, 50, 40));

        SectorDuels ofHome = duelReader.read(HOME, window);

        assertThat(ofHome).isEqualTo(new SectorDuels(new DuelCount(0, 0), new DuelCount(0, 0),
                new DuelCount(0, 0)));
    }

    @Test
    void aSectorIsStrainedFromItsThresholdAndOnlyWithTheMinimumNumberOfDuels() {
        assertThat(strainReader.strainOf(Role.DEFENDER, new DuelCount(7, 7))).isEqualTo(SectorStrain.NONE);
        assertThat(strainReader.strainOf(Role.DEFENDER, new DuelCount(4, 8))).isEqualTo(SectorStrain.NONE);
        assertThat(strainReader.strainOf(Role.DEFENDER, new DuelCount(5, 8))).isEqualTo(SectorStrain.DIFFICULTY);
        assertThat(strainReader.strainOf(Role.DEFENDER, new DuelCount(6, 8)))
                .isEqualTo(SectorStrain.SEVERE_CRISIS);
    }

    @Test
    void everySectorHasItsOwnPairOfThresholds() {
        DuelCount sixOfEight = new DuelCount(6, 8);
        DuelCount sevenOfEight = new DuelCount(7, 8);
        DuelCount nineOfTen = new DuelCount(9, 10);

        assertThat(strainReader.strainOf(Role.DEFENDER, sixOfEight)).isEqualTo(SectorStrain.SEVERE_CRISIS);
        assertThat(strainReader.strainOf(Role.MIDFIELDER, sixOfEight)).isEqualTo(SectorStrain.DIFFICULTY);
        assertThat(strainReader.strainOf(Role.FORWARD, sixOfEight)).isEqualTo(SectorStrain.NONE);
        assertThat(strainReader.strainOf(Role.MIDFIELDER, sevenOfEight)).isEqualTo(SectorStrain.SEVERE_CRISIS);
        assertThat(strainReader.strainOf(Role.FORWARD, sevenOfEight)).isEqualTo(SectorStrain.DIFFICULTY);
        assertThat(strainReader.strainOf(Role.FORWARD, nineOfTen)).isEqualTo(SectorStrain.SEVERE_CRISIS);
    }

    @Test
    void theStrainOfTheThreeSectorsIsReadFromTheEventsOfTheWindow() {
        EventWindow window = EventWindow.empty().after(eightDuelsLostByTheHomeDefence(), 90);

        Map<Role, SectorStrain> strains = strainReader.strainsOf(HOME, window);

        assertThat(strains).containsEntry(Role.DEFENDER, SectorStrain.SEVERE_CRISIS)
                .containsEntry(Role.MIDFIELDER, SectorStrain.NONE)
                .containsEntry(Role.FORWARD, SectorStrain.NONE);
    }

    private List<MatchEvent> eightDuelsLostByTheHomeDefence() {
        return List.of(
                events.duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 0),
                events.duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 1),
                events.duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 2),
                events.duel(EventType.DRIBBLE_COMPLETED, AWAY, ZoneRole.OPPONENT_BOX, 50, 3),
                events.duel(EventType.DRIBBLE_COMPLETED, AWAY, ZoneRole.OPPONENT_BOX, 50, 4),
                events.duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.OPPONENT_BOX, 50, 5),
                events.duel(EventType.INTERCEPTION, AWAY, ZoneRole.OPPONENT_BOX, 50, 6),
                events.duel(EventType.TACKLE, AWAY, ZoneRole.ATTACKING_THIRD, 50, 7));
    }
}
