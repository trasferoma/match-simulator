package org.tasktelemetry.matchsimulator.coach.human;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public record SheetPlayer(int number, Player player, PlayerStatus status, Role sector, FatigueBand fatigue,
        boolean cautioned) {

    public SheetPlayer {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(sector, "sector must not be null");
        Objects.requireNonNull(fatigue, "fatigue band must not be null");
        if (number < 1) {
            throw new IllegalArgumentException("roster numbers start from 1, was " + number);
        }
    }

    public boolean isOnPitch() {
        return status == PlayerStatus.ON_PITCH;
    }

    public boolean isAvailableOnBench() {
        return status == PlayerStatus.ON_BENCH;
    }

    public boolean isOutOfPosition() {
        return sector != player.role();
    }

    public double movementAverage() {
        return player.attributes().movementAverage();
    }

    SheetPlayer shiftedTo(Role newSector) {
        return new SheetPlayer(number, player, status, newSector, fatigue, cautioned);
    }

    SheetPlayer substitutedOut() {
        return new SheetPlayer(number, player, PlayerStatus.SUBSTITUTED, player.role(), FatigueBand.FRESH, false);
    }

    SheetPlayer enteringTheMatch() {
        return new SheetPlayer(number, player, PlayerStatus.ON_PITCH, player.role(), FatigueBand.FRESH, false);
    }
}
