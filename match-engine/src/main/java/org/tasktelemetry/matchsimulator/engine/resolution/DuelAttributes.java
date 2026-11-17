package org.tasktelemetry.matchsimulator.engine.resolution;

import java.util.function.ToIntFunction;

import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.fatigue.FatiguePenalty;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;

public enum DuelAttributes {

    PASSING(PlayerAttributes::passing, PlayerAttributes::positioning),
    DRIBBLING(PlayerAttributes::dribbling, PlayerAttributes::tackling),
    CROSS(PlayerAttributes::passing, PlayerAttributes::positioning),
    AERIAL(PlayerAttributes::aerial, PlayerAttributes::aerial),
    PACE(PlayerAttributes::pace, PlayerAttributes::pace);

    private final ToIntFunction<PlayerAttributes> attackAttribute;
    private final ToIntFunction<PlayerAttributes> defenceAttribute;

    DuelAttributes(ToIntFunction<PlayerAttributes> attackAttribute, ToIntFunction<PlayerAttributes> defenceAttribute) {
        this.attackAttribute = attackAttribute;
        this.defenceAttribute = defenceAttribute;
    }

    public double attackOf(TeamOnPitch team, Player attacker, FatiguePenalty fatigue) {
        return effectiveValueOf(attackAttribute, team, attacker, fatigue);
    }

    public double defenceOf(TeamOnPitch team, Player defender, FatiguePenalty fatigue) {
        return effectiveValueOf(defenceAttribute, team, defender, fatigue);
    }

    public double attackAverageIn(ZonePresence zonePresence, FatiguePenalty fatigue, TeamOnPitch team,
            ZoneRole zoneRole) {
        return zonePresence.averageAttribute(team, zoneRole, player -> attackOf(team, player, fatigue));
    }

    public double defenceAverageIn(ZonePresence zonePresence, FatiguePenalty fatigue, TeamOnPitch team,
            ZoneRole zoneRole) {
        return zonePresence.averageAttribute(team, zoneRole, player -> defenceOf(team, player, fatigue));
    }

    private static double effectiveValueOf(ToIntFunction<PlayerAttributes> attribute, TeamOnPitch team,
            Player player, FatiguePenalty fatigue) {
        int base = attribute.applyAsInt(player.attributes());
        double factor = fatigue.factorOf(team, player);
        return base * factor;
    }
}
