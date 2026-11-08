package org.tasktelemetry.matchsimulator.engine.state;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public record MatchState(MatchSide possession, Zone zone, PlayPhase phase, SetPieceKind setPiece, Player crosser) {

    public MatchState {
        Objects.requireNonNull(possession, "possession must not be null");
        Objects.requireNonNull(zone, "zone must not be null");
        Objects.requireNonNull(phase, "phase must not be null");
        requireKindOnlyInSetPiece(phase, setPiece);
    }

    public MatchState(MatchSide possession, Zone zone, PlayPhase phase, SetPieceKind setPiece) {
        this(possession, zone, phase, setPiece, null);
    }

    public MatchState(MatchSide possession, Zone zone, PlayPhase phase) {
        this(possession, zone, phase, null, null);
    }

    public static MatchState openPlay(MatchSide possession, Zone zone) {
        return new MatchState(possession, zone, PlayPhase.BUILD_UP, null, null);
    }

    public static MatchState forSetPiece(MatchSide possession, Zone zone, SetPieceKind kind) {
        return new MatchState(possession, zone, PlayPhase.SET_PIECE, kind, null);
    }

    public MatchState inOpenPlay() {
        return openPlay(possession, zone);
    }

    public MatchState withCrossInFlightFrom(Player crosser) {
        Objects.requireNonNull(crosser, "crosser must not be null");
        return new MatchState(possession, zone, phase, setPiece, crosser);
    }

    public boolean hasCrossInFlight() {
        return crosser != null;
    }

    private static void requireKindOnlyInSetPiece(PlayPhase phase, SetPieceKind setPiece) {
        boolean isSetPiece = phase == PlayPhase.SET_PIECE;
        if (isSetPiece && setPiece == null) {
            throw new IllegalArgumentException("a set-piece state needs the kind of set piece");
        }
        if (!isSetPiece && setPiece != null) {
            throw new IllegalArgumentException("a state in " + phase + " has no set piece, but the kind is "
                    + setPiece);
        }
    }
}
