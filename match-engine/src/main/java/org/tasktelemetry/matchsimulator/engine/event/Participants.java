package org.tasktelemetry.matchsimulator.engine.event;

import java.util.Objects;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class Participants {

    private static final Participants NOBODY = new Participants(null, null);

    private final Member primary;
    private final Member secondary;

    private Participants(Member primary, Member secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    public static Participants nobody() {
        return NOBODY;
    }

    public static Participants of(Player primary, MatchSide primarySide) {
        Member primaryMember = memberOf(primary, primarySide, "primary");
        return new Participants(primaryMember, null);
    }

    public static Participants of(Player primary, MatchSide primarySide, Player secondary,
            MatchSide secondarySide) {
        Member primaryMember = memberOf(primary, primarySide, "primary");
        Member secondaryMember = memberOf(secondary, secondarySide, "secondary");
        return new Participants(primaryMember, secondaryMember);
    }

    public Optional<Player> primary() {
        return Optional.ofNullable(primary).map(Member::player);
    }

    public Optional<MatchSide> primarySide() {
        return Optional.ofNullable(primary).map(Member::side);
    }

    public Optional<Player> secondary() {
        return Optional.ofNullable(secondary).map(Member::player);
    }

    public Optional<MatchSide> secondarySide() {
        return Optional.ofNullable(secondary).map(Member::side);
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

    private static Member memberOf(Player player, MatchSide side, String label) {
        Objects.requireNonNull(player, label + " player must not be null");
        Objects.requireNonNull(side, label + " side must not be null");
        return new Member(player, side);
    }

    private record Member(Player player, MatchSide side) {
    }
}
