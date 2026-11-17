package org.tasktelemetry.matchsimulator.engine.resolution.fatigue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.config.FatigueConfig;
import org.tasktelemetry.matchsimulator.engine.config.PlayStyleModifiers;
import org.tasktelemetry.matchsimulator.engine.config.StyleEffect;
import org.tasktelemetry.matchsimulator.engine.resolution.ActionOutcome;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.TeamEnergy;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class EnergyDrain {

    private static final double NO_ROLE_EFFECT = 1.0;

    private final FatigueConfig config;
    private final PlayStyleModifiers playStyles;

    public EnergyDrain(FatigueConfig config, PlayStyleModifiers playStyles) {
        this.config = config;
        this.playStyles = playStyles;
    }

    public double consumptionOf(TeamOnPitch team, MatchSide side, Player player, ActionOutcome outcome) {
        double staminaFactor = staminaFactorOf(player);
        double styleFactor = styleFactorOf(team);
        double baseDrain = baseDrainOf(team, player, outcome);
        double participationDrain = participationDrainOf(side, player, outcome);
        return staminaFactor * styleFactor * (baseDrain + participationDrain);
    }

    public FatigueUpdate drain(TeamOnPitch team, MatchSide side, ActionOutcome outcome) {
        if (outcome.durationSeconds() == 0) {
            List<FatigueUpdate.BandShift> noShifts = List.of();
            return new FatigueUpdate(team, noShifts);
        }
        TeamEnergy drained = drainedEnergy(team, side, outcome);
        TeamOnPitch tired = team.withEnergy(drained);
        return FatigueUpdate.between(team, tired, config);
    }

    private TeamEnergy drainedEnergy(TeamOnPitch team, MatchSide side, ActionOutcome outcome) {
        Map<Player, Double> energyByPlayer = new LinkedHashMap<>();
        for (Player player : team.players()) {
            double consumption = consumptionOf(team, side, player, outcome);
            double remaining = team.energy().energyOf(player) - consumption;
            double energy = Math.max(TeamEnergy.EMPTY, remaining);
            energyByPlayer.put(player, energy);
        }
        return new TeamEnergy(energyByPlayer);
    }

    private double staminaFactorOf(Player player) {
        int stamina = player.attributes().stamina();
        return config.drainFactorOf(stamina);
    }

    private double styleFactorOf(TeamOnPitch team) {
        StyleEffect effect = playStyles.effectOf(team.style());
        return effect.energyCost();
    }

    private double baseDrainOf(TeamOnPitch team, Player player, ActionOutcome outcome) {
        double minutes = (double) outcome.durationSeconds() / MatchPeriod.SECONDS_PER_MINUTE;
        return config.drainPerMinute() * minutes * roleFactorOf(team, player);
    }

    private double roleFactorOf(TeamOnPitch team, Player player) {
        boolean playsInGoal = team.roleOf(player) == Role.GOALKEEPER;
        return playsInGoal ? config.goalkeeperDrainMultiplier() : NO_ROLE_EFFECT;
    }

    private double participationDrainOf(MatchSide side, Player player, ActionOutcome outcome) {
        boolean isInvolved = outcome.involved().contains(player, side);
        return isInvolved ? config.participationCost() : 0.0;
    }
}
