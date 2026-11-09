package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.DurationRange;
import org.tasktelemetry.matchsimulator.engine.config.InjuryConfig;
import org.tasktelemetry.matchsimulator.engine.config.LineupConfig;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class InjuryResolverTest {

    private static final double EXACT = 1e-9;
    private static final double ONE_POINT = 0.01;
    private static final int DRAWS = 20_000;
    private static final int STARTER_RATING = 60;
    private static final long SEED = 7L;

    private final LineupConfig lineupConfig = LineupConfig.defaults();

    @Test
    void theInjuryProbabilityIsTheConfiguredOneAndNeverZeroWithTheDefaults() {
        InjuryResolver resolver = resolverWith(InjuryConfig.defaults());

        assertThat(resolver.injuryProbability()).isEqualTo(InjuryConfig.defaults().probabilityPerAction(),
                within(EXACT));
        assertThat(resolver.injuryProbability()).isPositive();
    }

    @Test
    void theFrequencyOfInjuriesFollowsTheProbabilityAtAFixedSeed() {
        InjuryResolver resolver = resolverWith(new InjuryConfig(0.30, new DurationRange(60, 150)));
        TeamOnPitch team = TeamOnPitch.startingWith(teamWithBench(List.of()));

        int injuries = countInjuries(resolver, team);

        assertThat(injuries / (double) DRAWS).isCloseTo(0.30, within(ONE_POINT));
    }

    @Test
    void withAZeroProbabilityNobodyIsEverInjured() {
        InjuryResolver resolver = resolverWith(new InjuryConfig(0.0, new DurationRange(60, 150)));
        TeamOnPitch team = TeamOnPitch.startingWith(teamWithBench(List.of()));

        assertThat(countInjuries(resolver, team)).isZero();
    }

    @Test
    void everyPlayerOnThePitchGoalkeeperIncludedHasTheSameChanceOfBeingTheInjuredOne() {
        InjuryResolver resolver = resolverWith(new InjuryConfig(0.99, new DurationRange(60, 150)));
        TeamOnPitch team = TeamOnPitch.startingWith(teamWithBench(List.of()));
        Map<Player, Integer> injuredCount = new LinkedHashMap<>();

        for (int draw = 0; draw < DRAWS; draw++) {
            Optional<Player> injured = resolver.injuredIn(team);
            injured.ifPresent(player -> injuredCount.merge(player, 1, Integer::sum));
        }

        int totalInjuries = injuredCount.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(injuredCount.keySet()).containsExactlyInAnyOrderElementsOf(team.players());
        assertThat(injuredCount.values()).allSatisfy(count ->
                assertThat(count / (double) totalInjuries).isCloseTo(1.0 / team.players().size(), within(ONE_POINT)));
    }

    @Test
    void anInjuryLastsBetweenTheConfiguredBoundsAndBothBoundsAreReached() {
        DurationRange range = new DurationRange(60, 150);
        InjuryResolver resolver = resolverWith(new InjuryConfig(0.5, range));

        List<Integer> seconds = new ArrayList<>();
        for (int draw = 0; draw < DRAWS; draw++) {
            seconds.add(resolver.drawInjurySeconds());
        }

        assertThat(seconds).allMatch(range::contains);
        assertThat(seconds).contains(range.minSeconds(), range.maxSeconds());
    }

    @Test
    void anInjuredOutfielderIsReplacedFromTheBenchAndTheTeamStaysAtEleven() {
        Team team = teamWithBench(List.of(player("BD", Role.DEFENDER, 70)));
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        Player injured = find(team, "D2");

        LineupOutcome outcome = resolver().replaceInjured(onPitch, injured);

        assertThat(outcome.suspendsTheMatch()).isFalse();
        assertThat(outcome.substitutions()).containsExactly(new Substitution(injured, find(team, "BD")));
        assertThat(outcome.team().players()).hasSize(11).doesNotContain(injured).contains(find(team, "BD"));
        assertThat(outcome.team().substitutionsMade()).isEqualTo(1);
        assertThat(outcome.team().playersIn(Role.DEFENDER)).hasSize(4);
    }

    @Test
    void withoutSubstitutesLeftTheInjuredLeavesAndTheTeamPlaysInInferiority() {
        Team team = teamWithBench(List.of(player("BD", Role.DEFENDER, 70)));
        TeamOnPitch exhausted = withSubstitutionsMade(TeamOnPitch.startingWith(team),
                lineupConfig.maximumSubstitutions());
        Player injured = find(team, "D2");

        LineupOutcome outcome = resolver().replaceInjured(exhausted, injured);

        assertThat(outcome.suspendsTheMatch()).isFalse();
        assertThat(outcome.substitutions()).isEmpty();
        assertThat(outcome.team().players()).hasSize(10).doesNotContain(injured);
        assertThat(outcome.team().substitutionsMade()).isEqualTo(lineupConfig.maximumSubstitutions());
    }

    @Test
    void withAnEmptyBenchTheInjuredLeavesWithoutSubstitution() {
        Team team = teamWithBench(List.of());
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);

        LineupOutcome outcome = resolver().replaceInjured(onPitch, find(team, "F1"));

        assertThat(outcome.substitutions()).isEmpty();
        assertThat(outcome.team().players()).hasSize(10);
    }

    @Test
    void theFifthSubstitutionIsAllowedAndTheSixthInjuryIsNotSubstituted() {
        Team team = teamWithBench(List.of(player("B1", Role.MIDFIELDER, 70), player("B2", Role.MIDFIELDER, 70),
                player("B3", Role.MIDFIELDER, 70), player("B4", Role.MIDFIELDER, 70),
                player("B5", Role.MIDFIELDER, 70), player("B6", Role.MIDFIELDER, 70)));
        TeamOnPitch current = TeamOnPitch.startingWith(team);
        String[] injuredNames = {"M1", "M2", "M3", "M4", "F1", "F2"};

        List<LineupOutcome> outcomes = new ArrayList<>();
        for (String name : injuredNames) {
            LineupOutcome outcome = resolver().replaceInjured(current, find(team, name));
            outcomes.add(outcome);
            current = outcome.team();
        }

        assertThat(outcomes.subList(0, 5)).allSatisfy(outcome -> assertThat(outcome.substitutions()).hasSize(1));
        assertThat(outcomes.get(5).substitutions()).isEmpty();
        assertThat(current.substitutionsMade()).isEqualTo(5);
        assertThat(current.players()).hasSize(10);
    }

    @Test
    void anInjuredPlayerWhoHadReplacedAnotherCanBeReplacedByTheNextBenchPlayer() {
        Team team = teamWithBench(List.of(player("BD1", Role.DEFENDER, 70), player("BD2", Role.DEFENDER, 65)));
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        LineupOutcome first = resolver().replaceInjured(onPitch, find(team, "D1"));

        LineupOutcome second = resolver().replaceInjured(first.team(), find(team, "BD1"));

        assertThat(second.substitutions()).containsExactly(new Substitution(find(team, "BD1"), find(team, "BD2")));
        assertThat(second.team().players()).doesNotContain(find(team, "BD1")).contains(find(team, "BD2"));
    }

    @Test
    void anUnreplacedInjuryThatLeavesSixSuspendsTheMatch() {
        Team team = teamWithBench(List.of());
        TeamOnPitch sevenOnThePitch = pitchWithout(team, "D3", "D4", "M3", "M4");

        LineupOutcome outcome = resolver().replaceInjured(sevenOnThePitch, find(team, "F1"));

        assertThat(sevenOnThePitch.players()).hasSize(7);
        assertThat(outcome.suspendsTheMatch()).isTrue();
        assertThat(outcome.team().players()).hasSize(6);
        assertThat(outcome.substitutions()).isEmpty();
    }

    @Test
    void anInjuryOfWhoHasNoSubstituteBecauseTheLimitIsReachedAlsoSuspendsAtSix() {
        Team team = teamWithBench(List.of(player("BD", Role.DEFENDER, 70)));
        TeamOnPitch sevenOnThePitch = withSubstitutionsMade(pitchWithout(team, "D3", "D4", "M3", "M4"),
                lineupConfig.maximumSubstitutions());

        LineupOutcome outcome = resolver().replaceInjured(sevenOnThePitch, find(team, "F1"));

        assertThat(outcome.suspendsTheMatch()).isTrue();
    }

    @Test
    void aSubstitutedInjuryWithSevenOnThePitchDoesNotSuspendTheMatch() {
        Team team = teamWithBench(List.of(player("BF", Role.FORWARD, 70)));
        TeamOnPitch sevenOnThePitch = pitchWithout(team, "D3", "D4", "M3", "M4");

        LineupOutcome outcome = resolver().replaceInjured(sevenOnThePitch, find(team, "F1"));

        assertThat(outcome.suspendsTheMatch()).isFalse();
        assertThat(outcome.team().players()).hasSize(7);
        assertThat(outcome.substitutions()).hasSize(1);
    }

    @Test
    void anUnreplacedInjuryWithEightOnThePitchContinuesWithSeven() {
        Team team = teamWithBench(List.of());
        TeamOnPitch eightOnThePitch = pitchWithout(team, "D3", "D4", "M4");

        LineupOutcome outcome = resolver().replaceInjured(eightOnThePitch, find(team, "F1"));

        assertThat(outcome.suspendsTheMatch()).isFalse();
        assertThat(outcome.team().players()).hasSize(7);
    }

    @Test
    void anUnreplacedInjuryThatEmptiesASectorIsRepairedFromTheNearestSector() {
        Team team = teamWithOneForward(List.of());
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);

        LineupOutcome outcome = resolver().replaceInjured(onPitch, find(team, "F1"));

        assertThat(outcome.suspendsTheMatch()).isFalse();
        assertThat(outcome.team().playersIn(Role.FORWARD)).hasSize(1);
        assertThat(outcome.team().playersIn(Role.MIDFIELDER)).hasSize(4);
        assertThat(outcome.team().players()).hasSize(10);
    }

    @Test
    void anInjuredGoalkeeperIsReplacedByTheReserveGoalkeeperAndTheTeamStaysAtEleven() {
        Team team = teamWithBench(List.of(player("BF", Role.FORWARD, 99), player("BG", Role.GOALKEEPER, 40)));
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        Player injured = find(team, "GK");

        LineupOutcome outcome = resolver().replaceInjured(onPitch, injured);

        assertThat(outcome.suspendsTheMatch()).isFalse();
        assertThat(outcome.substitutions()).containsExactly(new Substitution(injured, find(team, "BG")));
        assertThat(outcome.team().goalkeeper()).isEqualTo(find(team, "BG"));
        assertThat(outcome.team().players()).hasSize(11);
        assertThat(outcome.team().substitutionsMade()).isEqualTo(1);
    }

    @Test
    void anInjuredGoalkeeperWithoutAReserveIsReplacedByTheBestOutfielderThenTheBestGoalkeepingGoesInGoal() {
        Team team = teamWithBench(List.of(player("BM", Role.MIDFIELDER, 65), player("BD", Role.DEFENDER, 70)));
        Player specialist = find(team, "M2");
        Team withSpecialist = replaceStarter(team, specialist, goalkeepingSpecialist("M2", Role.MIDFIELDER, 90));
        TeamOnPitch onPitch = TeamOnPitch.startingWith(withSpecialist);
        Player injured = find(withSpecialist, "GK");

        LineupOutcome outcome = resolver().replaceInjured(onPitch, injured);

        Player newGoalkeeper = find(withSpecialist, "M2");
        assertThat(outcome.suspendsTheMatch()).isFalse();
        assertThat(outcome.substitutions()).containsExactly(new Substitution(injured, find(withSpecialist, "BD")));
        assertThat(outcome.team().goalkeeper()).isEqualTo(newGoalkeeper);
        assertThat(outcome.team().roleOf(newGoalkeeper)).isEqualTo(Role.GOALKEEPER);
        assertThat(outcome.team().players()).hasSize(11);
        assertThat(outcome.team().playersIn(Role.MIDFIELDER)).hasSize(3);
    }

    @Test
    void theEnteringOutfielderCountsAmongTheCandidatesForTheGoal() {
        Player keeperOnTheBench = goalkeepingSpecialist("BF", Role.FORWARD, 95);
        Team team = teamWithBench(List.of(keeperOnTheBench));
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);

        LineupOutcome outcome = resolver().replaceInjured(onPitch, find(team, "GK"));

        assertThat(outcome.team().goalkeeper()).isEqualTo(keeperOnTheBench);
        assertThat(outcome.team().playersIn(Role.FORWARD)).hasSize(2);
    }

    @Test
    void theGoalComesFirstAndThenTheSectorEmptiedByWhoGoesInGoalIsRepaired() {
        Team team = teamWithOneDefenderWhoIsTheBestGoalkeeper(List.of(player("BM", Role.MIDFIELDER, 65)));
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        Player injured = find(team, "GK");

        LineupOutcome outcome = resolver().replaceInjured(onPitch, injured);

        Player lastDefender = find(team, "D1");
        Player strongestMidfielder = find(team, "M4");
        assertThat(outcome.team().goalkeeper()).isEqualTo(lastDefender);
        assertThat(outcome.team().playersIn(Role.DEFENDER)).containsExactly(strongestMidfielder);
        assertThat(outcome.team().players()).hasSize(11);
        assertThat(outcome.substitutions()).hasSize(1);
    }

    @Test
    void anInjuredGoalkeeperWithoutSubstitutionsLeftIsNotReplacedEvenWithAReserveOnTheBench() {
        Team team = teamWithBench(List.of(player("BG", Role.GOALKEEPER, 40)));
        Team withSpecialist = replaceStarter(team, find(team, "M1"), goalkeepingSpecialist("M1", Role.MIDFIELDER, 85));
        TeamOnPitch exhausted = withSubstitutionsMade(TeamOnPitch.startingWith(withSpecialist),
                lineupConfig.maximumSubstitutions());

        LineupOutcome outcome = resolver().replaceInjured(exhausted, find(withSpecialist, "GK"));

        assertThat(outcome.suspendsTheMatch()).isFalse();
        assertThat(outcome.substitutions()).isEmpty();
        assertThat(outcome.team().goalkeeper()).isEqualTo(find(withSpecialist, "M1"));
        assertThat(outcome.team().players()).hasSize(10).doesNotContain(find(withSpecialist, "BG"));
    }

    @Test
    void anInjuredGoalkeeperWithAnEmptyBenchIsReplacedByTheBestGoalkeepingOnThePitch() {
        Team team = teamWithBench(List.of());
        Team withSpecialist = replaceStarter(team, find(team, "F2"), goalkeepingSpecialist("F2", Role.FORWARD, 85));
        TeamOnPitch onPitch = TeamOnPitch.startingWith(withSpecialist);

        LineupOutcome outcome = resolver().replaceInjured(onPitch, find(withSpecialist, "GK"));

        assertThat(outcome.substitutions()).isEmpty();
        assertThat(outcome.team().goalkeeper()).isEqualTo(find(withSpecialist, "F2"));
        assertThat(outcome.team().playersIn(Role.FORWARD)).hasSize(1);
    }

    @Test
    void anInjuredGoalkeeperWithoutSubstitutesIsCountedByTheRuleOfSevenBeforeTheOtherSteps() {
        Team team = teamWithBench(List.of());
        TeamOnPitch eightOnThePitch = pitchWithout(team, "D3", "D4", "M4");
        TeamOnPitch sevenOnThePitch = pitchWithout(team, "D3", "D4", "M3", "M4");

        LineupOutcome stillSeven = resolver().replaceInjured(eightOnThePitch, find(team, "GK"));
        LineupOutcome sixLeft = resolver().replaceInjured(sevenOnThePitch, find(team, "GK"));

        assertThat(stillSeven.suspendsTheMatch()).isFalse();
        assertThat(stillSeven.team().players()).hasSize(7);
        assertThat(stillSeven.team().playersIn(Role.GOALKEEPER)).hasSize(1);
        assertThat(sixLeft.suspendsTheMatch()).isTrue();
        assertThat(sixLeft.team().players()).hasSize(6);
        assertThat(sixLeft.team().playersIn(Role.GOALKEEPER)).isEmpty();
    }

    @Test
    void anInjuredGoalkeeperSubstitutedBeforeTheCountNeverSuspendsAtSeven() {
        Team team = teamWithBench(List.of(player("BG", Role.GOALKEEPER, 40)));
        TeamOnPitch sevenOnThePitch = pitchWithout(team, "D3", "D4", "M3", "M4");

        LineupOutcome outcome = resolver().replaceInjured(sevenOnThePitch, find(team, "GK"));

        assertThat(outcome.suspendsTheMatch()).isFalse();
        assertThat(outcome.team().goalkeeper()).isEqualTo(find(team, "BG"));
        assertThat(outcome.team().players()).hasSize(7);
    }

    @Test
    void theResolutionLeavesTheOriginalTeamUntouched() {
        Team team = teamWithBench(List.of(player("BD", Role.DEFENDER, 70)));
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        List<Player> before = onPitch.players();

        resolver().replaceInjured(onPitch, find(team, "D1"));

        assertThat(onPitch.players()).containsExactlyElementsOf(before);
        assertThat(onPitch.substitutionsMade()).isZero();
    }

    private InjuryResolver resolver() {
        return resolverWith(InjuryConfig.defaults());
    }

    private InjuryResolver resolverWith(InjuryConfig injuryConfig) {
        MatchRandom random = new MatchRandom(SEED);
        InjurySubstitution injurySubstitution = new InjurySubstitution(lineupConfig);
        MinimumPlayersRule minimumPlayers = new MinimumPlayersRule();
        LineupRepair repair = new LineupRepair(lineupConfig);
        return new InjuryResolver(injuryConfig, random, injurySubstitution, minimumPlayers, repair);
    }

    private int countInjuries(InjuryResolver resolver, TeamOnPitch team) {
        int injuries = 0;
        for (int draw = 0; draw < DRAWS; draw++) {
            if (resolver.injuredIn(team).isPresent()) {
                injuries++;
            }
        }
        return injuries;
    }

    private TeamOnPitch withSubstitutionsMade(TeamOnPitch team, int substitutions) {
        return new TeamOnPitch(team.team(), team.players(), team.reassigned(), team.cautioned(), substitutions);
    }

    private TeamOnPitch pitchWithout(Team team, String... leaving) {
        TeamOnPitch onPitch = TeamOnPitch.startingWith(team);
        for (String name : leaving) {
            onPitch = onPitch.withoutPlayer(find(team, name));
        }
        return onPitch;
    }

    private Team teamWithBench(List<Player> bench) {
        return teamOf(starters(4, 4, 2), bench);
    }

    private Team teamWithOneForward(List<Player> bench) {
        return teamOf(starters(4, 5, 1), bench);
    }

    private Team teamWithOneDefenderWhoIsTheBestGoalkeeper(List<Player> bench) {
        List<Player> starters = new ArrayList<>();
        starters.add(player("GK", Role.GOALKEEPER, STARTER_RATING));
        starters.add(goalkeepingSpecialist("D1", Role.DEFENDER, 90));
        starters.addAll(outfield("M", Role.MIDFIELDER, 3));
        starters.add(player("M4", Role.MIDFIELDER, 75));
        starters.add(player("M5", Role.MIDFIELDER, STARTER_RATING));
        starters.addAll(outfield("F", Role.FORWARD, 4));
        return teamOf(starters, bench);
    }

    private List<Player> starters(int defenders, int midfielders, int forwards) {
        List<Player> starters = new ArrayList<>();
        starters.add(player("GK", Role.GOALKEEPER, STARTER_RATING));
        starters.addAll(outfield("D", Role.DEFENDER, defenders));
        starters.addAll(outfield("M", Role.MIDFIELDER, midfielders));
        starters.addAll(outfield("F", Role.FORWARD, forwards));
        return starters;
    }

    private Team teamOf(List<Player> starters, List<Player> bench) {
        return new Team("T", PlayStyle.BALANCED, starters, bench);
    }

    private Team replaceStarter(Team team, Player replaced, Player replacement) {
        List<Player> starters = new ArrayList<>(team.starters());
        starters.set(starters.indexOf(replaced), replacement);
        return teamOf(starters, team.bench());
    }

    private List<Player> outfield(String prefix, Role role, int count) {
        List<Player> players = new ArrayList<>();
        for (int number = 1; number <= count; number++) {
            players.add(player(prefix + number, role, STARTER_RATING));
        }
        return players;
    }

    private Player player(String name, Role role, int rating) {
        return TestTeams.player(name, role, TestTeams.uniform(rating));
    }

    private Player goalkeepingSpecialist(String name, Role role, int goalkeeping) {
        PlayerAttributes attributes = new PlayerAttributes(STARTER_RATING, STARTER_RATING, STARTER_RATING,
                STARTER_RATING, STARTER_RATING, STARTER_RATING, STARTER_RATING, goalkeeping);
        return TestTeams.player(name, role, attributes);
    }

    private Player find(Team team, String name) {
        List<Player> squad = new ArrayList<>(team.starters());
        squad.addAll(team.bench());
        return find(squad, name);
    }

    private Player find(List<Player> squad, String name) {
        Optional<Player> found = squad.stream().filter(player -> player.name().equals(name)).findFirst();
        return found.orElseThrow();
    }
}
