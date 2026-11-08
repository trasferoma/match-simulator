package org.tasktelemetry.matchsimulator.engine.event;

import java.util.Objects;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class Participants {

    private static final Participants NOBODY = new Participants(null, null);

    private final Player primary;
    private final Player secondary;

    private Participants(Player primary, Player secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    public static Participants nobody() {
        return NOBODY;
    }

    public static Participants of(Player primary) {
        Objects.requireNonNull(primary, "primary player must not be null");
        return new Participants(primary, null);
    }

    public static Participants of(Player primary, Player secondary) {
        Objects.requireNonNull(primary, "primary player must not be null");
        Objects.requireNonNull(secondary, "secondary player must not be null");
        return new Participants(primary, secondary);
    }

    public Optional<Player> primary() {
        return Optional.ofNullable(primary);
    }

    public Optional<Player> secondary() {
        return Optional.ofNullable(secondary);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Participants participants
                && Objects.equals(participants.primary, primary)
                && Objects.equals(participants.secondary, secondary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(primary, secondary);
    }

    @Override
    public String toString() {
        String primaryName = primary().map(Player::name).orElse("-");
        String secondaryName = secondary().map(Player::name).orElse("-");
        return "Participants[primary=" + primaryName + ", secondary=" + secondaryName + "]";
    }
}
