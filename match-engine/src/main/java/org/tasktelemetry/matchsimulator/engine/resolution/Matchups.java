package org.tasktelemetry.matchsimulator.engine.resolution;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.config.PlayStyleModifiers;
import org.tasktelemetry.matchsimulator.engine.config.StyleEffect;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class Matchups {

    private static final double NEUTRAL = 1.0;

    private final PitchLayout layout;
    private final PlayerPicker picker;
    private final ZonePresence zonePresence;
    private final PlayStyleModifiers playStyles;
    private final double homeAdvantage;
    private final OutOfPositionPenalty outOfPosition;

    public Matchups(PitchLayout layout, PlayerPicker picker, ZonePresence zonePresence, PlayStyleModifiers playStyles,
            double homeAdvantage, OutOfPositionPenalty outOfPosition) {
        this.layout = layout;
        this.picker = picker;
        this.zonePresence = zonePresence;
        this.playStyles = playStyles;
        this.homeAdvantage = homeAdvantage;
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

    public Duel openPlayDuel(DuelAttributes attributes, MatchState state, TeamOnPitch attackers, Player attacker,
            TeamOnPitch defenders, Player defender) {
        Duel zoneDuel = zoneDuel(attributes, state, attackers, attacker, defenders, defender);
        double attackerModifier = attackingModifier(state, attackers, attacker);
        double defenderModifier = defendingModifier(state, defenders, defender);
        return zoneDuel.withAttackModifier(attackerModifier).withDefenceModifier(defenderModifier);
    }

    public Duel counterAttackDuel(MatchState state, TeamOnPitch attackers, Player attacker, TeamOnPitch defenders,
            Player defender) {
        MatchSide attackingSide = state.possession();
        MatchSide defendingSide = attackingSide.opposite();
        int attackerPace = DuelAttributes.PACE.attackOf(attacker);
        int defenderPace = DuelAttributes.PACE.defenceOf(defender);
        double advance = styleOf(attackers).counterAttackAdvance();
        double attackerTeamModifier = modifierOf(advance, attackingSide);
        double defenderTeamModifier = modifierOf(NEUTRAL, defendingSide);
        double attackerPositionFactor = outOfPosition.factorOf(attackers, attacker);
        double defenderPositionFactor = outOfPosition.factorOf(defenders, defender);
        double attackerModifier = attackerTeamModifier * attackerPositionFactor;
        double defenderModifier = defenderTeamModifier * defenderPositionFactor;
        Duel paceDuel = Duel.between(attackerPace, defenderPace);
        return paceDuel.withAttackModifier(attackerModifier).withDefenceModifier(defenderModifier);
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
        return modifierOf(strength, attackingSide);
    }

    private double defendingTeamModifier(MatchState state, TeamOnPitch defenders) {
        MatchSide attackingSide = state.possession();
        MatchSide defendingSide = attackingSide.opposite();
        ZoneRole attackingRole = layout.roleOf(state.zone(), attackingSide);
        double strength = defenceStrength(styleOf(defenders), attackingRole);
        return modifierOf(strength, defendingSide);
    }

    private Duel zoneDuel(DuelAttributes attributes, MatchState state, TeamOnPitch attackers, Player attacker,
            TeamOnPitch defenders, Player defender) {
        MatchSide defendingSide = state.possession().opposite();
        ZoneRole attackingRole = layout.roleOf(state.zone(), state.possession());
        ZoneRole defendingRole = layout.roleOf(state.zone(), defendingSide);
        double attackAverage = attributes.attackAverageIn(zonePresence, attackers, attackingRole);
        double defenceAverage = attributes.defenceAverageIn(zonePresence, defenders, defendingRole);
        int attackerValue = attributes.attackOf(attacker);
        int defenderValue = attributes.defenceOf(defender);
        double attackerStrength = blendOfAverageAndRepresentative(attackAverage, attackerValue);
        double defenderStrength = blendOfAverageAndRepresentative(defenceAverage, defenderValue);
        double superiority = zonePresence.superiorityFactor(attackers, attackingRole, defenders, defendingRole);
        return new Duel(attackerStrength * superiority, defenderStrength);
    }

    private double blendOfAverageAndRepresentative(double average, int representative) {
        double representativeWeight = zonePresence.representativeWeight();
        double averagePart = StrictMath.pow(average, NEUTRAL - representativeWeight);
        double representativePart = StrictMath.pow(representative, representativeWeight);
        return averagePart * representativePart;
    }

    private double modifierOf(double styleStrength, MatchSide side) {
        double venue = homeFactor(side);
        return styleStrength * venue;
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
