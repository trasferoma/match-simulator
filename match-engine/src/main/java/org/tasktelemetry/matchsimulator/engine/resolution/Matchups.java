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

    public Matchups(PitchLayout layout, PlayerPicker picker, PlayStyleModifiers playStyles, double homeAdvantage,
            double inferiorityMultiplier) {
        this.layout = layout;
        this.picker = picker;
        this.playStyles = playStyles;
        this.homeAdvantage = homeAdvantage;
        this.inferiorityMultiplier = inferiorityMultiplier;
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

    public Duel openPlayDuel(Duel base, MatchState state, TeamOnPitch attackers, TeamOnPitch defenders) {
        MatchSide attackingSide = state.possession();
        MatchSide defendingSide = attackingSide.opposite();
        ZoneRole attackingRole = layout.roleOf(state.zone(), attackingSide);
        ZoneRole defendingRole = layout.roleOf(state.zone(), defendingSide);
        StyleEffect attackerStyle = styleOf(attackers);
        StyleEffect defenderStyle = styleOf(defenders);
        double attackerStrength = attackStrength(attackerStyle, attackingRole);
        double defenderStrength = defenceStrength(defenderStyle, attackingRole);
        double attackerModifier = modifierOf(attackerStrength, attackingSide, attackers, attackingRole);
        double defenderModifier = modifierOf(defenderStrength, defendingSide, defenders, defendingRole);
        return base.withAttackModifier(attackerModifier).withDefenceModifier(defenderModifier);
    }

    public Duel counterAttackDuel(Duel base, MatchState state, TeamOnPitch attackers, TeamOnPitch defenders) {
        MatchSide attackingSide = state.possession();
        MatchSide defendingSide = attackingSide.opposite();
        ZoneRole attackingRole = layout.roleOf(state.zone(), attackingSide);
        ZoneRole defendingRole = layout.roleOf(state.zone(), defendingSide);
        StyleEffect attackerStyle = styleOf(attackers);
        double advance = attackerStyle.counterAttackAdvance();
        double attackerModifier = modifierOf(advance, attackingSide, attackers, attackingRole);
        double defenderModifier = modifierOf(NEUTRAL, defendingSide, defenders, defendingRole);
        return base.withAttackModifier(attackerModifier).withDefenceModifier(defenderModifier);
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
