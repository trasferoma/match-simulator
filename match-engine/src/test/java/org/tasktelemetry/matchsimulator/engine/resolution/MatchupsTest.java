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

        Duel duel = matchups.openPlayDuel(base, state, balanced, balanced);

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

        Duel duel = matchups.counterAttackDuel(base, state, balanced, balanced);

        assertThat(duel.attack()).isCloseTo(expectedAttack, within(EXACT));
        assertThat(duel.defence()).isCloseTo(expectedDefence, within(EXACT));
    }

    @Test
    void theHomeAdvantageIsTheConfiguredOne() {
        EngineConfig stronger = TestConfigs.withHomeAdvantage(config, 1.25);
        Matchups matchups = matchupsFor(stronger);
        MatchState state = openPlayAt(MatchSide.HOME, ZoneRole.MIDFIELD);
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);

        Duel duel = matchups.openPlayDuel(base, state, balanced, balanced);

        assertThat(duel.attack()).isCloseTo(ATTACK * 1.25, within(EXACT));
    }

    @Test
    void theBalancedStyleWithoutHomeAdvantageLeavesEveryDuelUntouched() {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        TeamOnPitch balanced = teamWithStyle(PlayStyle.BALANCED);

        for (MatchSide possession : MatchSide.values()) {
            for (ZoneRole zoneRole : ZoneRole.values()) {
                MatchState state = openPlayAt(possession, zoneRole);

                assertThat(matchups.openPlayDuel(base, state, balanced, balanced)).isEqualTo(base);
                assertThat(matchups.counterAttackDuel(base, state, balanced, balanced)).isEqualTo(base);
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

        Duel duel = matchups.openPlayDuel(base, state, attackers, defenders);

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

        Duel duel = matchups.openPlayDuel(base, state, attackers, defenders);

        assertThat(duel.attack()).isCloseTo(ATTACK, within(EXACT));
        assertThat(duel.defence()).isCloseTo(DEFENCE * multiplier, within(EXACT));
    }

    @ParameterizedTest
    @EnumSource(PlayStyle.class)
    void onlyTheCounterAttackStyleChangesTheAdvanceOfTheCounterAttackAndOnlyForTheAttackers(PlayStyle style) {
        Matchups matchups = matchupsFor(TestConfigs.withoutHomeAdvantage(config));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        double multiplier = style == PlayStyle.COUNTER_ATTACK ? 1.10 : 1.00;

        TeamOnPitch balancedDefenders = teamWithStyle(PlayStyle.BALANCED);

        Duel asAttackers = matchups.counterAttackDuel(base, state, teamWithStyle(style), balancedDefenders);

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

        Duel duel = matchups.openPlayDuel(base, counterAttack, possession, balanced);

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

        Duel duel = matchups.openPlayDuel(base, state, attackers, defenders);

        assertThat(duel.attack()).isCloseTo(ATTACK * expectedFactor, within(EXACT));
        assertThat(duel.defence()).isCloseTo(DEFENCE, within(EXACT));
    }

    @Test
    void theDefendersAreWeakenedWhenTheyMissAManInTheSectorThatDefendsTheZone() {
        Matchups matchups = matchupsFor(noVenueEffectWithMultiplier(0.9));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.OWN_BOX);
        TeamOnPitch attackers = teamWithStyle(PlayStyle.BALANCED);
        TeamOnPitch defendersWithoutAForward = withoutPlayersOf(teamWithStyle(PlayStyle.BALANCED), Role.FORWARD, 1);

        Duel duel = matchups.openPlayDuel(base, state, attackers, defendersWithoutAForward);

        assertThat(duel.attack()).isCloseTo(ATTACK, within(EXACT));
        assertThat(duel.defence()).isCloseTo(DEFENCE * 0.9, within(EXACT));
    }

    @Test
    void aMissingManInAnotherSectorDoesNotWeakenTheDuel() {
        Matchups matchups = matchupsFor(noVenueEffectWithMultiplier(0.9));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        TeamOnPitch attackers = withoutPlayersOf(teamWithStyle(PlayStyle.BALANCED), Role.DEFENDER, 2);
        TeamOnPitch defenders = withoutPlayersOf(teamWithStyle(PlayStyle.BALANCED), Role.FORWARD, 1);

        Duel duel = matchups.openPlayDuel(base, state, attackers, defenders);

        assertThat(duel).isEqualTo(base);
    }

    @Test
    void theInferiorityAlsoWeakensTheAttackersOfACounterAttack() {
        Matchups matchups = matchupsFor(noVenueEffectWithMultiplier(0.9));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD).inCounterAttack();
        TeamOnPitch attackers = withoutMidfielders(teamWithStyle(PlayStyle.BALANCED), 1);
        TeamOnPitch defenders = withoutMidfielders(teamWithStyle(PlayStyle.BALANCED), 2);

        Duel duel = matchups.counterAttackDuel(base, state, attackers, defenders);

        assertThat(duel.attack()).isCloseTo(ATTACK * 0.9, within(EXACT));
        assertThat(duel.defence()).isCloseTo(DEFENCE * 0.81, within(EXACT));
    }

    @Test
    void aMultiplierOfOneIsNeutralEvenWithMenMissing() {
        Matchups matchups = matchupsFor(noVenueEffectWithMultiplier(1.0));
        MatchState state = openPlayAt(MatchSide.AWAY, ZoneRole.MIDFIELD);
        TeamOnPitch weakened = withoutMidfielders(teamWithStyle(PlayStyle.BALANCED), 2);

        Duel duel = matchups.openPlayDuel(base, state, weakened, weakened);

        assertThat(duel).isEqualTo(base);
    }

    private EngineConfig noVenueEffectWithMultiplier(double multiplier) {
        EngineConfig noVenue = TestConfigs.withoutHomeAdvantage(config);
        return TestConfigs.withLineup(noVenue, new LineupConfig(config.lineup().maximumSubstitutions(), multiplier,
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
