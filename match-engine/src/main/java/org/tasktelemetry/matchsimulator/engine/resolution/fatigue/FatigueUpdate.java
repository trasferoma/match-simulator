package org.tasktelemetry.matchsimulator.engine.resolution.fatigue;

import java.util.List;

import org.tasktelemetry.matchsimulator.engine.config.FatigueConfig;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public record FatigueUpdate(TeamOnPitch team, List<BandShift> shifts) {

    public record BandShift(Player player, FatigueBand band) {
    }

    public FatigueUpdate {
        shifts = List.copyOf(shifts);
    }

    public static FatigueUpdate between(TeamOnPitch before, TeamOnPitch after, FatigueConfig config) {
        List<BandShift> shifts = after.players().stream()
                .filter(player -> hasChangedBand(before, after, player, config))
                .map(player -> shiftOf(after, player, config))
                .toList();
        return new FatigueUpdate(after, shifts);
    }

    private static BandShift shiftOf(TeamOnPitch after, Player player, FatigueConfig config) {
        FatigueBand reached = bandOf(after, player, config);
        return new BandShift(player, reached);
    }

    private static boolean hasChangedBand(TeamOnPitch before, TeamOnPitch after, Player player,
            FatigueConfig config) {
        return bandOf(before, player, config) != bandOf(after, player, config);
    }

    private static FatigueBand bandOf(TeamOnPitch team, Player player, FatigueConfig config) {
        double energy = team.energy().energyOf(player);
        return config.bandOf(energy);
    }
}
