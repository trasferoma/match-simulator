package org.tasktelemetry.matchsimulator.coach.lineup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.coach.config.BandResponse;
import org.tasktelemetry.matchsimulator.coach.config.BandResponses;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.config.Formation;
import org.tasktelemetry.matchsimulator.coach.config.FormationCatalog;
import org.tasktelemetry.matchsimulator.coach.squad.Squad;
import org.tasktelemetry.matchsimulator.coach.squad.SquadRule;
import org.tasktelemetry.matchsimulator.coach.squad.SquadRuleViolation;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class LineupChoiceTest {

    private final CoachConfig defaults = CoachConfig.defaults();
    private final LineupChoice choice = new LineupChoice(defaults);

    private static CoachConfig configWith(BandResponses bandResponses, FormationCatalog formations,
            int maximumBenchSize) {
        CoachConfig base = CoachConfig.defaults();
        return new CoachConfig(base.assessment(), bandResponses, formations, base.styleScale(), base.sectorLimits(),
                maximumBenchSize, base.timing(), base.resultTiming(), base.flow(), base.sectorStrain(),
                base.tacticalReading(), base.substitutions());
    }

    private static LineupChoice choiceAlwaysAimingAtTheFourFourTwo(int maximumBenchSize) {
        Map<AssessmentBand, BandResponse> responses = new EnumMap<>(AssessmentBand.class);
        BandResponse neutral = new BandResponse(0, PlayStyle.BALANCED);
        for (AssessmentBand band : AssessmentBand.values()) {
            responses.put(band, neutral);
        }
        BandResponses bandResponses = new BandResponses(responses);
        CoachConfig config = configWith(bandResponses, FormationCatalog.defaults(), maximumBenchSize);
        return new LineupChoice(config);
    }

    private static List<String> namesOf(List<Player> players) {
        return players.stream()
                .map(Player::name)
                .toList();
    }

    private int overallGradeOf(PreMatchDecision decision) {
        int styleGrade = defaults.styleScale().prudenceByStyle().get(decision.team().style());
        return decision.formation().grade() + styleGrade;
    }

    @Test
    void theEvaluationAtHomeAddsTheFieldWeight() {
        Squad own = TestSquads.fullSquad("A", 60);
        Squad opponent = TestSquads.fullSquad("B", 55);

        PreMatchDecision decision = choice.decide(own, opponent, MatchSide.HOME);

        assertThat(decision.evaluation()).isEqualTo(7.0);
    }

    @Test
    void theEvaluationAwaySubtractsTheFieldWeight() {
        Squad own = TestSquads.fullSquad("A", 60);
        Squad opponent = TestSquads.fullSquad("B", 55);

        PreMatchDecision decision = choice.decide(own, opponent, MatchSide.AWAY);

        assertThat(decision.evaluation()).isEqualTo(3.0);
    }

    @ParameterizedTest
    @CsvSource({
            "HOME, 50, 60, -8.0, MUCH_WEAKER, 5-3-2, DEFENSIVE",
            "HOME, 50, 58, -6.0, MUCH_WEAKER, 5-3-2, DEFENSIVE",
            "HOME, 50, 57, -5.0, WEAKER, 4-5-1, COUNTER_ATTACK",
            "HOME, 50, 54, -2.0, WEAKER, 4-5-1, COUNTER_ATTACK",
            "HOME, 50, 53, -1.0, EVEN, 4-4-2, BALANCED",
            "AWAY, 53, 50, 1.0, EVEN, 4-4-2, BALANCED",
            "AWAY, 54, 50, 2.0, STRONGER, 3-5-2, POSSESSION",
            "HOME, 53, 50, 5.0, STRONGER, 3-5-2, POSSESSION",
            "HOME, 54, 50, 6.0, MUCH_STRONGER, 4-3-3, ATTACKING",
            "HOME, 60, 50, 12.0, MUCH_STRONGER, 4-3-3, ATTACKING"})
    void everyBandGivesItsFormationAndStyleAndTheBoundariesBelongToTheOuterBand(MatchSide side, int ownRating,
            int opponentRating, double expectedEvaluation, AssessmentBand expectedBand, String expectedFormation,
            PlayStyle expectedStyle) {
        Squad own = TestSquads.fullSquad("A", ownRating);
        Squad opponent = TestSquads.fullSquad("B", opponentRating);

        PreMatchDecision decision = choice.decide(own, opponent, side);

        assertThat(decision.evaluation()).isEqualTo(expectedEvaluation);
        assertThat(decision.band()).isEqualTo(expectedBand);
        assertThat(decision.formation().label()).isEqualTo(expectedFormation);
        assertThat(decision.team().style()).isEqualTo(expectedStyle);
    }

    @Test
    void awayAgainstAMuchStrongerSquadThatAllowsOnlyTheFourThreeThreeTheStyleIsDefensive() {
        List<Player> players = TestSquads.uniformPlayers("A", 1, 4, 3, 3, 50);
        Squad own = TestSquads.squad("A", players);
        Squad opponent = TestSquads.fullSquad("B", 70);

        PreMatchDecision decision = choice.decide(own, opponent, MatchSide.AWAY);

        assertThat(decision.band()).isEqualTo(AssessmentBand.MUCH_WEAKER);
        assertThat(decision.formation().label()).isEqualTo("4-3-3");
        assertThat(decision.team().style()).isEqualTo(PlayStyle.DEFENSIVE);
    }

    @ParameterizedTest
    @CsvSource({"5, 3, 2", "4, 5, 1", "4, 4, 2", "3, 5, 2", "4, 3, 3", "4, 5, 2", "5, 5, 5"})
    void withAnEvaluationOfMinusSixOrLessTheOverallGradeIsNeverPositive(int defenders, int midfielders,
            int forwards) {
        Squad own = TestSquads.uniformSquad("A", 1, defenders, midfielders, forwards, 50);
        Squad opponent = TestSquads.fullSquad("B", 80);

        PreMatchDecision decision = choice.decide(own, opponent, MatchSide.AWAY);

        assertThat(decision.evaluation()).isLessThanOrEqualTo(-6.0);
        assertThat(overallGradeOf(decision)).isLessThanOrEqualTo(0);
    }

    @Test
    void whenTheFormationOfTheBandIsNotFeasibleTheClosestGradeIsChosenAndTheMoreCautiousWinsATie() {
        Squad own = TestSquads.uniformSquad("A", 1, 5, 3, 3, 50);
        Squad opponent = TestSquads.fullSquad("B", 52);

        PreMatchDecision decision = choice.decide(own, opponent, MatchSide.HOME);

        assertThat(decision.band()).isEqualTo(AssessmentBand.EVEN);
        assertThat(decision.formation().label()).isEqualTo("5-3-2");
        assertThat(decision.team().style()).isEqualTo(PlayStyle.BALANCED);
    }

    @Test
    void whenTheFormationOfTheBandIsNotFeasibleTheClosestGradeBeatsTheMoreCautiousOne() {
        Squad own = TestSquads.uniformSquad("A", 1, 5, 3, 3, 50);
        Squad opponent = TestSquads.fullSquad("B", 48);

        PreMatchDecision decision = choice.decide(own, opponent, MatchSide.HOME);

        assertThat(decision.band()).isEqualTo(AssessmentBand.STRONGER);
        assertThat(decision.formation().label()).isEqualTo("4-3-3");
    }

    @Test
    void aFallbackMoreOffensiveThanTheBandMakesTheStyleDefensive() {
        Squad own = TestSquads.uniformSquad("A", 1, 5, 3, 3, 50);
        Squad opponent = TestSquads.fullSquad("B", 48);

        PreMatchDecision decision = choice.decide(own, opponent, MatchSide.HOME);

        assertThat(decision.formation().grade()).isGreaterThan(
                defaults.bandResponses().responseOf(AssessmentBand.STRONGER).formationGrade());
        assertThat(decision.team().style()).isEqualTo(PlayStyle.DEFENSIVE);
    }

    @Test
    void aFallbackMoreCautiousThanTheBandKeepsTheStyleOfTheBand() {
        Squad own = TestSquads.uniformSquad("A", 1, 4, 4, 2, 50);

        PreMatchDecision stronger = choice.decide(own, TestSquads.fullSquad("B", 48), MatchSide.HOME);
        PreMatchDecision muchStronger = choice.decide(own, TestSquads.fullSquad("B", 40), MatchSide.HOME);

        assertThat(stronger.formation().label()).isEqualTo("4-4-2");
        assertThat(stronger.team().style()).isEqualTo(PlayStyle.POSSESSION);
        assertThat(muchStronger.formation().label()).isEqualTo("4-4-2");
        assertThat(muchStronger.team().style()).isEqualTo(PlayStyle.ATTACKING);
    }

    @Test
    void whenTwoFormationsHaveTheSameGradeTheCatalogOrderDecides() {
        Formation threeFiveTwo = new Formation(3, 5, 2, 0);
        Formation fourFourTwo = new Formation(4, 4, 2, 0);
        Squad own = TestSquads.uniformSquad("A", 1, 4, 5, 2, 50);
        Squad opponent = TestSquads.fullSquad("B", 52);
        LineupChoice threeFiveTwoFirst = choiceWithCatalog(threeFiveTwo, fourFourTwo);
        LineupChoice fourFourTwoFirst = choiceWithCatalog(fourFourTwo, threeFiveTwo);

        PreMatchDecision first = threeFiveTwoFirst.decide(own, opponent, MatchSide.HOME);
        PreMatchDecision second = fourFourTwoFirst.decide(own, opponent, MatchSide.HOME);

        assertThat(first.formation()).isEqualTo(threeFiveTwo);
        assertThat(second.formation()).isEqualTo(fourFourTwo);
    }

    private LineupChoice choiceWithCatalog(Formation first, Formation second) {
        FormationCatalog catalog = new FormationCatalog(List.of(first, second));
        CoachConfig config = configWith(defaults.bandResponses(), catalog, defaults.maximumBenchSize());
        return new LineupChoice(config);
    }

    @Test
    void theExampleOfTheSpecIsMuchWeakerAndPlaysTheFiveThreeTwoDefensively() {
        Squad own = squadWithMovementSums("A", new int[] {66, 66, 66, 66, 67, 67, 67}, 66);
        Squad opponent = squadWithMovementSums("B", new int[] {72, 72, 72, 72, 72, 72, 71}, 72);

        PreMatchDecision decision = choice.decide(own, opponent, MatchSide.AWAY);

        assertThat(decision.evaluation()).isCloseTo(-7.5, within(0.05));
        assertThat(decision.band()).isEqualTo(AssessmentBand.MUCH_WEAKER);
        assertThat(decision.formation().label()).isEqualTo("5-3-2");
        assertThat(decision.team().style()).isEqualTo(PlayStyle.DEFENSIVE);
    }

    private Squad squadWithMovementSums(String name, int[] movementAttributes, int goalkeeping) {
        List<Player> players = new ArrayList<>();
        players.add(TestSquads.goalkeeper(name + "-gk1", goalkeeping));
        players.add(TestSquads.goalkeeper(name + "-gk2", goalkeeping));
        Role[] sectors = {Role.DEFENDER, Role.MIDFIELDER, Role.FORWARD};
        int[] sizes = {6, 6, 4};
        for (int sector = 0; sector < sectors.length; sector++) {
            for (int index = 1; index <= sizes[sector]; index++) {
                players.add(movementPlayer(name + "-" + sectors[sector] + index, sectors[sector],
                        movementAttributes));
            }
        }
        return TestSquads.squad(name, players);
    }

    private Player movementPlayer(String name, Role role, int[] a) {
        PlayerAttributes attributes = new PlayerAttributes(a[0], a[1], a[2], a[3], a[4], a[5], a[6], 1);
        return new Player(name, role, attributes);
    }

    private List<Player> interleavedRoster() {
        return List.of(
                TestSquads.goalkeeper("g1", 50),
                TestSquads.outfield("d1", Role.DEFENDER, 60),
                TestSquads.outfield("m1", Role.MIDFIELDER, 70),
                TestSquads.outfield("f1", Role.FORWARD, 55),
                TestSquads.outfield("d2", Role.DEFENDER, 55),
                TestSquads.outfield("m2", Role.MIDFIELDER, 40),
                TestSquads.goalkeeper("g2", 70),
                TestSquads.outfield("d3", Role.DEFENDER, 50),
                TestSquads.outfield("m3", Role.MIDFIELDER, 65),
                TestSquads.outfield("f2", Role.FORWARD, 70),
                TestSquads.outfield("d4", Role.DEFENDER, 50),
                TestSquads.outfield("m4", Role.MIDFIELDER, 40),
                TestSquads.outfield("d5", Role.DEFENDER, 50),
                TestSquads.outfield("m5", Role.MIDFIELDER, 60),
                TestSquads.outfield("f3", Role.FORWARD, 45),
                TestSquads.outfield("d6", Role.DEFENDER, 40),
                TestSquads.outfield("m6", Role.MIDFIELDER, 40),
                TestSquads.outfield("f4", Role.FORWARD, 45));
    }

    @Test
    void theStartersAreTheBestGoalkeeperAndTheBestOfEverySectorWithTheRosterOrderBreakingTies() {
        LineupChoice fourFourTwo = choiceAlwaysAimingAtTheFourFourTwo(9);
        Squad own = TestSquads.squad("A", interleavedRoster());
        Squad opponent = TestSquads.fullSquad("B", 50);

        PreMatchDecision decision = fourFourTwo.decide(own, opponent, MatchSide.HOME);

        assertThat(decision.formation().label()).isEqualTo("4-4-2");
        assertThat(namesOf(decision.team().starters()))
                .containsExactly("d1", "m1", "f1", "d2", "m2", "g2", "d3", "m3", "f2", "d4", "m5");
    }

    @Test
    void aShortRosterPutsEveryNonStarterOnTheBenchInRosterOrder() {
        LineupChoice fourFourTwo = choiceAlwaysAimingAtTheFourFourTwo(9);
        Squad own = TestSquads.squad("A", interleavedRoster());
        Squad opponent = TestSquads.fullSquad("B", 50);

        PreMatchDecision decision = fourFourTwo.decide(own, opponent, MatchSide.HOME);

        assertThat(namesOf(decision.team().bench())).containsExactly("g1", "m4", "d5", "f3", "d6", "m6", "f4");
    }

    @Test
    void theBenchStartsWithTheGoalkeeperThenRotatesThroughDefenderMidfielderForward() {
        LineupChoice smallBench = choiceAlwaysAimingAtTheFourFourTwo(5);
        Squad own = TestSquads.squad("A", interleavedRoster());
        Squad opponent = TestSquads.fullSquad("B", 50);

        PreMatchDecision decision = smallBench.decide(own, opponent, MatchSide.HOME);

        assertThat(namesOf(decision.team().bench())).containsExactly("g1", "m4", "d5", "f3", "d6");
    }

    private List<Player> largeRosterRankedByName() {
        List<Player> players = new ArrayList<>();
        players.add(TestSquads.goalkeeper("g1", 90));
        players.add(TestSquads.goalkeeper("g2", 80));
        players.add(TestSquads.goalkeeper("g3", 70));
        players.addAll(rankedGroup("d", Role.DEFENDER, 9, 90));
        players.addAll(rankedGroup("m", Role.MIDFIELDER, 8, 90));
        players.addAll(rankedGroup("f", Role.FORWARD, 3, 90));
        return players;
    }

    private List<Player> rankedGroup(String prefix, Role role, int count, int best) {
        List<Player> players = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            players.add(TestSquads.outfield(prefix + index, role, best - index + 1));
        }
        return players;
    }

    @Test
    void theBenchHoldsAtMostTheMaximumSkippingTheSectorsThatRunOut() {
        LineupChoice fourFourTwo = choiceAlwaysAimingAtTheFourFourTwo(9);
        Squad own = TestSquads.squad("A", largeRosterRankedByName());
        Squad opponent = TestSquads.fullSquad("B", 50);

        PreMatchDecision decision = fourFourTwo.decide(own, opponent, MatchSide.HOME);

        assertThat(decision.team().bench()).hasSize(9);
        assertThat(namesOf(decision.team().bench()))
                .containsExactly("g2", "d5", "d6", "d7", "d8", "m5", "m6", "m7", "f3");
    }

    @Test
    void startersAndBenchDoNotShareAPlayerAndTheTeamIsBuiltWithTheSquadNameAndTheChosenStyle() {
        LineupChoice fourFourTwo = choiceAlwaysAimingAtTheFourFourTwo(9);
        Squad own = TestSquads.squad("A", largeRosterRankedByName());
        Squad opponent = TestSquads.fullSquad("B", 50);

        Team team = fourFourTwo.decide(own, opponent, MatchSide.HOME).team();

        assertThat(team.name()).isEqualTo("A");
        assertThat(team.style()).isEqualTo(PlayStyle.BALANCED);
        assertThat(team.starters()).doesNotContainAnyElementsOf(team.bench());
    }

    @Test
    void aRosterWithoutAFeasibleFormationIsRejected() {
        Squad own = TestSquads.uniformSquad("A", 1, 2, 7, 1, 50);
        Squad opponent = TestSquads.fullSquad("B", 50);

        assertThatThrownBy(() -> choice.decide(own, opponent, MatchSide.HOME))
                .isInstanceOfSatisfying(SquadRuleViolation.class,
                        violation -> assertThat(violation.rule()).isEqualTo(SquadRule.NO_FEASIBLE_FORMATION));
    }

    @Test
    void theChoiceOfASquadDoesNotDependOnWhenTheOtherSquadHasChosen() {
        Squad home = TestSquads.fullSquad("A", 60);
        Squad away = TestSquads.fullSquad("B", 52);

        PreMatchDecision homeFirst = choice.decide(home, away, MatchSide.HOME);
        PreMatchDecision awayAfter = choice.decide(away, home, MatchSide.AWAY);
        PreMatchDecision awayFirst = choice.decide(away, home, MatchSide.AWAY);
        PreMatchDecision homeAfter = choice.decide(home, away, MatchSide.HOME);

        assertThat(homeAfter).isEqualTo(homeFirst);
        assertThat(awayFirst).isEqualTo(awayAfter);
    }
}
