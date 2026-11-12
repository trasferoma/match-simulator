package org.tasktelemetry.matchsimulator.engine.resolution;

import java.util.function.ToIntFunction;

import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
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

    public int attackOf(Player attacker) {
        return attackAttribute.applyAsInt(attacker.attributes());
    }

    public int defenceOf(Player defender) {
        return defenceAttribute.applyAsInt(defender.attributes());
    }

    public double attackAverageIn(ZonePresence zonePresence, TeamOnPitch team, ZoneRole zoneRole) {
        return zonePresence.averageAttribute(team, zoneRole, attackAttribute);
    }

    public double defenceAverageIn(ZonePresence zonePresence, TeamOnPitch team, ZoneRole zoneRole) {
        return zonePresence.averageAttribute(team, zoneRole, defenceAttribute);
    }
}
