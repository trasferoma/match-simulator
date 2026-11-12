package org.tasktelemetry.matchsimulator.engine.resolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.TestConfigs;
import org.tasktelemetry.matchsimulator.engine.TestResolvers;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.LineupConfig;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class MatchupsTest {

    private static final double EXACT = 1e-9;
    private static final double ATTACK = 60.0;
    private static final double DEFENCE = 40.0;
    private static final double PACE = 50.0;
    private static final PlayerAttributes DUEL_ATTRIBUTES = TestTeams.withDuelAttributes(60, 55, 45, 40);
    private static final double ATTACKERS_ZONE_AVERAGE = (1.2 * 40 + 4.0 * 60 + 0.8 * 80) / 6.0;
    private static final double ATTACKERS_REPRESENTATIVE = 80.0;
    private static final double DEFENDERS_ZONE_AVERAGE = (1.2 * 30 + 4.0 * 50 + 0.8 * 70) / 6.0;
    private static final double DEFENDERS_REPRESENTATIVE = 30.0;

    private final PitchLayout layout = PitchLayout.standard();
    private final EngineConfig config = EngineConfig.defaults();

    static Stream<Arguments> sectorsWithPresenceInTheZone() {
        return sectorsAndZonesWhere(presence -> presence > 0.0);
    }

    static Stream<Arguments> sectorsWithoutPresenceInTheZone() {
        return sectorsAndZonesWhere(presence -> presence == 0.0);
    }

    private static Stream<Arguments> sectorsAndZonesWhere(Predicate<Double> presenceFilter) {
        Map<ZoneRole, Map<Role, Double>> table = EngineConfig.defaults().zonePresence().presenceByZoneRole();
        return table.entrySet().stream()
                .flatMap(zone -> zone.getValue().entrySet().stream()
                        .filter(sector -> presenceFilter.test(sector.getValue()))
                        .map(sector -> Arguments.of(sector.getKey(), zone.getKey())));
    }

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void theHomeAdvantageMultipliesTheValueOfTheHomeTeamWhetherItAttacksOrDefends(MatchSide possession) {
        Matchups matchups = matchupsFor(config);
        MatchState state = openPlayAt(possession, ZoneRole.MIDFIELD);
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);
        double expectedAttack = possession == MatchSide.HOME ? ATTACK * config.homeAdvantage() : ATTACK;
        double expectedDefence = possession == MatchSide.AWAY ? DEFENCE * config.homeAdvantage() : DEFENCE;

        Duel duel = passingDuel(matchups, state, balanced, balanced);

        assertDuel(duel, expectedAttack, expectedDefence);
    }

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void theHomeAdvantageOfTheCounterAttackDuelFollowsTheSideOfTheHomeTeam(MatchSide possession) {
        Matchups matchups = matchupsFor(config);
        MatchState state = openPlayAt(possession, ZoneRole.MIDFIELD);
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);
        double expectedAttack = possession == MatchSide.HOME ? PACE * config.homeAdvantage() : PACE;
        double expectedDefence = possession == MatchSide.AWAY ? PACE * config.homeAdvantage() : PACE;

        Duel duel = counterAttackDuel(matchups, state, balanced, balanced);

        assertDuel(duel, expectedAttack, expectedDefence);
    }

    @Test
    void theHomeAdvantageIsTheConfiguredOne() {
        EngineConfig stronger = TestConfigs.withHomeAdvantage(config, 1.25);
        Matchups matchups = matchupsFor(stronger);
        MatchState state = openPlayAt(MatchSide.HOME, ZoneRole.MIDFIELD);
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);

        Duel duel = passingDuel(matchups, state, balanced, balanced);

        assertThat(duel.attack()).isCloseTo(ATTACK * 1.25, within(EXACT));
    }

    @Test
    void theBalancedStyleWithoutHomeAdvantageLeavesEveryDuelUntouchedInEveryZone() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);

        for (MatchSide possession : MatchSide.values()) {
            for (ZoneRole zoneRole : ZoneRole.values()) {
                MatchState state = openPlayAt(possession, zoneRole);

                assertDuel(passingDuel(matchups, state, balanced, balanced), ATTACK, DEFENCE);
                assertDuel(counterAttackDuel(matchups, state, balanced, balanced), PACE, PACE);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
            "ATTACKING, ATTACKING_THIRD, 1.10",
            "ATTACKING, OPPONENT_BOX, 1.10",
            "ATTACKING, OWN_BOX, 1.10",
            "ATTACKING, MIDFIELD, 1.00",
            "DEFENSIVE, ATTACKING_THIRD, 0.90",
            "DEFENSIVE, MIDFIELD, 1.00",
            "POSSESSION, MIDFIELD, 1.10",
            "POSSESSION, ATTACKING_THIRD, 1.00",
            "COUNTER_ATTACK, MIDFIELD, 0.92",
            "COUNTER_ATTACK, ATTACKING_THIRD, 1.00",
            "BALANCED, MIDFIELD, 1.00"})
    void theStyleOfTheAttackersMultipliesOnlyTheAttackValueInTheDuelOfTheirLine(PlayStyle style, ZoneRole zoneRole,
            double multiplier) {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, zoneRole);
        TeamOnPitch attackers = teamWithStyle(style);
        TeamOnPitch defenders = teamWithStyle(PlayStyle.BALANCED);

        Duel duel = passingDuel(matchups, state, attackers, defenders);

        assertDuel(duel, ATTACK * multiplier, DEFENCE);
    }

    @ParameterizedTest
    @CsvSource({
            "ATTACKING, ATTACKING_THIRD, 0.92",
            "ATTACKING, MIDFIELD, 1.00",
            "DEFENSIVE, ATTACKING_THIRD, 1.10",
            "DEFENSIVE, OPPONENT_BOX, 1.10",
            "DEFENSIVE, MIDFIELD, 1.00",
            "POSSESSION, MIDFIELD, 1.10",
            "POSSESSION, ATTACKING_THIRD, 1.00",
            "COUNTER_ATTACK, MIDFIELD, 0.92",
            "COUNTER_ATTACK, ATTACKING_THIRD, 1.00",
            "BALANCED, MIDFIELD, 1.00"})
    void theStyleOfTheDefendersMultipliesOnlyTheDefenceValueInTheDuelOfTheirLine(PlayStyle style,
            ZoneRole zoneRole, double multiplier) {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, zoneRole);
        TeamOnPitch attackers = teamWithStyle(PlayStyle.BALANCED);
        TeamOnPitch defenders = teamWithStyle(style);

        Duel duel = passingDuel(matchups, state, attackers, defenders);

        assertDuel(duel, ATTACK, DEFENCE * multiplier);
    }

    @ParameterizedTest
    @EnumSource(PlayStyle.class)
    void onlyTheCounterAttackStyleChangesTheAdvanceOfTheCounterAttackAndOnlyForTheAttackers(PlayStyle style) {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        double multiplier = style == PlayStyle.COUNTER_ATTACK ? 1.10 : 1.00;
        TeamOnPitch styledAttackers = teamWithStyle(style);
        TeamOnPitch balancedDefenders = teamWithStyle(PlayStyle.BALANCED);

        Duel asAttackers = counterAttackDuel(matchups, state, styledAttackers, balancedDefenders);

        assertDuel(asAttackers, PACE * multiplier, PACE);
    }

    @Test
    void theAttackerAndTheDefenderAreFieldPlayersOfTheirOwnTeams() {
        Matchups matchups = matchupsFor(config);
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.ATTACKING_THIRD);
        TeamOnPitch attackers = teamWithStyle(PlayStyle.BALANCED);
        TeamOnPitch defenders = teamWithStyle(PlayStyle.BALANCED);

        Player attacker = matchups.pickAttacker(state, attackers);
        Player defender = matchups.pickDefender(state, defenders);

        assertThat(attackers.players()).contains(attacker);
        assertThat(attackers.roleOf(attacker)).isNotEqualTo(Role.GOALKEEPER);
        assertThat(defenders.players()).contains(defender);
        assertThat(defenders.roleOf(defender)).isNotEqualTo(Role.GOALKEEPER);
    }

    @Test
    void aStateInCounterAttackIsReadByTheSameZoneRoleAsOpenPlay() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState counterAttack = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD).inCounterAttack();
        TeamOnPitch possession = teamWithStyle(PlayStyle.POSSESSION);
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);

        Duel duel = passingDuel(matchups, counterAttack, possession, balanced);

        assertThat(counterAttack.phase()).isEqualTo(PlayPhase.COUNTER_ATTACK);
        assertThat(duel.attack()).isCloseTo(ATTACK * 1.10, within(EXACT));
    }

    @ParameterizedTest
    @EnumSource(value = DuelAttributes.class, names = {"PASSING", "DRIBBLING", "CROSS", "AERIAL"})
    void withARepresentativeWeightOfZeroTheStrengthOfEachSideIsTheAverageOfItsZone(DuelAttributes attributes) {
        Matchups matchups = matchupsFor(withRepresentativeWeight(0.0));

        Duel duel = zoneDuelBetweenTeamsOfDifferentLines(matchups, attributes);

        assertDuel(duel, ATTACKERS_ZONE_AVERAGE, DEFENDERS_ZONE_AVERAGE);
    }

    @ParameterizedTest
    @EnumSource(value = DuelAttributes.class, names = {"PASSING", "DRIBBLING", "CROSS", "AERIAL"})
    void withARepresentativeWeightOfOneTheStrengthOfEachSideIsTheValueOfItsRepresentative(
            DuelAttributes attributes) {
        Matchups matchups = matchupsFor(withRepresentativeWeight(1.0));

        Duel duel = zoneDuelBetweenTeamsOfDifferentLines(matchups, attributes);

        assertDuel(duel, ATTACKERS_REPRESENTATIVE, DEFENDERS_REPRESENTATIVE);
    }

    @ParameterizedTest
    @EnumSource(value = DuelAttributes.class, names = {"PASSING", "DRIBBLING", "CROSS", "AERIAL"})
    void withTheDefaultWeightTheStrengthOfEachSideIsTheGeometricMeanOfAverageAndRepresentative(
            DuelAttributes attributes) {
        Matchups matchups = matchupsFor(withRepresentativeWeight(config.zonePresence().representativeWeight()));
        double weight = config.zonePresence().representativeWeight();
        double expectedAttack = StrictMath.pow(ATTACKERS_ZONE_AVERAGE, 1.0 - weight)
                * StrictMath.pow(ATTACKERS_REPRESENTATIVE, weight);
        double expectedDefence = StrictMath.pow(DEFENDERS_ZONE_AVERAGE, 1.0 - weight)
                * StrictMath.pow(DEFENDERS_REPRESENTATIVE, weight);

        Duel duel = zoneDuelBetweenTeamsOfDifferentLines(matchups, attributes);

        assertDuel(duel, expectedAttack, expectedDefence);
    }

    @Test
    void theAttackerReadsTheAttackAttributeOfTheDuelAndTheDefenderTheDefenceOne() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        TeamOnPitch attackers = teamWithStyle(PlayStyle.BALANCED);
        TeamOnPitch defenders = teamWithStyle(PlayStyle.BALANCED);
        Player attacker = fieldPlayerOf(attackers);
        Player defender = fieldPlayerOf(defenders);

        Duel passing = matchups.openPlayDuel(DuelAttributes.PASSING, state, attackers, attacker, defenders, defender);
        Duel dribbling = matchups.openPlayDuel(DuelAttributes.DRIBBLING, state, attackers, attacker, defenders,
                defender);

        assertDuel(passing, 60.0, 40.0);
        assertDuel(dribbling, 55.0, 45.0);
    }

    @Test
    void aMidfielderLessInMidfieldDividesTheAttackOfTheTeamByTheFactorOfOneMan() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        TeamOnPitch complete = teamWithStyle(PlayStyle.BALANCED);
        TeamOnPitch oneMidfielderLess = withoutPlayersOf(complete, Role.MIDFIELDER, 1);

        Duel duel = passingDuel(matchups, state, oneMidfielderLess, complete);

        assertDuel(duel, ATTACK / config.zonePresence().advantageFactorPerMan(), DEFENCE);
    }

    @Test
    void aMidfielderLessInTheMidfieldOfTheDefendersMakesTheAttackersStrongerByTheSameFactor() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        TeamOnPitch complete = teamWithStyle(PlayStyle.BALANCED);
        TeamOnPitch oneMidfielderLess = withoutPlayersOf(complete, Role.MIDFIELDER, 1);

        Duel duel = passingDuel(matchups, state, complete, oneMidfielderLess);

        assertDuel(duel, ATTACK * config.zonePresence().advantageFactorPerMan(), DEFENCE);
    }

    @ParameterizedTest
    @MethodSource("sectorsWithPresenceInTheZone")
    void aTeamWithAManLessHasALowerProbabilityInEveryZoneWhereTheSectorOfTheMissingManHasPresence(Role sector,
            ZoneRole zoneRole) {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        TeamOnPitch complete = teamWithStyle(PlayStyle.BALANCED);
        TeamOnPitch oneManLess = withoutPlayersOf(complete, sector, 1);
        MatchState whereTheTeamAttacks = openPlayAt(MatchSide.AWAY, zoneRole);
        MatchState whereTheTeamDefends = openPlayWhereTheDefendersAreAt(zoneRole);

        double evenAttack = passingDuel(matchups, whereTheTeamAttacks, complete, complete).attackerWinProbability();
        double attackingWithOneLess = passingDuel(matchups, whereTheTeamAttacks, oneManLess, complete)
                .attackerWinProbability();
        double defendingWithOneLess = passingDuel(matchups, whereTheTeamDefends, complete, oneManLess)
                .attackerWinProbability();

        assertThat(attackingWithOneLess).isLessThan(evenAttack);
        assertThat(defendingWithOneLess).isGreaterThan(evenAttack);
    }

    @ParameterizedTest
    @MethodSource("sectorsWithoutPresenceInTheZone")
    void aManMissingInASectorWithoutPresenceInTheZoneDoesNotChangeTheDuel(Role sector, ZoneRole zoneRole) {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        TeamOnPitch complete = teamWithStyle(PlayStyle.BALANCED);
        TeamOnPitch oneManLess = withoutPlayersOf(complete, sector, 1);
        MatchState whereTheTeamAttacks = openPlayAt(MatchSide.AWAY, zoneRole);
        MatchState whereTheTeamDefends = openPlayWhereTheDefendersAreAt(zoneRole);

        Duel attackingWithOneLess = passingDuel(matchups, whereTheTeamAttacks, oneManLess, complete);
        Duel defendingWithOneLess = passingDuel(matchups, whereTheTeamDefends, complete, oneManLess);

        assertDuel(attackingWithOneLess, ATTACK, DEFENCE);
        assertDuel(defendingWithOneLess, ATTACK, DEFENCE);
    }

    @Test
    void aTeamStillAtElevenMenWithItsLinesReshuffledMovesMenBetweenTheZones() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);
        Player carrier = balanced.playersIn(Role.FORWARD).getFirst();
        Player movedToDefence = balanced.playersIn(Role.MIDFIELDER).getFirst();
        TeamOnPitch reshuffled = balanced.withRole(movedToDefence, Role.DEFENDER);
        double menInMidfield = 5 * 0.3 + 3 * 1.0 + 2 * 0.4;
        double advantage = menInMidfield - 6.0;
        double superiority = StrictMath.pow(config.zonePresence().advantageFactorPerMan(), advantage);

        Duel duel = matchups.openPlayDuel(DuelAttributes.PASSING, state, reshuffled, carrier, balanced,
                fieldPlayerOf(balanced));

        assertDuel(duel, ATTACK * superiority, DEFENCE);
    }

    @Test
    void aPlayerOutOfPositionWeakensHisSideOfTheOpenPlayDuelByTheOutOfPositionMultiplier() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        TeamOnPitch attackers = teamWithStyle(PlayStyle.BALANCED);
        TeamOnPitch defenders = teamWithStyle(PlayStyle.BALANCED);
        Player attacker = attackers.playersIn(Role.MIDFIELDER).getFirst();
        Player defender = defenders.playersIn(Role.MIDFIELDER).getFirst();
        Player exchangedDefender = attackers.playersIn(Role.DEFENDER).getFirst();
        Player exchangedForward = defenders.playersIn(Role.FORWARD).getFirst();
        TeamOnPitch attackersMisplaced = exchangeRoles(attackers, attacker, exchangedDefender);
        TeamOnPitch defendersMisplaced = exchangeRoles(defenders, defender, exchangedForward);
        double multiplier = config.lineup().outOfPositionMultiplier();

        Duel duel = matchups.openPlayDuel(DuelAttributes.PASSING, state, attackersMisplaced, attacker,
                defendersMisplaced, defender);

        assertDuel(duel, ATTACK * multiplier, DEFENCE * multiplier);
    }

    @Test
    void aPlayerOutOfPositionWeakensHisSideOfTheCounterAttackDuelByTheOutOfPositionMultiplier() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD).inCounterAttack();
        TeamOnPitch attackers = teamWithStyle(PlayStyle.BALANCED);
        TeamOnPitch defenders = teamWithStyle(PlayStyle.BALANCED);
        Player runner = attackers.playersIn(Role.MIDFIELDER).getFirst();
        Player chaser = defenders.playersIn(Role.MIDFIELDER).getFirst();
        TeamOnPitch attackersMisplaced = attackers.withRole(runner, Role.DEFENDER);
        TeamOnPitch defendersMisplaced = defenders.withRole(chaser, Role.FORWARD);
        double multiplier = config.lineup().outOfPositionMultiplier();

        Duel duel = matchups.counterAttackDuel(state, attackersMisplaced, runner, defendersMisplaced, chaser);

        assertDuel(duel, PACE * multiplier, PACE * multiplier);
    }

    @Test
    void aPlayerOutOfPositionWhoIsNotInTheDuelDoesNotWeakenIt() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);
        Player misplaced = balanced.playersIn(Role.MIDFIELDER).getFirst();
        Player carrier = balanced.playersIn(Role.MIDFIELDER).getLast();
        Player exchanged = balanced.playersIn(Role.DEFENDER).getFirst();
        TeamOnPitch withOneMisplaced = exchangeRoles(balanced, misplaced, exchanged);

        Duel duel = matchups.openPlayDuel(DuelAttributes.PASSING, state, withOneMisplaced, carrier, balanced,
                fieldPlayerOf(balanced));

        assertDuel(duel, ATTACK, DEFENCE);
    }

    @Test
    void anOutOfPositionMultiplierOfOneIsNeutral() {
        EngineConfig neutral = TestConfigs.withLineup(TestConfigs.withoutHomeAdvantage(config),
                new LineupConfig(config.lineup().maximumSubstitutions(), 1.0,
                        config.lineup().substitutionDuration()));
        Matchups matchups = matchupsFor(neutral);
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);
        Player misplaced = balanced.playersIn(Role.MIDFIELDER).getFirst();
        Player exchanged = balanced.playersIn(Role.DEFENDER).getFirst();
        TeamOnPitch withOneMisplaced = exchangeRoles(balanced, misplaced, exchanged);

        Duel duel = matchups.openPlayDuel(DuelAttributes.PASSING, state, withOneMisplaced, misplaced,
                withOneMisplaced, misplaced);

        assertDuel(duel, ATTACK, DEFENCE);
    }

    @Test
    void theCounterAttackRunIsOneAgainstOneOnPaceWithoutAverageNorSuperiority() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.OPPONENT_BOX);
        TeamOnPitch fastAndWide = teamOf("Fast", PlayStyle.BALANCED, paceAttributes(80), 4, 2, 4);
        TeamOnPitch slowAndDeep = teamOf("Slow", PlayStyle.BALANCED, paceAttributes(30), 1, 5, 4);

        Duel duel = counterAttackDuel(matchups, state, fastAndWide, slowAndDeep);

        assertDuel(duel, 80.0, 30.0);
    }

    @Test
    void fourAgainstOneInTheBoxGivesTheAttackersTheCappedSuperiority() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.OPPONENT_BOX);
        PlayerAttributes sameEverywhere = TestTeams.uniform(60);
        TeamOnPitch wide = teamOf("Wide", PlayStyle.BALANCED, sameEverywhere, 4, 2, 4);
        TeamOnPitch deep = teamOf("Deep", PlayStyle.BALANCED, sameEverywhere, 1, 5, 4);

        Duel duel = passingDuel(matchups, state, wide, deep);

        assertThat(layout.roleOf(state.zone(), MatchSide.HOME)).isEqualTo(ZoneRole.OWN_BOX);
        assertThat(duel.attackerWinProbability()).isCloseTo(2.44140625 / 3.44140625, within(EXACT));
    }

    @Test
    void theBalancedStyleWithoutHomeAdvantageLeavesTheShotUntouched() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);

        for (MatchSide possession : MatchSide.values()) {
            for (ZoneRole zoneRole : ZoneRole.values()) {
                MatchState state = openPlayAt(possession, zoneRole);

                assertThat(matchups.shootingMultiplier(state, balanced, fieldPlayerOf(balanced))).isEqualTo(1.0);
                assertThat(matchups.goalkeepingMultiplier(state, balanced)).isEqualTo(1.0);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
            "ATTACKING, OPPONENT_BOX, 1.10",
            "ATTACKING, MIDFIELD, 1.00",
            "DEFENSIVE, ATTACKING_THIRD, 0.90",
            "POSSESSION, MIDFIELD, 1.10",
            "COUNTER_ATTACK, MIDFIELD, 0.92",
            "COUNTER_ATTACK, OPPONENT_BOX, 1.00"})
    void theStyleOfTheAttackersMultipliesTheShootingOfTheShooterWithTheValueOfTheirLine(PlayStyle style,
            ZoneRole zoneRole, double multiplier) {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, zoneRole);
        TeamOnPitch attackers = teamWithStyle(style);

        double shooting = matchups.shootingMultiplier(state, attackers, attackers.playersIn(Role.FORWARD).getFirst());

        assertThat(shooting).isCloseTo(multiplier, within(EXACT));
    }

    @ParameterizedTest
    @CsvSource({
            "DEFENSIVE, OPPONENT_BOX, 1.10",
            "DEFENSIVE, MIDFIELD, 1.00",
            "ATTACKING, ATTACKING_THIRD, 0.92",
            "POSSESSION, MIDFIELD, 1.10",
            "COUNTER_ATTACK, MIDFIELD, 0.92",
            "BALANCED, OPPONENT_BOX, 1.00"})
    void theStyleOfTheDefendersMultipliesTheGoalkeepingWithTheValueOfTheirLine(PlayStyle style, ZoneRole zoneRole,
            double multiplier) {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, zoneRole);
        TeamOnPitch defenders = teamWithStyle(style);

        double goalkeeping = matchups.goalkeepingMultiplier(state, defenders);

        assertThat(goalkeeping).isCloseTo(multiplier, within(EXACT));
    }

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void theHomeAdvantageMultipliesTheShootingOfTheHomeAttackersAndTheGoalkeepingOfTheHomeDefenders(
            MatchSide possession) {
        Matchups matchups = matchupsFor(config);
        MatchState state = openPlayAt(possession, ZoneRole.OPPONENT_BOX);
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);
        double homeAttacks = possession == MatchSide.HOME ? config.homeAdvantage() : 1.0;
        double homeDefends = possession == MatchSide.AWAY ? config.homeAdvantage() : 1.0;

        double shooting = matchups.shootingMultiplier(state, balanced, fieldPlayerOf(balanced));
        double goalkeeping = matchups.goalkeepingMultiplier(state, balanced);

        assertThat(shooting).isCloseTo(homeAttacks, within(EXACT));
        assertThat(goalkeeping).isCloseTo(homeDefends, within(EXACT));
    }

    @Test
    void theShootingMultiplierCombinesStyleVenueAndPositionOfTheShooterWithoutTheMenOfTheTeam() {
        Matchups matchups = matchupsFor(config);
        MatchState state = openPlayAt(MatchSide.HOME, ZoneRole.OPPONENT_BOX);
        TeamOnPitch attacking = teamWithStyle(PlayStyle.ATTACKING);
        Player lastForward = attacking.playersIn(Role.FORWARD).getLast();
        Player misplaced = attacking.playersIn(Role.FORWARD).getFirst();
        TeamOnPitch oneForwardLessAndOneMisplaced = attacking.withoutPlayer(lastForward)
                .withRole(misplaced, Role.MIDFIELDER);
        double style = config.playStyles().effectOf(PlayStyle.ATTACKING).attack();
        double position = config.lineup().outOfPositionMultiplier();

        double shooting = matchups.shootingMultiplier(state, oneForwardLessAndOneMisplaced, misplaced);

        assertThat(shooting).isCloseTo(style * config.homeAdvantage() * position, within(EXACT));
    }

    @Test
    void theShootingAndGoalkeepingMultipliersIgnoreTheNumericalSuperiorityOfTheTeams() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.OPPONENT_BOX);
        PlayerAttributes sameEverywhere = TestTeams.uniform(60);
        TeamOnPitch wide = teamOf("Wide", PlayStyle.BALANCED, sameEverywhere, 4, 2, 4);
        TeamOnPitch deep = teamOf("Deep", PlayStyle.BALANCED, sameEverywhere, 1, 5, 4);

        double shooting = matchups.shootingMultiplier(state, wide, wide.playersIn(Role.FORWARD).getFirst());
        double goalkeeping = matchups.goalkeepingMultiplier(state, deep);

        assertThat(shooting).isEqualTo(1.0);
        assertThat(goalkeeping).isEqualTo(1.0);
    }

    @Test
    void theGoalkeepingMultiplierDoesNotWeakenWithTheMenMissingInTheLineThatDefendsTheBox() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.OPPONENT_BOX);
        TeamOnPitch defenders = withoutPlayersOf(teamWithStyle(PlayStyle.DEFENSIVE), Role.DEFENDER, 2);
        double style = config.playStyles().effectOf(PlayStyle.DEFENSIVE).defence();

        double goalkeeping = matchups.goalkeepingMultiplier(state, defenders);

        assertThat(goalkeeping).isCloseTo(style, within(EXACT));
    }

    private Duel zoneDuelBetweenTeamsOfDifferentLines(Matchups matchups, DuelAttributes attributes) {
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        TeamOnPitch attackers = teamByLine("Attackers", 40, 60, 80);
        TeamOnPitch defenders = teamByLine("Defenders", 30, 50, 70);
        Player attacker = attackers.playersIn(Role.FORWARD).getFirst();
        Player defender = defenders.playersIn(Role.DEFENDER).getFirst();
        return matchups.openPlayDuel(attributes, state, attackers, attacker, defenders, defender);
    }

    private EngineConfig withRepresentativeWeight(double weight) {
        EngineConfig noVenue = TestConfigs.withoutHomeAdvantage(config);
        return TestConfigs.withRepresentativeWeight(noVenue, weight);
    }

    private Duel passingDuel(Matchups matchups, MatchState state, TeamOnPitch attackers, TeamOnPitch defenders) {
        Player attacker = fieldPlayerOf(attackers);
        Player defender = fieldPlayerOf(defenders);
        return matchups.openPlayDuel(DuelAttributes.PASSING, state, attackers, attacker, defenders, defender);
    }

    private Duel counterAttackDuel(Matchups matchups, MatchState state, TeamOnPitch attackers,
            TeamOnPitch defenders) {
        Player attacker = fieldPlayerOf(attackers);
        Player defender = fieldPlayerOf(defenders);
        return matchups.counterAttackDuel(state, attackers, attacker, defenders, defender);
    }

    private void assertDuel(Duel duel, double attack, double defence) {
        assertThat(duel.attack()).isCloseTo(attack, within(EXACT));
        assertThat(duel.defence()).isCloseTo(defence, within(EXACT));
    }

    private Player fieldPlayerOf(TeamOnPitch team) {
        return team.players().stream()
                .filter(player -> team.roleOf(player) != Role.GOALKEEPER)
                .findFirst()
                .orElseThrow();
    }

    private TeamOnPitch exchangeRoles(TeamOnPitch team, Player first, Player second) {
        Role firstRole = team.roleOf(first);
        Role secondRole = team.roleOf(second);
        return team.withRole(first, secondRole).withRole(second, firstRole);
    }

    private TeamOnPitch withoutPlayersOf(TeamOnPitch team, Role role, int count) {
        TeamOnPitch reduced = team;
        for (Player player : team.playersIn(role).stream().limit(count).toList()) {
            reduced = reduced.withoutPlayer(player);
        }
        return reduced;
    }

    private Matchups matchupsFor(EngineConfig engineConfig) {
        return TestResolvers.matchups(engineConfig, layout, new MatchRandom(1L));
    }

    private TeamOnPitch teamWithStyle(PlayStyle style) {
        return teamOf("T-" + style, style, DUEL_ATTRIBUTES, 4, 4, 2);
    }

    private TeamOnPitch teamOf(String name, PlayStyle style, PlayerAttributes attributes, int defenders,
            int midfielders, int forwards) {
        List<Player> starters = TestTeams.squad(name, "S", attributes, 1, defenders, midfielders, forwards);
        return TeamOnPitch.startingWith(new Team(name, style, starters, List.of()));
    }

    private TeamOnPitch teamByLine(String name, int defendersRating, int midfieldersRating, int forwardsRating) {
        List<Player> starters = new ArrayList<>();
        starters.addAll(TestTeams.squad(name, "S", TestTeams.uniform(50), 1, 0, 0, 0));
        starters.addAll(TestTeams.squad(name, "S", TestTeams.uniform(defendersRating), 0, 4, 0, 0));
        starters.addAll(TestTeams.squad(name, "S", TestTeams.uniform(midfieldersRating), 0, 0, 4, 0));
        starters.addAll(TestTeams.squad(name, "S", TestTeams.uniform(forwardsRating), 0, 0, 0, 2));
        return TeamOnPitch.startingWith(new Team(name, PlayStyle.BALANCED, starters, List.of()));
    }

    private PlayerAttributes paceAttributes(int pace) {
        return new PlayerAttributes(50, 50, 50, 50, 50, pace, 50, 50);
    }

    private MatchState openPlayAt(MatchSide possession, ZoneRole zoneRole) {
        Zone zone = layout.zones().stream()
                .filter(candidate -> layout.roleOf(candidate, possession) == zoneRole)
                .findFirst()
                .orElseThrow();
        return MatchState.openPlay(possession, zone);
    }

    private MatchState openPlayWhereTheDefendersAreAt(ZoneRole zoneRole) {
        Zone zone = layout.zones().stream()
                .filter(candidate -> layout.roleOf(candidate, MatchSide.HOME) == zoneRole)
                .findFirst()
                .orElseThrow();
        return MatchState.openPlay(MatchSide.AWAY, zone);
    }
}
