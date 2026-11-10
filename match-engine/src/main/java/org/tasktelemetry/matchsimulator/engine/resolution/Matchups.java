package org.tasktelemetry.matchsimulator.engine.resolution;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.config.PlayStyleModifiers;
import org.tasktelemetry.matchsimulator.engine.config.StyleEffect;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class Matchups {

    private static final double NEUTRAL = 1.0;

    private final PitchLayout layout;
    private final PlayerPicker picker;
    private final PlayStyleModifiers playStyles;
    private final double homeAdvantage;
    private final double inferiorityMultiplier;
    private final OutOfPositionPenalty outOfPosition;

    public Matchups(PitchLayout layout, PlayerPicker picker, PlayStyleModifiers playStyles, double homeAdvantage,
            double inferiorityMultiplier, OutOfPositionPenalty outOfPosition) {
        this.layout = layout;
        this.picker = picker;
        this.playStyles = playStyles;
        this.homeAdvantage = homeAdvantage;
        this.inferiorityMultiplier = inferiorityMultiplier;
        this.outOfPosition = outOfPosition;
    }

    public Player pickAttacker(MatchState state, TeamOnPitch attackers) {
        ZoneRole zoneRole = layout.roleOf(state.zone(), state.possession());
        return picker.pickFrom(attackers, zoneRole);
    }

    public Player pickDefender(MatchState state, TeamOnPitch defenders) {
        MatchSide defendingSide = state.possession().opposite();
        ZoneRole zoneRole = layout.roleOf(state.zone(), defendingSide);
        return picker.pickFrom(defenders, zoneRole);
    }

    public Duel openPlayDuel(Duel base, MatchState state, TeamOnPitch attackers, Player attacker,
            TeamOnPitch defenders, Player defender) {
        double attackerModifier = attackingModifier(state, attackers, attacker);
        double defenderModifier = defendingModifier(state, defenders, defender);
        return base.withAttackModifier(attackerModifier).withDefenceModifier(defenderModifier);
    }

    public Duel counterAttackDuel(Duel base, MatchState state, TeamOnPitch attackers, Player attacker,
            TeamOnPitch defenders, Player defender) {
        MatchSide attackingSide = state.possession();
        MatchSide defendingSide = attackingSide.opposite();
        ZoneRole attackingRole = layout.roleOf(state.zone(), attackingSide);
        ZoneRole defendingRole = layout.roleOf(state.zone(), defendingSide);
        double advance = styleOf(attackers).counterAttackAdvance();
        double attackerTeamModifier = modifierOf(advance, attackingSide, attackers, attackingRole);
        double defenderTeamModifier = modifierOf(NEUTRAL, defendingSide, defenders, defendingRole);
        double attackerPositionFactor = outOfPosition.factorOf(attackers, attacker);
        double defenderPositionFactor = outOfPosition.factorOf(defenders, defender);
        double attackerModifier = attackerTeamModifier * attackerPositionFactor;
        double defenderModifier = defenderTeamModifier * defenderPositionFactor;
        return base.withAttackModifier(attackerModifier).withDefenceModifier(defenderModifier);
    }

    public double shootingMultiplier(MatchState state, TeamOnPitch attackers, Player shooter) {
        return attackingModifier(state, attackers, shooter);
    }

    public double goalkeepingMultiplier(MatchState state, TeamOnPitch defenders) {
        return defendingTeamModifier(state, defenders);
    }

    private double attackingModifier(MatchState state, TeamOnPitch attackers, Player attacker) {
        double teamModifier = attackingTeamModifier(state, attackers);
        double positionFactor = outOfPosition.factorOf(attackers, attacker);
        return teamModifier * positionFactor;
    }

    private double defendingModifier(MatchState state, TeamOnPitch defenders, Player defender) {
        double teamModifier = defendingTeamModifier(state, defenders);
        double positionFactor = outOfPosition.factorOf(defenders, defender);
        return teamModifier * positionFactor;
    }

    private double attackingTeamModifier(MatchState state, TeamOnPitch attackers) {
        MatchSide attackingSide = state.possession();
        ZoneRole attackingRole = layout.roleOf(state.zone(), attackingSide);
        double strength = attackStrength(styleOf(attackers), attackingRole);
        return modifierOf(strength, attackingSide, attackers, attackingRole);
    }

    private double defendingTeamModifier(MatchState state, TeamOnPitch defenders) {
        MatchSide attackingSide = state.possession();
        MatchSide defendingSide = attackingSide.opposite();
        ZoneRole attackingRole = layout.roleOf(state.zone(), attackingSide);
        ZoneRole defendingRole = layout.roleOf(state.zone(), defendingSide);
        double strength = defenceStrength(styleOf(defenders), attackingRole);
        return modifierOf(strength, defendingSide, defenders, defendingRole);
    }

    private double modifierOf(double styleStrength, MatchSide side, TeamOnPitch team, ZoneRole zoneRole) {
        double venue = homeFactor(side);
        double inferiority = inferiorityFactor(team, zoneRole);
        return styleStrength * venue * inferiority;
    }

    private double inferiorityFactor(TeamOnPitch team, ZoneRole zoneRole) {
        Role sector = PlayerPicker.sectorOf(zoneRole);
        int menDown = team.missingIn(sector);
        return StrictMath.pow(inferiorityMultiplier, menDown);
    }

    private StyleEffect styleOf(TeamOnPitch team) {
        return playStyles.effectOf(team.team().style());
    }

    private double attackStrength(StyleEffect style, ZoneRole zoneRole) {
        return zoneRole == ZoneRole.MIDFIELD ? style.midfield() : style.attack();
    }

    private double defenceStrength(StyleEffect style, ZoneRole zoneRole) {
        return zoneRole == ZoneRole.MIDFIELD ? style.midfield() : style.defence();
    }

    private double homeFactor(MatchSide side) {
        return side == MatchSide.HOME ? homeAdvantage : NEUTRAL;
    }
}
