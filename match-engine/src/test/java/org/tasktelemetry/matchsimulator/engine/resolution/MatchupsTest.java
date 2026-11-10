package org.tasktelemetry.matchsimulator.engine.resolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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
import org.tasktelemetry.matchsimulator.engine.team.Role;

class MatchupsTest {

    private static final double EXACT = 1e-9;
    private static final double ATTACK = 60.0;
    private static final double DEFENCE = 40.0;

    private final PitchLayout layout = PitchLayout.standard();
    private final EngineConfig config = EngineConfig.defaults();
    private final Duel base = new Duel(ATTACK, DEFENCE);

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void theHomeAdvantageMultipliesTheValueOfTheHomeTeamWhetherItAttacksOrDefends(MatchSide possession) {
        Matchups matchups = matchupsFor(config);
        MatchState state = openPlayAt(possession, ZoneRole.MIDFIELD);
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);
        double expectedAttack = possession == MatchSide.HOME ? ATTACK * config.homeAdvantage() : ATTACK;
        double expectedDefence = possession == MatchSide.AWAY ? DEFENCE * config.homeAdvantage() : DEFENCE;

        Duel duel = matchups.openPlayDuel(base, state, balanced, firstOf(balanced), balanced, firstOf(balanced));

        assertThat(duel.attack()).isCloseTo(expectedAttack, within(EXACT));
        assertThat(duel.defence()).isCloseTo(expectedDefence, within(EXACT));
    }

    @ParameterizedTest
    @EnumSource(MatchSide.class)
    void theHomeAdvantageOfTheCounterAttackDuelFollowsTheSideOfTheHomeTeam(MatchSide possession) {
        Matchups matchups = matchupsFor(config);
        MatchState state = openPlayAt(possession, ZoneRole.MIDFIELD);
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);
        double expectedAttack = possession == MatchSide.HOME ? ATTACK * config.homeAdvantage() : ATTACK;
        double expectedDefence = possession == MatchSide.AWAY ? DEFENCE * config.homeAdvantage() : DEFENCE;

        Duel duel = matchups.counterAttackDuel(base, state, balanced, firstOf(balanced), balanced, firstOf(balanced));

        assertThat(duel.attack()).isCloseTo(expectedAttack, within(EXACT));
        assertThat(duel.defence()).isCloseTo(expectedDefence, within(EXACT));
    }

    @Test
    void theHomeAdvantageIsTheConfiguredOne() {
        EngineConfig stronger = TestConfigs.withHomeAdvantage(config, 1.25);
        Matchups matchups = matchupsFor(stronger);
        MatchState state = openPlayAt(MatchSide.HOME, ZoneRole.MIDFIELD);
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);

        Duel duel = matchups.openPlayDuel(base, state, balanced, firstOf(balanced), balanced, firstOf(balanced));

        assertThat(duel.attack()).isCloseTo(ATTACK * 1.25, within(EXACT));
    }

    @Test
    void theBalancedStyleWithoutHomeAdvantageLeavesEveryDuelUntouched() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);

        for (MatchSide possession : MatchSide.values()) {
            for (ZoneRole zoneRole : ZoneRole.values()) {
                MatchState state = openPlayAt(possession, zoneRole);

                assertThat(matchups.openPlayDuel(base, state, balanced, firstOf(balanced), balanced, firstOf(balanced))).isEqualTo(base);
                assertThat(matchups.counterAttackDuel(base, state, balanced, firstOf(balanced), balanced, firstOf(balanced))).isEqualTo(base);
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

        Duel duel = matchups.openPlayDuel(base, state, attackers, firstOf(attackers), defenders, firstOf(defenders));

        assertThat(duel.attack()).isCloseTo(ATTACK * multiplier, within(EXACT));
        assertThat(duel.defence()).isCloseTo(DEFENCE, within(EXACT));
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

        Duel duel = matchups.openPlayDuel(base, state, attackers, firstOf(attackers), defenders, firstOf(defenders));

        assertThat(duel.attack()).isCloseTo(ATTACK, within(EXACT));
        assertThat(duel.defence()).isCloseTo(DEFENCE * multiplier, within(EXACT));
    }

    @ParameterizedTest
    @EnumSource(PlayStyle.class)
    void onlyTheCounterAttackStyleChangesTheAdvanceOfTheCounterAttackAndOnlyForTheAttackers(PlayStyle style) {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        double multiplier = style == PlayStyle.COUNTER_ATTACK ? 1.10 : 1.00;

        TeamOnPitch styledAttackers = teamWithStyle(style);
        TeamOnPitch balancedDefenders = teamWithStyle(PlayStyle.BALANCED);

        Duel asAttackers = matchups.counterAttackDuel(base, state, styledAttackers, firstOf(styledAttackers),
                balancedDefenders, firstOf(balancedDefenders));

        assertThat(asAttackers.attack()).isCloseTo(ATTACK * multiplier, within(EXACT));
        assertThat(asAttackers.defence()).isCloseTo(DEFENCE, within(EXACT));
    }

    @Test
    void theAttackersAreChosenInTheLineOfTheirZoneAndTheDefendersInTheLineOfTheZoneSeenFromTheirSide() {
        Matchups matchups = matchupsFor(config);
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.ATTACKING_THIRD);
        TeamOnPitch attackers = teamWithStyle(PlayStyle.BALANCED);
        TeamOnPitch defenders = teamWithStyle(PlayStyle.BALANCED);

        Player attacker = matchups.pickAttacker(state, attackers);
        Player defender = matchups.pickDefender(state, defenders);

        assertThat(attacker.role()).isEqualTo(Role.FORWARD);
        assertThat(attackers.players()).contains(attacker);
        assertThat(defender.role()).isEqualTo(Role.DEFENDER);
        assertThat(defenders.players()).contains(defender);
    }

    @Test
    void aStateInCounterAttackIsReadByTheSameZoneRoleAsOpenPlay() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState counterAttack = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD).inCounterAttack();
        TeamOnPitch possession = teamWithStyle(PlayStyle.POSSESSION);
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);

        Duel duel = matchups.openPlayDuel(base, counterAttack, possession, firstOf(possession), balanced, firstOf(balanced));

        assertThat(counterAttack.phase()).isEqualTo(PlayPhase.COUNTER_ATTACK);
        assertThat(duel.attack()).isCloseTo(ATTACK * 1.10, within(EXACT));
    }

    @ParameterizedTest
    @CsvSource({"0, 1.0", "1, 0.9", "2, 0.81"})
    void everyMissingManInTheSectorOfTheDuelWeakensTheTeamByTheInferiorityMultiplier(int menDown,
            double expectedFactor) {
        Matchups matchups = matchupsFor(noVenueEffectWithMultiplier(0.9));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        TeamOnPitch attackers = withoutMidfielders(teamWithStyle(PlayStyle.BALANCED), menDown);
        TeamOnPitch defenders = teamWithStyle(PlayStyle.BALANCED);

        Duel duel = matchups.openPlayDuel(base, state, attackers, firstOf(attackers), defenders, firstOf(defenders));

        assertThat(duel.attack()).isCloseTo(ATTACK * expectedFactor, within(EXACT));
        assertThat(duel.defence()).isCloseTo(DEFENCE, within(EXACT));
    }

    @Test
    void theDefendersAreWeakenedWhenTheyMissAManInTheSectorThatDefendsTheZone() {
        Matchups matchups = matchupsFor(noVenueEffectWithMultiplier(0.9));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.OWN_BOX);
        TeamOnPitch attackers = teamWithStyle(PlayStyle.BALANCED);
        TeamOnPitch defendersWithoutAForward = withoutPlayersOf(teamWithStyle(PlayStyle.BALANCED), Role.FORWARD, 1);

        Duel duel = matchups.openPlayDuel(base, state, attackers, firstOf(attackers), defendersWithoutAForward, firstOf(defendersWithoutAForward));

        assertThat(duel.attack()).isCloseTo(ATTACK, within(EXACT));
        assertThat(duel.defence()).isCloseTo(DEFENCE * 0.9, within(EXACT));
    }

    @Test
    void aMissingManInAnotherSectorDoesNotWeakenTheDuel() {
        Matchups matchups = matchupsFor(noVenueEffectWithMultiplier(0.9));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        TeamOnPitch attackers = withoutPlayersOf(teamWithStyle(PlayStyle.BALANCED), Role.DEFENDER, 2);
        TeamOnPitch defenders = withoutPlayersOf(teamWithStyle(PlayStyle.BALANCED), Role.FORWARD, 1);

        Duel duel = matchups.openPlayDuel(base, state, attackers, firstOf(attackers), defenders, firstOf(defenders));

        assertThat(duel).isEqualTo(base);
    }

    @Test
    void theInferiorityAlsoWeakensTheAttackersOfACounterAttack() {
        Matchups matchups = matchupsFor(noVenueEffectWithMultiplier(0.9));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD).inCounterAttack();
        TeamOnPitch attackers = withoutMidfielders(teamWithStyle(PlayStyle.BALANCED), 1);
        TeamOnPitch defenders = withoutMidfielders(teamWithStyle(PlayStyle.BALANCED), 2);

        Duel duel = matchups.counterAttackDuel(base, state, attackers, firstOf(attackers), defenders, firstOf(defenders));

        assertThat(duel.attack()).isCloseTo(ATTACK * 0.9, within(EXACT));
        assertThat(duel.defence()).isCloseTo(DEFENCE * 0.81, within(EXACT));
    }

    @Test
    void aMultiplierOfOneIsNeutralEvenWithMenMissing() {
        Matchups matchups = matchupsFor(noVenueEffectWithMultiplier(1.0));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        TeamOnPitch weakened = withoutMidfielders(teamWithStyle(PlayStyle.BALANCED), 2);

        Duel duel = matchups.openPlayDuel(base, state, weakened, firstOf(weakened), weakened, firstOf(weakened));

        assertThat(duel).isEqualTo(base);
    }

    @Test
    void aTeamStillAtElevenMenWithItsLinesReshuffledSuffersNoInferiority() {
        Matchups matchups = matchupsFor(noVenueEffectWithMultiplier(0.9));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);
        Player carrier = balanced.playersIn(Role.FORWARD).getFirst();
        Player movedToDefence = balanced.playersIn(Role.MIDFIELDER).getFirst();
        TeamOnPitch reshuffled = balanced.withRole(movedToDefence, Role.DEFENDER);
        TeamOnPitch defenders = teamWithStyle(PlayStyle.BALANCED);

        Duel duel = matchups.openPlayDuel(base, state, reshuffled, carrier, defenders, firstOf(defenders));

        assertThat(reshuffled.missingIn(Role.MIDFIELDER)).isZero();
        assertThat(duel).isEqualTo(base);
    }

    @Test
    void theMenMissingInASectorCountAtMostAsManyAsTheTeamIsShortOfEleven() {
        Matchups matchups = matchupsFor(noVenueEffectWithMultiplier(0.9));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);
        Player carrier = balanced.playersIn(Role.FORWARD).getFirst();
        Player removed = balanced.playersIn(Role.MIDFIELDER).get(0);
        Player movedToDefence = balanced.playersIn(Role.MIDFIELDER).get(1);
        TeamOnPitch shortAndReshuffled = balanced.withoutPlayer(removed).withRole(movedToDefence, Role.DEFENDER);
        TeamOnPitch defenders = teamWithStyle(PlayStyle.BALANCED);

        Duel duel = matchups.openPlayDuel(base, state, shortAndReshuffled, carrier, defenders, firstOf(defenders));

        assertThat(shortAndReshuffled.missingIn(Role.MIDFIELDER)).isEqualTo(1);
        assertThat(duel.attack()).isCloseTo(ATTACK * 0.9, within(EXACT));
    }

    @Test
    void aPlayerOutOfPositionWeakensHisSideOfTheOpenPlayDuelByTheOutOfPositionMultiplier() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        TeamOnPitch attackers = teamWithStyle(PlayStyle.BALANCED);
        TeamOnPitch defenders = teamWithStyle(PlayStyle.BALANCED);
        Player attacker = attackers.playersIn(Role.MIDFIELDER).getFirst();
        Player defender = defenders.playersIn(Role.MIDFIELDER).getFirst();
        TeamOnPitch attackersMisplaced = attackers.withRole(attacker, Role.DEFENDER);
        TeamOnPitch defendersMisplaced = defenders.withRole(defender, Role.FORWARD);
        double multiplier = config.lineup().outOfPositionMultiplier();

        Duel duel = matchups.openPlayDuel(base, state, attackersMisplaced, attacker, defendersMisplaced, defender);

        assertThat(duel.attack()).isCloseTo(ATTACK * multiplier, within(EXACT));
        assertThat(duel.defence()).isCloseTo(DEFENCE * multiplier, within(EXACT));
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

        Duel duel = matchups.counterAttackDuel(base, state, attackersMisplaced, runner, defendersMisplaced, chaser);

        assertThat(duel.attack()).isCloseTo(ATTACK * multiplier, within(EXACT));
        assertThat(duel.defence()).isCloseTo(DEFENCE * multiplier, within(EXACT));
    }

    @Test
    void aPlayerOutOfPositionWhoIsNotInTheDuelDoesNotWeakenIt() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);
        Player misplaced = balanced.playersIn(Role.MIDFIELDER).getFirst();
        Player carrier = balanced.playersIn(Role.MIDFIELDER).getLast();
        TeamOnPitch withOneMisplaced = balanced.withRole(misplaced, Role.DEFENDER);

        Duel duel = matchups.openPlayDuel(base, state, withOneMisplaced, carrier, balanced, firstOf(balanced));

        assertThat(duel).isEqualTo(base);
    }

    @Test
    void anOutOfPositionMultiplierOfOneIsNeutral() {
        EngineConfig neutral = TestConfigs.withLineup(TestConfigs.withoutHomeAdvantage(config),
                new LineupConfig(config.lineup().maximumSubstitutions(), 1.0, 1.0,
                        config.lineup().substitutionDuration()));
        Matchups matchups = matchupsFor(neutral);
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);
        Player misplaced = balanced.playersIn(Role.MIDFIELDER).getFirst();
        TeamOnPitch withOneMisplaced = balanced.withRole(misplaced, Role.DEFENDER);

        Duel duel = matchups.openPlayDuel(base, state, withOneMisplaced, misplaced, withOneMisplaced, misplaced);

        assertThat(duel).isEqualTo(base);
    }

    @Test
    void theBalancedStyleWithoutHomeAdvantageLeavesTheShotUntouched() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);

        for (MatchSide possession : MatchSide.values()) {
            for (ZoneRole zoneRole : ZoneRole.values()) {
                MatchState state = openPlayAt(possession, zoneRole);

                assertThat(matchups.shootingMultiplier(state, balanced, firstOf(balanced))).isEqualTo(1.0);
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

        double shooting = matchups.shootingMultiplier(state, balanced, firstOf(balanced));
        double goalkeeping = matchups.goalkeepingMultiplier(state, balanced);

        assertThat(shooting).isCloseTo(homeAttacks, within(EXACT));
        assertThat(goalkeeping).isCloseTo(homeDefends, within(EXACT));
    }

    @Test
    void theShootingMultiplierCombinesStyleVenueInferiorityAndPositionOfTheShooter() {
        Matchups matchups = matchupsFor(withInferiorityMultiplierAndTheDefaultVenue(0.9));
        MatchState state = openPlayAt(MatchSide.HOME, ZoneRole.OPPONENT_BOX);
        TeamOnPitch attacking = teamWithStyle(PlayStyle.ATTACKING);
        Player lastForward = attacking.playersIn(Role.FORWARD).getLast();
        Player misplaced = attacking.playersIn(Role.FORWARD).getFirst();
        TeamOnPitch oneForwardLessAndOneMisplaced = attacking.withoutPlayer(lastForward)
                .withRole(misplaced, Role.MIDFIELDER);
        double style = config.playStyles().effectOf(PlayStyle.ATTACKING).attack();
        double inferiority = 0.9;
        double position = config.lineup().outOfPositionMultiplier();

        double shooting = matchups.shootingMultiplier(state, oneForwardLessAndOneMisplaced, misplaced);

        assertThat(oneForwardLessAndOneMisplaced.missingIn(Role.FORWARD)).isEqualTo(1);
        assertThat(shooting).isCloseTo(style * config.homeAdvantage() * inferiority * position, within(EXACT));
    }

    @Test
    void theGoalkeepingMultiplierWeakensWithTheMenMissingInTheLineThatDefendsTheBox() {
        Matchups matchups = matchupsFor(noVenueEffectWithMultiplier(0.9));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.OPPONENT_BOX);
        TeamOnPitch defenders = withoutPlayersOf(teamWithStyle(PlayStyle.DEFENSIVE), Role.DEFENDER, 2);
        double style = config.playStyles().effectOf(PlayStyle.DEFENSIVE).defence();

        double goalkeeping = matchups.goalkeepingMultiplier(state, defenders);

        assertThat(goalkeeping).isCloseTo(style * 0.81, within(EXACT));
    }

    private EngineConfig withInferiorityMultiplierAndTheDefaultVenue(double multiplier) {
        EngineConfig withVenue = config;
        return TestConfigs.withLineup(withVenue, new LineupConfig(config.lineup().maximumSubstitutions(), multiplier,
                config.lineup().outOfPositionMultiplier(), config.lineup().substitutionDuration()));
    }

    private Player firstOf(TeamOnPitch team) {
        return team.players().getFirst();
    }

    private EngineConfig noVenueEffectWithMultiplier(double multiplier) {
        EngineConfig noVenue = TestConfigs.withoutHomeAdvantage(config);
        return TestConfigs.withLineup(noVenue, new LineupConfig(config.lineup().maximumSubstitutions(), multiplier, config.lineup().outOfPositionMultiplier(),
                config.lineup().substitutionDuration()));
    }

    private TeamOnPitch withoutMidfielders(TeamOnPitch team, int count) {
        return withoutPlayersOf(team, Role.MIDFIELDER, count);
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
        return TeamOnPitch.startingWith(TestTeams.team("T-" + style, style, 50));
    }

    private MatchState openPlayAt(MatchSide possession, ZoneRole zoneRole) {
        Zone zone = layout.zones().stream()
                .filter(candidate -> layout.roleOf(candidate, possession) == zoneRole)
                .findFirst()
                .orElseThrow();
        return MatchState.openPlay(possession, zone);
    }
}
