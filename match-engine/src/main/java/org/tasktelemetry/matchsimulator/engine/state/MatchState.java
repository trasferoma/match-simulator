package org.tasktelemetry.matchsimulator.engine.state;

import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public record MatchState(MatchSide possession, Zone zone, PlayPhase phase, SetPieceKind setPiece, Player crosser,
        Player lastPasser) {

    public MatchState {
        Objects.requireNonNull(possession, "possession must not be null");
        Objects.requireNonNull(zone, "zone must not be null");
        Objects.requireNonNull(phase, "phase must not be null");
        requireKindOnlyInSetPiece(phase, setPiece);
        requireCrosserOnlyWhereACrossCanArise(phase, setPiece, crosser);
        requireLastPasserOnlyInOpenBuildUp(phase, crosser, lastPasser);
    }

    public MatchState(MatchSide possession, Zone zone, PlayPhase phase, SetPieceKind setPiece, Player crosser) {
        this(possession, zone, phase, setPiece, crosser, null);
    }

    public MatchState(MatchSide possession, Zone zone, PlayPhase phase, SetPieceKind setPiece) {
        this(possession, zone, phase, setPiece, null, null);
    }

    public MatchState(MatchSide possession, Zone zone, PlayPhase phase) {
        this(possession, zone, phase, null, null, null);
    }

    public static MatchState openPlay(MatchSide possession, Zone zone) {
        return new MatchState(possession, zone, PlayPhase.BUILD_UP, null, null, null);
    }

    public static MatchState forSetPiece(MatchSide possession, Zone zone, SetPieceKind kind) {
        return new MatchState(possession, zone, PlayPhase.SET_PIECE, kind, null, null);
    }

    public MatchState inOpenPlay() {
        return openPlay(possession, zone);
    }

    public MatchState inCounterAttack() {
        return new MatchState(possession, zone, PlayPhase.COUNTER_ATTACK);
    }

    public MatchState withCrossInFlightFrom(Player crosser) {
        Objects.requireNonNull(crosser, "crosser must not be null");
        return new MatchState(possession, zone, phase, setPiece, crosser, null);
    }

    public MatchState withLastPasser(Player passer) {
        Objects.requireNonNull(passer, "passer must not be null");
        return new MatchState(possession, zone, phase, setPiece, crosser, passer);
    }

    public MatchState withoutLastPasser() {
        return new MatchState(possession, zone, phase, setPiece, crosser, null);
    }

    public MatchState movedTo(Zone target) {
        return new MatchState(possession, target, phase, setPiece, crosser, lastPasser);
    }

    public boolean hasCrossInFlight() {
        return crosser != null;
    }

    public boolean hasLastPasser() {
        return lastPasser != null;
    }

    public boolean isAwaitingPenalty() {
        return setPiece == SetPieceKind.PENALTY;
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

    private static void requireCrosserOnlyWhereACrossCanArise(PlayPhase phase, SetPieceKind setPiece,
            Player crosser) {
        boolean crossCanArise = phase == PlayPhase.BUILD_UP || setPiece == SetPieceKind.CORNER
                || setPiece == SetPieceKind.FREE_KICK;
        if (crosser != null && !crossCanArise) {
            throw new IllegalArgumentException("a cross can be in flight only from open build-up play, a corner or a free kick,"
                    + " not in " + phase + " with set piece " + setPiece);
        }
    }

    private static void requireLastPasserOnlyInOpenBuildUp(PlayPhase phase, Player crosser, Player lastPasser) {
        boolean isOpenBuildUp = phase == PlayPhase.BUILD_UP && crosser == null;
        if (lastPasser != null && !isOpenBuildUp) {
            throw new IllegalArgumentException("a last passer can be remembered only in open build-up play without a"
                    + " cross in flight, not in " + phase + " with a cross from " + crosser);
        }
    }
}
