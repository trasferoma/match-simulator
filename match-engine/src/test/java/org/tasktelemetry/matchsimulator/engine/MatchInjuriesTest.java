package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.InjuryConfig;
import org.tasktelemetry.matchsimulator.engine.config.LineupConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class MatchInjuriesTest {

    private static final int MATCHES = 60;
    private static final int MAXIMUM_SUBSTITUTIONS = 5;
    private static final double FREQUENT_INJURIES = 0.01;
    private static final double VERY_FREQUENT_INJURIES = 0.05;
    private static final int REGULATION_SECONDS = 45 * 60;
    private static final int STARTER_RATING = 60;

    private record Occurrence(List<MatchEvent> events, int index, int substitutionsBefore) {

        MatchEvent injury() {
            return events.get(index);
        }

        Player injured() {
            return injury().participants().primary().orElseThrow();
        }

        MatchSide side() {
            return injury().side();
        }
    }

    private final Team home = benchWithTwoGoalkeepers("Home");
    private final Team away = benchWithTwoGoalkeepers("Away");

    @Test
    void injuriesHappenInTheMatchesPlayedWithTheDefaultConfiguration() {
        List<List<MatchEvent>> matches = playMany(EngineConfig.defaults(), home, away, 200);

        long injuries = matches.stream().flatMap(List::stream).filter(this::isInjury).count();

        assertThat(injuries).isPositive();
    }

    @Test
    void anInjuryNamesTheInjuredPlayerOfTheSideLastsWithinTheConfiguredBoundsAndStartsBeforeTheRegulationEnds() {
        EngineConfig config = withInjuries(FREQUENT_INJURIES, MAXIMUM_SUBSTITUTIONS);
        List<MatchEvent> injuries = playMany(config, home, away, MATCHES).stream()
                .flatMap(List::stream)
                .filter(this::isInjury)
                .toList();

        assertThat(injuries).isNotEmpty().allSatisfy(injury -> {
            Team team = injury.side() == MatchSide.HOME ? home : away;
            assertThat(squadOf(team)).contains(injury.participants().primary().orElseThrow());
            assertThat(injury.participants().secondary()).isEmpty();
            assertThat(config.injury().duration().contains(injury.durationSeconds())).isTrue();
            assertThat(injury.time().seconds() - injury.durationSeconds()).isLessThan(REGULATION_SECONDS);
            assertThat(injury.time().period()).isIn(MatchPeriod.FIRST_HALF, MatchPeriod.SECOND_HALF);
        });
    }

    @Test
    void withAZeroProbabilityThereAreNoInjuriesAndNoSubstitutionsWithoutFouls() {
        EngineConfig config = withInjuries(0.0, MAXIMUM_SUBSTITUTIONS);

        List<MatchEvent> events = playMany(config, home, away, 20).stream().flatMap(List::stream).toList();

        assertThat(events).extracting(MatchEvent::type)
                .doesNotContain(EventType.INJURY, EventType.SUBSTITUTION);
    }

    @Test
    void anInjuredOutfielderIsSubstitutedRightAwayFromTheBenchWhileSubstitutionsAreLeft() {
        EngineConfig config = withInjuries(FREQUENT_INJURIES, MAXIMUM_SUBSTITUTIONS);
        List<Occurrence> outfieldInjuries = occurrencesIn(playMany(config, home, away, MATCHES)).stream()
                .filter(occurrence -> occurrence.injured().role() != Role.GOALKEEPER)
                .toList();

        assertThat(outfieldInjuries).isNotEmpty();
        int substituted = 0;
        for (Occurrence occurrence : outfieldInjuries) {
            Team team = occurrence.side() == MatchSide.HOME ? home : away;
            Set<Player> usedBench = benchPlayersUsedBefore(occurrence, team);
            List<Player> unusedBench = team.bench().stream().filter(player -> !usedBench.contains(player)).toList();
            List<Player> unusedOutfield = unusedBench.stream().filter(player -> player.role() != Role.GOALKEEPER)
                    .toList();
            boolean substitutionExpected = occurrence.substitutionsBefore() < MAXIMUM_SUBSTITUTIONS
                    && !unusedOutfield.isEmpty();
            MatchEvent next = occurrence.events().get(occurrence.index() + 1);
            if (!substitutionExpected) {
                assertThat(isSubstitutionOf(next, occurrence)).isFalse();
                continue;
            }
            assertThat(isSubstitutionOf(next, occurrence)).isTrue();
            Player entering = next.participants().secondary().orElseThrow();
            assertThat(unusedOutfield).contains(entering);
            boolean sameRoleAvailable = unusedOutfield.stream()
                    .anyMatch(player -> player.role() == occurrence.injured().role());
            if (sameRoleAvailable) {
                assertThat(entering.role()).isEqualTo(occurrence.injured().role());
            }
            substituted++;
        }
        assertThat(substituted).isPositive();
    }

    @Test
    void aSubstitutionNamesWhoLeavesAndWhoEntersAndLastsWithinTheConfiguredBounds() {
        EngineConfig config = withInjuries(FREQUENT_INJURIES, MAXIMUM_SUBSTITUTIONS);
        List<MatchEvent> substitutions = playMany(config, home, away, MATCHES).stream()
                .flatMap(List::stream)
                .filter(event -> event.type() == EventType.SUBSTITUTION)
                .toList();

        assertThat(substitutions).isNotEmpty().allSatisfy(substitution -> {
            Team team = substitution.side() == MatchSide.HOME ? home : away;
            Player leaving = substitution.participants().primary().orElseThrow();
            Player entering = substitution.participants().secondary().orElseThrow();
            assertThat(team.bench()).contains(entering);
            assertThat(squadOf(team)).contains(leaving);
            assertThat(config.lineup().substitutionDuration().contains(substitution.durationSeconds())).isTrue();
        });
    }

    @Test
    void anInjuredPlayerAndAReplacedOneNeverAppearInAnyLaterEvent() {
        EngineConfig config = withInjuries(FREQUENT_INJURIES, MAXIMUM_SUBSTITUTIONS);

        for (List<MatchEvent> events : playMany(config, home, away, MATCHES)) {
            for (Occurrence occurrence : occurrencesIn(List.of(events))) {
                Player injured = occurrence.injured();
                List<MatchEvent> later = events.subList(occurrence.index() + 1, events.size()).stream()
                        .filter(event -> !isSubstitutionOf(event, occurrence))
                        .toList();
                assertThat(later).noneMatch(event -> involves(event, injured));
            }
        }
    }

    @Test
    void aSideNeverMakesMoreThanFiveSubstitutionsAndAfterTheFifthAnInjuryLeavesItInInferiority() {
        EngineConfig config = withInjuries(VERY_FREQUENT_INJURIES, MAXIMUM_SUBSTITUTIONS);
        int unsubstitutedAfterTheLimit = 0;

        for (List<MatchEvent> events : playMany(config, home, away, MATCHES)) {
            for (MatchSide side : MatchSide.values()) {
                assertThat(substitutionsOf(events, side)).isLessThanOrEqualTo(MAXIMUM_SUBSTITUTIONS);
            }
            for (Occurrence occurrence : occurrencesIn(List.of(events))) {
                boolean limitReached = occurrence.substitutionsBefore() >= MAXIMUM_SUBSTITUTIONS;
                if (limitReached && !isLast(events, occurrence.index())) {
                    assertThat(isSubstitutionOf(events.get(occurrence.index() + 1), occurrence)).isFalse();
                    unsubstitutedAfterTheLimit++;
                }
            }
        }
        assertThat(unsubstitutedAfterTheLimit).isPositive();
    }

    @Test
    void anInjuredGoalkeeperIsReplacedByTheReserveGoalkeeperWhoTakesTheGoal() {
        Team teamWithReserve = teamWithSpecialistAndBench("Home", true);
        EngineConfig config = withInjuries(FREQUENT_INJURIES, MAXIMUM_SUBSTITUTIONS);
        Player reserve = goalkeeperOf(teamWithReserve.bench());
        int checked = 0;
        int saves = 0;

        List<Occurrence> withSubstitutionsLeft = starterGoalkeeperInjuries(config, teamWithReserve).stream()
                .filter(occurrence -> occurrence.substitutionsBefore() < MAXIMUM_SUBSTITUTIONS)
                .toList();
        for (Occurrence occurrence : withSubstitutionsLeft) {
            MatchEvent next = occurrence.events().get(occurrence.index() + 1);
            assertThat(isSubstitutionOf(next, occurrence)).isTrue();
            assertThat(next.participants().secondary()).contains(reserve);
            List<Player> goalkeepers = goalkeepersWhoSavedAfter(occurrence);
            assertThat(goalkeepers).allMatch(reserve::equals);
            saves += goalkeepers.size();
            checked++;
        }
        assertThat(checked).isPositive();
        assertThat(saves).isPositive();
    }

    @Test
    void anInjuredGoalkeeperWithoutAReserveIsReplacedByTheBestOutfielderThenTheBestGoalkeepingGoesInGoal() {
        Team teamWithoutReserve = teamWithSpecialistAndBench("Home", false);
        Player specialist = specialistOf(teamWithoutReserve);
        Player bestOutfielderOnTheBench = playerNamed(teamWithoutReserve.bench(), "Home-B-FORWARD-1");
        EngineConfig config = withInjuries(FREQUENT_INJURIES, MAXIMUM_SUBSTITUTIONS);
        int checked = 0;
        int saves = 0;

        List<Occurrence> firstInjuriesOfTheSide = starterGoalkeeperInjuries(config, teamWithoutReserve).stream()
                .filter(occurrence -> occurrence.substitutionsBefore() == 0)
                .toList();
        for (Occurrence occurrence : firstInjuriesOfTheSide) {
            MatchEvent next = occurrence.events().get(occurrence.index() + 1);
            assertThat(isSubstitutionOf(next, occurrence)).isTrue();
            assertThat(next.participants().secondary()).contains(bestOutfielderOnTheBench);
            List<Player> goalkeepers = goalkeepersWhoSavedAfter(occurrence);
            assertThat(goalkeepers).allMatch(specialist::equals);
            saves += goalkeepers.size();
            checked++;
        }
        assertThat(checked).isPositive();
        assertThat(saves).isPositive();
    }

    @Test
    void anInjuredGoalkeeperWithoutSubstitutionsLeftIsNotReplacedAndTheBestGoalkeepingGoesInGoal() {
        Team teamWithReserve = teamWithSpecialistAndBench("Home", true);
        Player specialist = specialistOf(teamWithReserve);
        EngineConfig config = withInjuries(FREQUENT_INJURIES, 1);
        int checked = 0;
        int saves = 0;

        List<Occurrence> afterTheOnlySubstitution = starterGoalkeeperInjuries(config, teamWithReserve).stream()
                .filter(occurrence -> occurrence.substitutionsBefore() == 1)
                .filter(occurrence -> !specialistWasInjuredBefore(occurrence, specialist))
                .toList();
        for (Occurrence occurrence : afterTheOnlySubstitution) {
            MatchEvent next = occurrence.events().get(occurrence.index() + 1);
            assertThat(isSubstitutionOf(next, occurrence)).isFalse();
            List<Player> goalkeepers = goalkeepersWhoSavedAfter(occurrence);
            assertThat(goalkeepers).allMatch(specialist::equals);
            saves += goalkeepers.size();
            checked++;
        }
        assertThat(checked).isPositive();
        assertThat(saves).isPositive();
    }

    @Test
    void aTeamReducedToSixByInjuriesWithoutSubstitutionsSuspendsTheMatchRightAfterTheLastInjury() {
        EngineConfig config = withInjuries(VERY_FREQUENT_INJURIES, 1);
        int suspended = 0;

        for (List<MatchEvent> events : playMany(config, home, away, MATCHES)) {
            MatchEvent last = events.get(events.size() - 1);
            if (last.type() != EventType.MATCH_SUSPENDED) {
                continue;
            }
            MatchEvent beforeTheSuspension = events.get(events.size() - 2);
            EventDetail.Suspension suspension = (EventDetail.Suspension) last.detail();
            assertThat(beforeTheSuspension.type()).isEqualTo(EventType.INJURY);
            assertThat(beforeTheSuspension.side()).isEqualTo(suspension.responsibleSide());
            assertThat(playersOnThePitch(events, suspension.responsibleSide(), home, away)).hasSize(6);
            assertThat(events).extracting(MatchEvent::type)
                    .doesNotContain(EventType.MATCH_END, EventType.RED_CARD);
            suspended++;
        }
        assertThat(suspended).isPositive();
    }

    @Test
    void matchesWithInjuriesStayReproducibleForTheSameSeed() {
        EngineConfig config = withInjuries(FREQUENT_INJURIES, MAXIMUM_SUBSTITUTIONS);

        List<MatchEvent> first = play(config, home, away, 11L);
        List<MatchEvent> second = play(config, home, away, 11L);

        assertThat(first).containsExactlyElementsOf(second);
    }

    private List<Occurrence> starterGoalkeeperInjuries(EngineConfig config, Team homeTeam) {
        Player starterGoalkeeper = goalkeeperOf(homeTeam.starters());
        List<List<MatchEvent>> matches = playMany(config, homeTeam, away, MATCHES * 5);
        return occurrencesIn(matches).stream()
                .filter(occurrence -> occurrence.side() == MatchSide.HOME)
                .filter(occurrence -> occurrence.injured().equals(starterGoalkeeper))
                .toList();
    }

    private boolean specialistWasInjuredBefore(Occurrence occurrence, Player specialist) {
        return occurrence.events().subList(0, occurrence.index()).stream()
                .anyMatch(event -> isInjury(event) && event.participants().primary().filter(specialist::equals)
                        .isPresent());
    }

    private List<Player> goalkeepersWhoSavedAfter(Occurrence occurrence) {
        List<Player> goalkeepers = new ArrayList<>();
        List<MatchEvent> events = occurrence.events();
        for (int index = occurrence.index() + 1; index < events.size(); index++) {
            MatchEvent event = events.get(index);
            if (event.type() == EventType.INJURY && event.side() == occurrence.side()) {
                break;
            }
            boolean savedAgainstTheInjuredSide = event.type() == EventType.SHOT_SAVED
                    && event.side() == occurrence.side().opposite();
            if (savedAgainstTheInjuredSide) {
                goalkeepers.add(event.participants().secondary().orElseThrow());
            }
        }
        return goalkeepers;
    }

    private List<Occurrence> occurrencesIn(List<List<MatchEvent>> matches) {
        List<Occurrence> occurrences = new ArrayList<>();
        for (List<MatchEvent> events : matches) {
            Map<MatchSide, Integer> substitutions = new EnumMap<>(MatchSide.class);
            for (MatchSide side : MatchSide.values()) {
                substitutions.put(side, 0);
            }
            for (int index = 0; index < events.size(); index++) {
                MatchEvent event = events.get(index);
                if (event.type() == EventType.SUBSTITUTION) {
                    substitutions.merge(event.side(), 1, Integer::sum);
                }
                if (isInjury(event) && !isLast(events, index)) {
                    occurrences.add(new Occurrence(events, index, substitutions.get(event.side())));
                }
            }
        }
        return occurrences;
    }

    private Set<Player> benchPlayersUsedBefore(Occurrence occurrence, Team team) {
        Set<Player> used = new LinkedHashSet<>();
        for (MatchEvent event : occurrence.events().subList(0, occurrence.index())) {
            boolean ofThisSide = event.side() == occurrence.side();
            if (event.type() == EventType.SUBSTITUTION && ofThisSide) {
                used.add(event.participants().secondary().orElseThrow());
            }
        }
        return used;
    }

    private List<Player> playersOnThePitch(List<MatchEvent> events, MatchSide side, Team homeTeam, Team awayTeam) {
        Team team = side == MatchSide.HOME ? homeTeam : awayTeam;
        List<Player> onThePitch = new ArrayList<>(team.starters());
        for (int index = 0; index < events.size(); index++) {
            MatchEvent event = events.get(index);
            if (!isInjury(event) || event.side() != side) {
                continue;
            }
            Player injured = event.participants().primary().orElseThrow();
            boolean substituted = !isLast(events, index) && isSubstitutionOf(events.get(index + 1), injured, side);
            if (substituted) {
                Player entering = events.get(index + 1).participants().secondary().orElseThrow();
                onThePitch.set(onThePitch.indexOf(injured), entering);
            } else {
                onThePitch.remove(injured);
            }
        }
        return onThePitch;
    }

    private boolean isSubstitutionOf(MatchEvent event, Occurrence occurrence) {
        return isSubstitutionOf(event, occurrence.injured(), occurrence.side());
    }

    private boolean isSubstitutionOf(MatchEvent event, Player injured, MatchSide side) {
        return event.type() == EventType.SUBSTITUTION && event.side() == side
                && event.participants().primary().filter(injured::equals).isPresent();
    }

    private int substitutionsOf(List<MatchEvent> events, MatchSide side) {
        return (int) events.stream()
                .filter(event -> event.type() == EventType.SUBSTITUTION && event.side() == side)
                .count();
    }

    private boolean involves(MatchEvent event, Player player) {
        return event.participants().primary().filter(player::equals).isPresent()
                || event.participants().secondary().filter(player::equals).isPresent();
    }

    private boolean isInjury(MatchEvent event) {
        return event.type() == EventType.INJURY;
    }

    private boolean isLast(List<MatchEvent> events, int index) {
        return index == events.size() - 1;
    }

    private EngineConfig withInjuries(double probability, int maximumSubstitutions) {
        EngineConfig base = TestConfigs.withoutFouls(EngineConfig.defaults());
        InjuryConfig injury = new InjuryConfig(probability, base.injury().duration());
        LineupConfig lineup = new LineupConfig(maximumSubstitutions, base.lineup().inferiorityMultiplier(),
                base.lineup().substitutionDuration());
        return TestConfigs.withLineup(TestConfigs.withInjury(base, injury), lineup);
    }

    private List<List<MatchEvent>> playMany(EngineConfig config, Team homeTeam, Team awayTeam, int count) {
        List<List<MatchEvent>> matches = new ArrayList<>();
        for (long seed = 1; seed <= count; seed++) {
            matches.add(play(config, homeTeam, awayTeam, seed));
        }
        return matches;
    }

    private List<MatchEvent> play(EngineConfig config, Team homeTeam, Team awayTeam, long seed) {
        Match match = new MatchEngine(config).start(homeTeam, awayTeam, seed);
        while (!match.isFinished()) {
            match.step();
        }
        return match.events();
    }

    private Team benchWithTwoGoalkeepers(String name) {
        PlayerAttributes attributes = TestTeams.uniform(STARTER_RATING);
        List<Player> starters = TestTeams.squad(name, "S", attributes, 1, 4, 4, 2);
        List<Player> bench = TestTeams.squad(name, "B", attributes, 2, 2, 2, 2);
        return new Team(name, PlayStyle.BALANCED, starters, bench);
    }

    private Team teamWithSpecialistAndBench(String name, boolean reserveGoalkeeper) {
        PlayerAttributes attributes = TestTeams.uniform(STARTER_RATING);
        List<Player> starters = new ArrayList<>(TestTeams.squad(name, "S", attributes, 1, 4, 4, 2));
        Player midfielder = playerNamed(starters, name + "-S-MIDFIELDER-1");
        Player specialist = TestTeams.player(midfielder.name(), Role.MIDFIELDER,
                new PlayerAttributes(STARTER_RATING, STARTER_RATING, STARTER_RATING, STARTER_RATING, STARTER_RATING,
                        STARTER_RATING, STARTER_RATING, 95));
        starters.set(starters.indexOf(midfielder), specialist);
        int reserves = reserveGoalkeeper ? 1 : 0;
        List<Player> bench = new ArrayList<>(TestTeams.squad(name, "B", attributes, reserves, 2, 2, 2));
        Player bestOutfielder = playerNamed(bench, name + "-B-FORWARD-1");
        Player strongerForward = TestTeams.player(bestOutfielder.name(), Role.FORWARD,
                TestTeams.uniform(STARTER_RATING + 10));
        bench.set(bench.indexOf(bestOutfielder), strongerForward);
        return new Team(name, PlayStyle.BALANCED, starters, bench);
    }

    private Player specialistOf(Team team) {
        return playerNamed(team.starters(), team.name() + "-S-MIDFIELDER-1");
    }

    private Player goalkeeperOf(List<Player> players) {
        return players.stream().filter(player -> player.role() == Role.GOALKEEPER).findFirst().orElseThrow();
    }

    private Player playerNamed(List<Player> players, String name) {
        return players.stream().filter(player -> player.name().equals(name)).findFirst().orElseThrow();
    }

    private List<Player> squadOf(Team team) {
        List<Player> squad = new ArrayList<>(team.starters());
        squad.addAll(team.bench());
        return squad;
    }
}
